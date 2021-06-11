use crate::model::CredentialScope;
use http::Uri;
use regex::Regex;
use serde::{Serialize, Serializer};
use std::collections::HashMap;

mod engine;
mod model;

#[derive(Eq, PartialEq, Debug, Clone, Serialize)]
pub enum AddressingStyle {
    Virtual,
    Auto,
    Path,
}

#[derive(Debug)]
pub struct S3Config {
    pub address_style: AddressingStyle,
    pub dualstack: bool,
    pub accelerate: bool,
    pub use_arn_region: bool,
}

#[derive(Debug)]
pub struct Request<'a> {
    pub region: &'a str,
    pub bucket: &'a str,
    pub s3_config: S3Config,
}

pub use engine::complete_table;

impl Request<'_> {
    fn bucket_is_valid_dns(&self) -> bool {
        self.bucket.len() >= 3
            && self.bucket.len() <= 63
            && self
                .bucket
                .chars()
                .all(|chr| chr.is_ascii_lowercase() || chr.is_numeric() || chr == '-')
            && {
                let first = self.bucket.chars().next().unwrap();
                first.is_ascii_lowercase() || first.is_numeric()
            }
            && {
                let last = self.bucket.chars().last().unwrap();
                last.is_ascii_lowercase() || last.is_numeric()
            }
    }

    pub fn apply<'a, 'b, B>(
        &'a self,
        mut request: &mut http::Request<B>,
        table: &'b [TableRow],
    ) -> Result<&'b TableRow, crate::InvalidState> {
        let matching_row = table
            .into_iter()
            .find(|row| row.key.matches(self))
            .ok_or(format!("No matching row in table: {:?}", &self))?;
        let matching_value = matching_row.value.as_ref()?;
        if let Some(region_match) = &matching_value.region_match_regex {
            let mut base_match = region_match.template.to_owned();
            for key in &region_match.keys {
                if key == &"region" {
                    base_match = base_match.replace("{region}", &regex::escape(self.region));
                } else {
                    let capture_group: usize = key
                        .strip_prefix("bucket:")
                        .expect("key must be bucket regex")
                        .parse()
                        .expect("must be valid int");
                    base_match = base_match.replace(
                        &format!("{{{}}}", key),
                        &regex::escape(
                            matching_value
                                .bucket_regex
                                .captures(self.bucket)
                                .expect("must have captures")
                                .get(capture_group)
                                .expect("capture group must exist")
                                .as_str(),
                        ),
                    );
                }
            }
            if !Regex::new(&base_match)
                .map_err(|_| "could not create regex".to_string())?
                .is_match(self.region)
            {
                return Err(format!(
                    "Invalid configuration: invalid region, expected {}, found {}",
                    base_match, self.region
                ));
            }
        }
        self.apply_result(&mut request, &matching_value);
        Ok(&matching_row)
    }

    fn apply_result<B>(&self, request: &mut http::Request<B>, table_result: &TableValue) {
        let mut base_uri = table_result.uri_template.template.as_str().to_string();
        for key in &table_result.uri_template.keys {
            if key == &"region" {
                base_uri = base_uri.replace("{region}", self.region);
            } else {
                let capture_group: usize = key
                    .strip_prefix("bucket:")
                    .expect("key must be bucket regex")
                    .parse()
                    .expect("must be valid int");
                base_uri = base_uri.replace(
                    &format!("{{{}}}", key),
                    table_result
                        .bucket_regex
                        .captures(self.bucket)
                        .expect("must have captures")
                        .get(capture_group)
                        .expect("capture group must exist")
                        .as_str(),
                );
            }
        }
        let new_path = if table_result.remove_bucket_from_path {
            let path = request
                .uri()
                .path_and_query()
                .expect("must have path and query, we are removing bucket")
                .as_str();
            path.strip_prefix("/")
                .expect("must start with /")
                .strip_prefix(self.bucket)
                .expect("must start with bucket")
        } else {
            request
                .uri()
                .path_and_query()
                .map(|path| path.as_str())
                .unwrap_or_default()
        };
        let new_uri: Uri = base_uri
            .parse()
            .expect(&format!("{}: base should be a valid URI", base_uri));
        let mut parts = new_uri.into_parts();
        parts.path_and_query = Some(new_path.parse().expect("path must be valid"));
        let new_uri = Uri::from_parts(parts).expect("uri must be valid");
        *request.uri_mut() = new_uri;
    }
}

#[derive(Default, Debug, Serialize)]
pub struct TableKey {
    #[serde(serialize_with = "serde_regex_opt")]
    region_regex: Option<Regex>,
    #[serde(serialize_with = "serde_regex_opt")]
    bucket_regex: Option<Regex>,
    addressing_style: Option<AddressingStyle>,
    dualstack: Option<bool>,
    accelerate: Option<bool>,
    use_arn_region: Option<bool>,
    bucket_is_valid_dns: Option<bool>,
    docs: String,
}

impl TableKey {
    fn matches(&self, req: &Request) -> bool {
        if let Some(address_style) = &self.addressing_style {
            if address_style != &req.s3_config.address_style {
                return false;
            }
        }

        if let Some(dualstack) = self.dualstack {
            if dualstack != req.s3_config.dualstack {
                return false;
            }
        }

        if let Some(use_arn_region) = self.use_arn_region {
            if use_arn_region != req.s3_config.use_arn_region {
                return false;
            }
        }
        if let Some(accelerate) = self.accelerate {
            if accelerate != req.s3_config.accelerate {
                return false;
            }
        }
        if let Some(bucket_valid_dns) = self.bucket_is_valid_dns {
            if bucket_valid_dns != req.bucket_is_valid_dns() {
                return false;
            }
        }

        if let Some(regex) = &self.region_regex {
            if !regex.is_match(req.region) {
                return false;
            }
        }

        if let Some(regex) = &self.bucket_regex {
            if !regex.is_match(req.bucket) {
                return false;
            }
        }
        return true;
    }
}

/// Valid fields:
/// - {region}
/// - {bucket:n} (capture group of the bucket match regex)
#[derive(Debug, Serialize)]
struct Template {
    template: String,
    keys: Vec<&'static str>,
}

impl Template {
    fn validate(&self) {
        let mut patt = self.template.to_string();
        for key in &self.keys {
            patt = patt.replace(&format!("{{{}}}", key), "");
        }
        assert_eq!(patt.find("{"), None, "invalid pattern: {}", self.template);
    }
}

#[derive(Debug, Serialize)]
pub struct TableValue {
    uri_template: Template,
    #[serde(serialize_with = "serde_regex")]
    bucket_regex: Regex,
    header_template: HashMap<String, Template>,
    credential_scope: CredentialScope,
    remove_bucket_from_path: bool,
    /// Validation that the client region is appropriate for this endpoint
    region_match_regex: Option<Template>,
}

fn serde_regex<S>(regex: &Regex, s: S) -> Result<S::Ok, S::Error>
where
    S: Serializer,
{
    s.serialize_str(regex.as_str())
}

fn serde_regex_opt<S>(regex: &Option<Regex>, s: S) -> Result<S::Ok, S::Error>
where
    S: Serializer,
{
    match regex {
        Some(regex) => serde_regex(regex, s),
        None => s.serialize_none(),
    }
}

pub type InvalidState = String;

#[derive(Debug, Serialize)]
pub struct TableRow {
    pub key: TableKey,
    pub value: Result<TableValue, InvalidState>,
}

#[cfg(test)]
mod test {
    use crate::{engine, model, AddressingStyle, Request, S3Config, TableValue, Template};
    use http::{Method, Uri};
    use regex::Regex;
    use std::error::Error;
    use std::fs::File;
    use std::io::Read;

    #[test]
    fn load_endpoints_data() -> Result<(), Box<dyn Error>> {
        let mut data = vec![];
        File::open("data/endpoints.json")?.read_to_end(&mut data)?;
        let endpoints: model::Endpoints = serde_json::from_slice(&data)?;
        let patterns = engine::region_patterns(endpoints);
        dbg!(patterns.len());
        Ok(())
    }

    #[test]
    fn test_banner() {
        let output_match = TableValue {
            uri_template: Template {
                template: "https://{bucket:3}-{bucket:2}.s3-object-lambda.{bucket:1}.amazonaws.com"
                    .to_string(),
                keys: vec!["region", "bucket:3", "bucket:1", "bucket:2"],
            },
            bucket_regex: Regex::new(
                "arn:aws:s3-object-lambda:([^:]+):([^:]+):accesspoint/([0-9a-zA-Z-]{3,50})",
            )
            .expect("valid regex"),
            header_template: Default::default(),
            credential_scope: Default::default(),
            remove_bucket_from_path: true,
            region_match_regex: None,
        };
        let bucket = "arn:aws:s3-object-lambda:us-east-1:123456789012:accesspoint/mybanner";
        let req = Request {
            region: "us-east-1",
            bucket,
            s3_config: S3Config {
                address_style: AddressingStyle::Virtual,
                dualstack: false,
                accelerate: false,
                use_arn_region: false,
            },
        };
        let mut http_req = http::Request::builder()
            .uri(format!("/{}?foo", bucket))
            .method(Method::GET)
            .body(())
            .unwrap();
        req.apply_result(&mut http_req, &output_match);
        assert_eq!(
            http_req.uri(),
            &Uri::from_static(
                "https://mybanner-123456789012.s3-object-lambda.us-east-1.amazonaws.com/?foo"
            )
        );
    }

    #[test]
    fn end_to_end_test() {
        let req = Request {
            region: "us-east-2",
            bucket: "rust-sdk-bucket",
            s3_config: S3Config {
                address_style: AddressingStyle::Virtual,
                dualstack: false,
                accelerate: false,
                use_arn_region: false,
            },
        };
        let table = engine::complete_table().expect("should be able to build table");
        let mut http_req = request("rust-sdk-bucket");
        req.apply(&mut http_req, &table)
            .expect("failed to apply rule");
        assert_eq!(
            http_req.uri(),
            &Uri::from_static("https://rust-sdk-bucket.s3.us-east-2.amazonaws.com/?foo")
        );

        let req = Request {
            region: "us-east-2",
            bucket: "rust-sdk-bucket-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            s3_config: S3Config {
                address_style: AddressingStyle::Auto,
                dualstack: false,
                accelerate: false,
                use_arn_region: false,
            },
        };
        assert_eq!(req.bucket_is_valid_dns(), false);
        let table = engine::complete_table().expect("should be able to build table");
        let mut http_req =
            request("rust-sdk-bucket-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        req.apply(&mut http_req, &table)
            .expect("failed to update uri");
        assert_eq!(
            http_req.uri(),
            &Uri::from_static("https://s3.us-east-2.amazonaws.com/rust-sdk-bucket-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa?foo")
        )
    }

    fn request(bucket: &str) -> http::request::Request<()> {
        http::Request::builder()
            .uri(format!("/{}?foo", bucket))
            .method(Method::GET)
            .body(())
            .unwrap()
    }

    #[test]
    fn test_virtual_addressing() {
        let req = Request {
            region: "us-east-2",
            bucket: "rust-sdk-bucket",
            s3_config: S3Config {
                address_style: AddressingStyle::Virtual,
                dualstack: false,
                accelerate: false,
                use_arn_region: false,
            },
        };
        let output_match = TableValue {
            uri_template: Template {
                template: "https://{bucket:0}.s3.{region}.amazonaws.com".to_string(),
                keys: vec!["region", "bucket:0"],
            },
            bucket_regex: Regex::new("(.*)").expect("valid regex"),
            header_template: Default::default(),
            credential_scope: Default::default(),
            region_match_regex: None,
            remove_bucket_from_path: true,
        };
        let mut http_req = http::Request::builder()
            .uri("/rust-sdk-bucket?foo")
            .method(Method::GET)
            .body(())
            .unwrap();
        req.apply_result(&mut http_req, &output_match);
        assert_eq!(
            http_req.uri(),
            &Uri::from_static("https://rust-sdk-bucket.s3.us-east-2.amazonaws.com/?foo")
        );
    }
}

/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

mod westeros;

use crate::model::{CredentialScope, Endpoint, Endpoints};
use crate::{model, AddressingStyle, TableKey, TableRow, TableValue, Template};
use regex::Regex;
use serde_json::Value;
use std::error::Error;
use std::fs::File;
use std::io::Read;

#[derive(Debug, Eq, PartialEq)]
pub enum RegionRegex {
    ExactMatch(String),
    RegexMatch(String),
}

pub fn complete_table() -> Result<Vec<TableRow>, Box<dyn Error>> {
    let mut out = vec![];
    let mut data = vec![];
    File::open("data/endpoints.json")?.read_to_end(&mut data)?;
    let endpoints: model::Endpoints = serde_json::from_slice(&data)?;
    let patterns = region_patterns(endpoints);
    westeros::access_points_dont_support_accelerate(&mut out);
    out.extend(virtual_addressing_error_cases().into_iter());
    out.extend(for_all_regions(virtual_addressing, &patterns));
    out.extend(for_all_regions(just_dualstack, &patterns));
    out.extend(for_all_regions(just_accelerate, &patterns));
    out.extend(for_all_regions(dualstack_with_accelerate, &patterns));

    westeros::no_fips_in_arn(&mut out);
    for (region_regex, endpoint) in &patterns {
        westeros::vanilla_access_point_addressing(region_regex, &endpoint, &mut out);
        westeros::fips_meta_regions(region_regex, &endpoint, &mut out);
    }
    // These MUST be after the all happy-path rows since they are wild card fallbacks
    westeros::cross_partition_error(&mut out);
    westeros::misc_arn_errors(&mut out);
    out.iter()
        .for_each(|row| row.value.iter().for_each(|v| v.uri_template.validate()));
    Ok(out)
}
impl RegionRegex {
    pub fn to_regex(&self) -> Regex {
        match self {
            RegionRegex::ExactMatch(region) => {
                Regex::new(&format!("^{}$", &regex::escape(region))).unwrap()
            }
            RegionRegex::RegexMatch(pattern) => Regex::new(pattern).unwrap(),
        }
    }
}

#[derive(Debug, Eq, PartialEq)]
pub struct DerivedEndpoint {
    hostname: String,
    raw_pattern: String,
    dns_suffix: String,
    partition: String,
    regional_endpoint: bool,
    protocol: &'static str,
    credential_scope: CredentialScope,
}

fn merge(a: &mut Value, b: &Value) {
    match (a, b) {
        (&mut Value::Object(ref mut a), &Value::Object(ref b)) => {
            for (k, v) in b {
                merge(a.entry(k.clone()).or_insert(Value::Null), v);
            }
        }
        (a, b) => {
            *a = b.clone();
        }
    }
}

fn virtual_addressing_error_cases() -> Vec<TableRow> {
    let mut out = vec![];

    let key = TableKey {
        addressing_style: Some(AddressingStyle::Virtual),
        bucket_is_valid_dns: Some(false),
        docs: "Virtual addresses can only be used when the bucket is valid DNS".to_string(),
        ..Default::default()
    };
    out.push(TableRow {
        key,
        value: Err("virtual addressing cannot be used with invalid DNS".into()),
    });

    let key = TableKey {
        addressing_style: Some(AddressingStyle::Path),
        bucket_is_valid_dns: None,
        accelerate: Some(true),
        docs: "Accelerate cannot be combined with path addressing".to_string(),
        ..Default::default()
    };
    out.push(TableRow {
        key,
        value: Err("accelerate and path addressing are incompatible".into()),
    });
    let key = TableKey {
        bucket_is_valid_dns: Some(false),
        accelerate: Some(true),
        docs: "Accelerate cannot used, the bucket name is not DNS compatible".to_string(),
        ..Default::default()
    };
    out.push(TableRow {
        key,
        value: Err("Bucket name is not DNS compatible as required by S3 accelerate".into()),
    });

    out
}

fn convert_to_dualstack(pattern: &str) -> String {
    if pattern.contains("{service}") {
        pattern.replace("{service}", "{service}.dualstack")
    } else if let Some(rest) = pattern.strip_prefix("s3") {
        format!("{{service}}.dualstack{}", rest)
    } else {
        panic!("cannot find s3 in uri")
    }
}

fn basic_bucket() -> Regex {
    Regex::new("^[a-z0-9\\-_.]+$").unwrap()
}

fn just_dualstack(region_regex: &RegionRegex, derived_endpoint: &DerivedEndpoint) -> Vec<TableRow> {
    let mut out = vec![];
    for addressing_style in &[AddressingStyle::Virtual, AddressingStyle::Auto] {
        let key = TableKey {
            region_regex: Some(region_regex.to_regex()),
            bucket_regex: Some(basic_bucket()),
            addressing_style: Some(addressing_style.clone()),
            dualstack: Some(true),
            accelerate: Some(false),
            use_arn_region: None,
            bucket_is_valid_dns: Some(true),
            docs: "virtual address compatible".to_string(),
        };
        let dualstack_pattern = convert_to_dualstack(&derived_endpoint.raw_pattern)
            .replace("{service}", "s3")
            .replace("{dnsSuffix}", &derived_endpoint.dns_suffix);
        let value = Ok(TableValue {
            uri_template: Template {
                template: format!(
                    "{}://{{bucket:0}}.{}",
                    derived_endpoint.protocol, dualstack_pattern
                ),
                keys: vec!["region", "bucket:0"],
            },
            bucket_regex: Regex::new(".*").unwrap(),
            header_template: Default::default(),
            credential_scope: derived_endpoint.credential_scope.clone(),
            remove_bucket_from_path: true,
            region_match_regex: None,
        });
        out.push(TableRow { key, value });
    }
    for addressing_style in &[AddressingStyle::Path, AddressingStyle::Auto] {
        let key = TableKey {
            region_regex: Some(region_regex.to_regex()),
            bucket_regex: Some(basic_bucket()),
            addressing_style: Some(addressing_style.clone()),
            dualstack: Some(true),
            accelerate: Some(false),
            use_arn_region: None,
            bucket_is_valid_dns: Some(false),
            docs: "dualstack, invalid DNS".to_string(),
        };
        let dualstack_pattern = convert_to_dualstack(&derived_endpoint.raw_pattern)
            .replace("{service}", "s3")
            .replace("{dnsSuffix}", &derived_endpoint.dns_suffix);
        let value = Ok(TableValue {
            uri_template: Template {
                template: format!("{}://{}", derived_endpoint.protocol, dualstack_pattern),
                keys: vec!["region"],
            },
            bucket_regex: Regex::new(".*").unwrap(),
            header_template: Default::default(),
            credential_scope: derived_endpoint.credential_scope.clone(),
            remove_bucket_from_path: false,
            region_match_regex: None,
        });
        out.push(TableRow { key, value });
    }
    out
}

fn for_all_regions(
    f: impl Fn(&RegionRegex, &DerivedEndpoint) -> Vec<TableRow>,
    regions: &[(RegionRegex, DerivedEndpoint)],
) -> Vec<TableRow> {
    regions
        .iter()
        .flat_map(|(region, endpoint)| f(region, endpoint))
        .collect()
}

fn dualstack_with_accelerate(
    region_regex: &RegionRegex,
    derived_endpoint: &DerivedEndpoint,
) -> Vec<TableRow> {
    let mut out = vec![];
    for addressing_style in &[AddressingStyle::Virtual, AddressingStyle::Auto] {
        let key = TableKey {
            region_regex: Some(region_regex.to_regex()),
            bucket_regex: Some(basic_bucket()),
            addressing_style: Some(addressing_style.clone()),
            dualstack: Some(true),
            accelerate: Some(true),
            use_arn_region: None,
            bucket_is_valid_dns: Some(true),
            docs: "virtual address compatible".to_string(),
        };
        let value = Ok(TableValue {
            uri_template: Template {
                template: format!(
                    "{}://{{bucket:0}}.s3-accelerate.dualstack.{}",
                    derived_endpoint.protocol, derived_endpoint.dns_suffix
                ),
                keys: vec!["region", "bucket:0"],
            },
            bucket_regex: Regex::new(".*").unwrap(),
            header_template: Default::default(),
            credential_scope: derived_endpoint.credential_scope.clone(),
            remove_bucket_from_path: true,
            region_match_regex: None,
        });
        out.push(TableRow { key, value });
    }
    out
}

fn just_accelerate(
    region_regex: &RegionRegex,
    derived_endpoint: &DerivedEndpoint,
) -> Vec<TableRow> {
    let mut out = vec![];
    for addressing_style in &[AddressingStyle::Virtual, AddressingStyle::Auto] {
        let key = TableKey {
            region_regex: Some(region_regex.to_regex()),
            bucket_regex: Some(basic_bucket()),
            addressing_style: Some(addressing_style.clone()),
            dualstack: Some(false),
            accelerate: Some(true),
            use_arn_region: None,
            bucket_is_valid_dns: Some(true),
            docs: "virtual address compatible".to_string(),
        };
        let value = Ok(TableValue {
            uri_template: Template {
                template: format!(
                    "{}://{{bucket:0}}.s3-accelerate.{}",
                    derived_endpoint.protocol, derived_endpoint.dns_suffix
                ),
                keys: vec!["region", "bucket:0"],
            },
            bucket_regex: Regex::new(".*").unwrap(),
            header_template: Default::default(),
            credential_scope: derived_endpoint.credential_scope.clone(),
            remove_bucket_from_path: true,
            region_match_regex: None,
        });
        out.push(TableRow { key, value });
    }
    out
}

fn virtual_addressing(
    region_regex: &RegionRegex,
    derived_endpoint: &DerivedEndpoint,
) -> Vec<TableRow> {
    let mut out = vec![];
    for addressing_style in &[AddressingStyle::Virtual, AddressingStyle::Auto] {
        let key = TableKey {
            region_regex: Some(region_regex.to_regex()),
            bucket_regex: Some(basic_bucket()),
            addressing_style: Some(addressing_style.clone()),
            dualstack: Some(false),
            accelerate: Some(false),
            use_arn_region: None,
            bucket_is_valid_dns: Some(true),
            docs: "Dns compatible bucket with vanilla settings".to_string(),
        };
        let value = Ok(TableValue {
            uri_template: Template {
                template: format!(
                    "{}://{{bucket:0}}.{}",
                    derived_endpoint.protocol, derived_endpoint.hostname
                ),
                keys: vec!["region", "bucket:0"],
            },
            bucket_regex: Regex::new(".*").unwrap(),
            header_template: Default::default(),
            credential_scope: derived_endpoint.credential_scope.clone(),
            remove_bucket_from_path: true,
            region_match_regex: None,
        });
        out.push(TableRow { key, value });
    }
    let key = TableKey {
        region_regex: Some(region_regex.to_regex()),
        bucket_regex: Some(basic_bucket()),
        addressing_style: Some(AddressingStyle::Auto),
        dualstack: Some(false),
        accelerate: Some(false),
        use_arn_region: None,
        bucket_is_valid_dns: Some(false),
        docs: "Dns incompatible bucket with vanilla settings".to_string(),
    };
    let value = Ok(TableValue {
        uri_template: Template {
            template: format!(
                "{}://{}",
                derived_endpoint.protocol, derived_endpoint.hostname
            ),
            keys: vec!["region"],
        },
        bucket_regex: Regex::new(".*").unwrap(),
        header_template: Default::default(),
        credential_scope: derived_endpoint.credential_scope.clone(),
        remove_bucket_from_path: false,
        region_match_regex: None,
    });
    out.push(TableRow { key, value });
    out
}

fn protocol(schemes: &[String]) -> &'static str {
    if schemes.iter().any(|s| s == "https") {
        "https"
    } else if schemes.iter().any(|s| s == "https") {
        "http"
    } else {
        panic!("no schemes possible")
    }
}

pub fn region_patterns(endpoint: Endpoints) -> Vec<(RegionRegex, DerivedEndpoint)> {
    let mut derived_endpoints = vec![];
    for mut partition in endpoint.partitions {
        if let Some(s3_override) = partition.services.remove("s3") {
            let service_default = {
                let mut o = partition.defaults.clone();
                merge(&mut o, &s3_override.defaults);
                o
            };
            let service_endpoint = {
                let parsed_ep: Endpoint =
                    serde_json::from_value(service_default.clone()).expect("must be endpoint");
                DerivedEndpoint {
                    regional_endpoint: true,
                    partition: partition.partition.clone(),
                    raw_pattern: parsed_ep.hostname.clone().expect("must have hostname"),
                    hostname: parsed_ep
                        .hostname
                        .expect("must have hostname")
                        .replace("{service}", "s3")
                        .replace("{dnsSuffix}", &partition.dns_suffix),
                    dns_suffix: partition.dns_suffix.clone(),
                    protocol: protocol(&parsed_ep.protocols),
                    credential_scope: parsed_ep.credential_scope.clone(),
                }
            };
            for (region, ep) in s3_override.endpoints {
                let mut ep_base = service_default.clone();
                merge(&mut ep_base, &ep);
                let endpoint: Endpoint = serde_json::from_value(ep_base).expect("must be valid ep");
                let derived_ep = DerivedEndpoint {
                    partition: partition.partition.clone(),
                    // TODO: is the right way to determine if this is a regional endpoint??
                    regional_endpoint: Regex::new(&partition.region_regex)
                        .unwrap()
                        .is_match(&region),
                    raw_pattern: endpoint.hostname.clone().expect("must have hostname"),
                    hostname: endpoint
                        .hostname
                        .expect("must have hostname")
                        .replace("{service}", "s3")
                        .replace("{dnsSuffix}", &partition.dns_suffix),
                    dns_suffix: partition.dns_suffix.clone(),
                    protocol: protocol(&endpoint.protocols),
                    credential_scope: endpoint.credential_scope.clone(),
                };
                if derived_ep != service_endpoint {
                    derived_endpoints.push((RegionRegex::ExactMatch(region), derived_ep));
                }
            }
            derived_endpoints.push((
                RegionRegex::RegexMatch(partition.region_regex),
                service_endpoint,
            ));
        } else {
            panic!("no s3")
        }
    }
    derived_endpoints
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn basic_bucket_doesnt_match_arn() {
        let bucket = "arn:aws:s3:us-east-1:123456789012:accesspoint:myendpoint";
        assert_eq!(basic_bucket().is_match(bucket), false);
    }
}

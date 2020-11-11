use crate::MediaType::Other;
use assert_json_diff::assert_json_eq_no_panic;
use http::{Request, Uri};
use smithy_http::base64;
use std::collections::HashSet;
use std::str::{from_utf8, FromStr};
use thiserror::Error;

#[derive(PartialEq, Eq, Error, Debug)]
pub enum ProtocolTestFailure {
    #[error("missing query param: expected {expected}, found {found:?}")]
    MissingQueryParam {
        expected: String,
        found: Vec<String>,
    },
    #[error("forbidden query param present: {expected}")]
    ForbiddenQueryParam { expected: String },
    #[error("required query param missing: {expected}")]
    RequiredQueryParam { expected: String },
    #[error("invalid header value: expected {expected}, found {found}")]
    InvalidHeader { expected: String, found: String },
    #[error("missing header {expected}")]
    MissingHeader { expected: String },
    #[error("Bodies did not match. Hint:\n{hint}")]
    BodyDidNotMatch {
        expected: String,
        found: String,
        hint: String,
    },
    #[error("Expected body to be valid {expected} but instead: {found}")]
    InvalidBodyFormat { expected: String, found: String },
}

#[derive(Eq, PartialEq, Hash)]
struct QueryParam<'a> {
    key: &'a str,
    value: Option<&'a str>,
}

impl<'a> QueryParam<'a> {
    fn parse(s: &'a str) -> Self {
        let mut parsed = s.split('=');
        QueryParam {
            key: parsed.next().unwrap(),
            value: parsed.next(),
        }
    }
}

fn extract_params(uri: &Uri) -> HashSet<&str> {
    uri.query().unwrap_or_default().split('&').collect()
}

pub fn validate_query_string<B>(
    request: &Request<B>,
    expected_params: &[&str],
) -> Result<(), ProtocolTestFailure> {
    let actual_params = extract_params(request.uri());
    for param in expected_params {
        if !actual_params.contains(param) {
            return Err(ProtocolTestFailure::MissingQueryParam {
                expected: param.to_string(),
                found: actual_params.iter().map(|s| s.to_string()).collect(),
            });
        }
    }
    Ok(())
}

pub fn forbid_query_params<B>(
    request: &Request<B>,
    forbid_keys: &[&str],
) -> Result<(), ProtocolTestFailure> {
    let actual_keys: HashSet<&str> = extract_params(request.uri())
        .iter()
        .map(|param| QueryParam::parse(param).key)
        .collect();
    for key in forbid_keys {
        if actual_keys.contains(*key) {
            return Err(ProtocolTestFailure::ForbiddenQueryParam {
                expected: key.to_string(),
            });
        }
    }
    Ok(())
}

pub fn require_query_params<B>(
    request: &Request<B>,
    require_keys: &[&str],
) -> Result<(), ProtocolTestFailure> {
    let actual_keys: HashSet<&str> = extract_params(request.uri())
        .iter()
        .map(|param| QueryParam::parse(param).key)
        .collect();
    for key in require_keys {
        if !actual_keys.contains(*key) {
            return Err(ProtocolTestFailure::RequiredQueryParam {
                expected: key.to_string(),
            });
        }
    }
    Ok(())
}

pub fn validate_headers<B>(
    request: &Request<B>,
    expected_headers: &[(&str, &str)],
) -> Result<(), ProtocolTestFailure> {
    for (key, expected_value) in expected_headers {
        // Protocol tests store header lists as comma-delimited
        if !request.headers().contains_key(*key) {
            return Err(ProtocolTestFailure::MissingHeader {
                expected: key.to_string(),
            });
        }
        let actual_value: String = request
            .headers()
            .get_all(*key)
            .iter()
            .map(|hv| hv.to_str().unwrap())
            .collect::<Vec<_>>()
            .join(", ");
        if *expected_value != actual_value {
            return Err(ProtocolTestFailure::InvalidHeader {
                expected: expected_value.to_string(),
                found: actual_value,
            });
        }
    }
    Ok(())
}

pub enum MediaType {
    Json,
    Other(String),
}

impl FromStr for MediaType {
    type Err = ();

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        Ok(match s {
            "application/json" => MediaType::Json,
            other => Other(other.to_string()),
        })
    }
}

pub fn validate_body(
    actual_body: &[u8],
    expected_body: &str,
    media_type: MediaType,
) -> Result<(), ProtocolTestFailure> {
    let actual_body_str = from_utf8(actual_body)
        .map(|body| body.to_string())
        .unwrap_or(base64::encode(actual_body));
    match media_type {
        MediaType::Json => {
            let actual_json: serde_json::Value =
                serde_json::from_slice(actual_body).map_err(|e| {
                    ProtocolTestFailure::InvalidBodyFormat {
                        expected: "json".to_owned(),
                        found: e.to_string(),
                    }
                })?;
            let expected_json: serde_json::Value =
                serde_json::from_str(expected_body).expect("expected value must be valid JSON");
            return match assert_json_eq_no_panic(&actual_json, &expected_json) {
                Ok(()) => Ok(()),
                Err(message) => Err(ProtocolTestFailure::BodyDidNotMatch {
                    expected: expected_body.to_string(),
                    found: actual_body_str,
                    hint: message,
                }),
            };
        }
        MediaType::Other(other_media_type) => {
            if actual_body != expected_body.as_bytes() {
                return Err(ProtocolTestFailure::BodyDidNotMatch {
                    expected: expected_body.to_string(),
                    found: actual_body_str,
                    hint: format!("media type: {}", other_media_type),
                });
            }
        }
    }
    Ok(())
}

/// Check that the protocol test succeeded & print the pretty error
/// if it did not
///
/// The primary motivation is making multiline debug output
/// readable as is the case in JSON diff hints
#[track_caller]
pub fn check(inp: Result<(), ProtocolTestFailure>) {
    match inp {
        Ok(_) => (),
        Err(e) => {
            eprintln!("{}", e);
            assert!(false, "Protocol test failed");
        }
    }
}

#[cfg(test)]
mod tests {
    use crate::{
        check, forbid_query_params, require_query_params, validate_body, validate_headers,
        validate_query_string, MediaType, ProtocolTestFailure,
    };
    use http::Request;
    use std::str::FromStr;

    #[test]
    fn test_validate_empty_query_string() {
        let request = Request::builder().uri("/foo").body(()).unwrap();
        validate_query_string(&request, &[]).expect("no required params should pass");
        validate_query_string(&request, &["a"])
            .err()
            .expect("no params provided");
    }

    #[test]
    fn test_validate_query_string() {
        let request = Request::builder()
            .uri("/foo?a=b&c&d=efg&hello=a%20b")
            .body(())
            .unwrap();
        validate_query_string(&request, &["a=b"]).expect("a=b is in the query string");
        validate_query_string(&request, &["c", "a=b"])
            .expect("both params are in the query string");
        validate_query_string(&request, &["a=b", "c", "d=efg", "hello=a%20b"])
            .expect("all params are in the query string");
        validate_query_string(&request, &[]).expect("no required params should pass");

        validate_query_string(&request, &["a"]).expect_err("no parameter should match");
        validate_query_string(&request, &["a=bc"]).expect_err("no parameter should match");
        validate_query_string(&request, &["a=bc"]).expect_err("no parameter should match");
        validate_query_string(&request, &["hell=a%20"]).expect_err("no parameter should match");
    }

    #[test]
    fn test_forbid_query_param() {
        let request = Request::builder()
            .uri("/foo?a=b&c&d=efg&hello=a%20b")
            .body(())
            .unwrap();
        forbid_query_params(&request, &["a"]).expect_err("a is a query param");
        forbid_query_params(&request, &["not_included"]).expect("query param not included");
        forbid_query_params(&request, &["a=b"]).expect("should be matching against keys");
        forbid_query_params(&request, &["c"]).expect_err("c is a query param");
    }

    #[test]
    fn test_require_query_param() {
        let request = Request::builder()
            .uri("/foo?a=b&c&d=efg&hello=a%20b")
            .body(())
            .unwrap();
        require_query_params(&request, &["a"]).expect("a is a query param");
        require_query_params(&request, &["not_included"]).expect_err("query param not included");
        require_query_params(&request, &["a=b"]).expect_err("should be matching against keys");
        require_query_params(&request, &["c"]).expect("c is a query param");
    }

    #[test]
    fn test_validate_headers() {
        let request = Request::builder()
            .uri("/")
            .header("X-Foo", "foo")
            .header("X-Foo-List", "foo")
            .header("X-Foo-List", "bar")
            .header("X-Inline", "inline, other")
            .body(())
            .unwrap();

        validate_headers(&request, &[("X-Foo", "foo")]).expect("header present");
        validate_headers(&request, &[("X-Foo", "Foo")]).expect_err("case sensitive");
        validate_headers(&request, &[("x-foo-list", "foo, bar")]).expect("list concat");
        validate_headers(&request, &[("X-Foo-List", "foo")])
            .expect_err("all list members must be specified");
        validate_headers(&request, &[("X-Inline", "inline, other")])
            .expect("inline header lists also work");
        assert_eq!(
            validate_headers(&request, &[("missing", "value")]),
            Err(ProtocolTestFailure::MissingHeader {
                expected: "missing".to_owned()
            })
        );
    }

    #[test]
    fn test_validate_json_body() {
        let expected = r#"{"abc": 5 }"#;
        let actual = r#"   {"abc":   5 }"#;
        check(validate_body(actual.as_bytes(), expected, MediaType::Json));

        let expected = r#"{"abc": 5 }"#;
        let actual = r#"   {"abc":   6 }"#;
        validate_body(actual.as_bytes(), expected, MediaType::Json)
            .expect_err("bodies do not match");
    }

    #[test]
    fn test_validate_non_json_body() {
        let expected = r#"asdf"#;
        let actual = r#"asdf "#;
        validate_body(
            actual.as_bytes(),
            expected,
            MediaType::from_str("something/else").unwrap(),
        ).expect_err("bodies do not match");

        check(validate_body(
            expected.as_bytes(),
            expected,
            MediaType::from_str("something/else").unwrap(),
        ))
    }
}

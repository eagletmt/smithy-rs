/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

use http::header::ToStrError;

// currently only used by AwsJson
#[allow(unused)]
pub fn is_error<B>(response: &http::Response<B>) -> bool {
    !response.status().is_success()
}

fn error_type_from_header<B>(response: &http::Response<B>) -> Result<Option<&str>, ToStrError> {
    response
        .headers()
        .get("X-Amzn-Errortype")
        .map(|v| v.to_str())
        .transpose()
}

fn error_type_from_body(body: &serde_json::Value) -> Option<&str> {
    body.as_object()
        .and_then(|b: &serde_json::Map<String, serde_json::Value>| {
            b.get("code").or_else(|| b.get("__type"))
        })
        .and_then(|v| v.as_str())
}

fn sanitize_error_code(error_code: &str) -> &str {
    // Trim a trailing URL from the error code, beginning with a `:`
    let error_code = match error_code.find(':') {
        Some(idx) => &error_code[..idx],
        None => &error_code,
    };

    // Trim a prefixing namespace from the error code, beginning with a `#`
    match error_code.find('#') {
        Some(idx) => &error_code[idx + 1..],
        None => &error_code,
    }
}

pub fn parse_generic_error<B>(
    response: &http::Response<B>,
    body: &serde_json::Value,
) -> smithy_types::Error {
    let mut err_builder = smithy_types::Error::builder();
    let code = error_type_from_header(&response)
        .unwrap_or(Some("header was not valid UTF-8"))
        .or_else(|| error_type_from_body(body))
        .map(|s| sanitize_error_code(s));
    if let Some(code) = code {
        err_builder.code(code);
    }
    let message = body
        .get("message")
        .or_else(|| body.get("Message"))
        .or_else(|| body.get("errorMessage"))
        .and_then(|v| v.as_str());
    if let Some(message) = message {
        err_builder.message(message);
    }
    let request_id = response
        .headers()
        .get("X-Amzn-Requestid")
        .and_then(|v| v.to_str().ok());
    if let Some(request_id) = request_id {
        err_builder.request_id(request_id);
    }
    err_builder.build()
}

#[cfg(test)]
mod test {
    use crate::aws_json_errors::{error_type_from_body, parse_generic_error, sanitize_error_code};
    use serde_json::json;
    use smithy_types::Error;

    #[test]
    fn generic_error() {
        let response = http::Response::builder()
            .header("X-Amzn-Requestid", "1234")
            .body(json!({
                "__type": "FooError",
                "message": "Go to foo"
            }))
            .unwrap();
        assert_eq!(
            parse_generic_error(&response, response.body()),
            Error::builder()
                .code("FooError")
                .message("Go to foo")
                .request_id("1234")
                .build()
        )
    }

    #[test]
    fn error_type() {
        let error_body = json!({
            "__type": "FooError"
        });
        assert_eq!(error_type_from_body(&error_body), Some("FooError"));
    }

    #[test]
    fn code_takes_priority() {
        let error_body = json!({
            "__type": "FooError",
            "code": "BarError"
        });
        assert_eq!(error_type_from_body(&error_body), Some("BarError"));
    }

    #[test]
    fn sanitize_namespace_and_url() {
        assert_eq!(
            sanitize_error_code("aws.protocoltests.restjson#FooError:http://internal.amazon.com/coral/com.amazon.coral.validate/"),
            "FooError");
    }

    #[test]
    fn sanitize_noop() {
        assert_eq!(sanitize_error_code("FooError"), "FooError");
    }

    #[test]
    fn sanitize_url() {
        assert_eq!(
            sanitize_error_code(
                "FooError:http://internal.amazon.com/coral/com.amazon.coral.validate/"
            ),
            "FooError"
        );
    }

    #[test]
    fn sanitize_namespace() {
        assert_eq!(
            sanitize_error_code("aws.protocoltests.restjson#FooError"),
            "FooError"
        );
    }

    // services like lambda use an alternate `Message` instead of `message`
    #[test]
    fn alternative_error_message_names() {
        let response = http::Response::builder()
            .header("x-amzn-errortype", "ResourceNotFoundException")
            .body(json!({
                "Type": "User",
                "Message": "Functions from 'us-west-2' are not reachable from us-east-1"
            }))
            .unwrap();
        assert_eq!(
            parse_generic_error(&response, response.body()),
            Error::builder()
                .code("ResourceNotFoundException")
                .message("Functions from 'us-west-2' are not reachable from us-east-1")
                .build()
        );
    }
}

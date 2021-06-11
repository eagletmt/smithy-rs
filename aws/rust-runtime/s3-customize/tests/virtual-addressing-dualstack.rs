/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

use http::{Request, Uri};
use s3_customize::{AddressingStyle, S3Config, TableRow};
use serde::Deserialize;
use std::error::Error;
use std::fs;
use std::time::Instant;

#[derive(Deserialize, Debug)]
#[serde(rename_all = "PascalCase")]
struct TestCase {
    bucket: String,
    configured_addressing_style: String,
    expected_uri: String,
    region: String,
    use_dualstack: bool,
    use_s3_accelerate: bool,
    s3_us_east_1_regional_endpoint: String,
}

#[test]
fn run_test_cases() -> Result<(), Box<dyn Error>> {
    let test_cases = fs::read_to_string("test-data/virtual-addressing.json")?;
    let test_cases: Vec<TestCase> = serde_json::from_str(&test_cases)?;
    let test_cases = test_cases
        .into_iter()
        .filter(|test| {
            !(test.region == "us-east-1" && test.s3_us_east_1_regional_endpoint == "legacy")
        })
        .collect::<Vec<_>>();
    let table = s3_customize::complete_table()?;
    let now = Instant::now();
    for test_case in &test_cases {
        check(test_case, &table);
    }
    let after = Instant::now();
    println!(
        "delta: {:?}, total cases: {}",
        after - now,
        test_cases.len()
    );
    Ok(())
}

fn check(test_case: &TestCase, table: &[TableRow]) {
    let request = s3_customize::Request {
        region: &test_case.region,
        bucket: &test_case.bucket,
        s3_config: S3Config {
            address_style: match test_case.configured_addressing_style.as_str() {
                "default" => AddressingStyle::Auto,
                "path" => AddressingStyle::Path,
                _ => panic!(),
            },
            dualstack: test_case.use_dualstack,
            accelerate: test_case.use_s3_accelerate,
            use_arn_region: false,
        },
    };

    let mut input_request = Request::builder()
        .uri(format!("/{}", test_case.bucket))
        .body(())
        .unwrap();
    request
        .apply(&mut input_request, table)
        .expect(&format!("failed to process request: {:?}", test_case));
    assert_eq!(
        input_request.uri(),
        &test_case.expected_uri.parse::<http::Uri>().unwrap(),
        "{:?}",
        test_case
    );
}

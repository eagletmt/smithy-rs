/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

use s3_customize::{AddressingStyle, S3Config, TableRow};
use serde::Deserialize;
use std::error::Error;
use std::fs;
use std::time::Instant;

#[derive(Deserialize, Debug)]
struct TestCase {
    bucket: String,
    endpoint: Result<String, String>,
    region: String,
    use_dualstack: bool,
    use_s3_accelerate: bool,
    use_arn_region: bool,
}

#[test]
fn run_test_cases() -> Result<(), Box<dyn Error>> {
    let test_cases = fs::read_to_string("test-data/westeros.json")?;
    let test_cases: Vec<TestCase> = serde_json::from_str(&test_cases)?;
    let table = s3_customize::complete_table()?;
    let now = Instant::now();
    for test_case in &test_cases {
        check(&test_case, &table)
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
            address_style: AddressingStyle::Auto,
            dualstack: test_case.use_dualstack,
            accelerate: test_case.use_s3_accelerate,
            use_arn_region: test_case.use_arn_region,
        },
    };

    let mut input_request = http::Request::builder()
        .uri(format!("/{}", test_case.bucket))
        .body(())
        .unwrap();
    match (
        request.apply(&mut input_request, table),
        &test_case.endpoint,
    ) {
        (Ok(row), Ok(ep)) => assert_eq!(
            input_request.uri(),
            &ep.parse::<http::Uri>().unwrap(),
            "{:?} {:?}",
            test_case,
            row
        ),
        (Err(actual), Err(expected)) => assert_eq!(&actual, expected, "{:?}", test_case),
        (actual, expected) => panic!(
            "Mismatch: \n actual: {:?}\n expected: {:?}\n test case: {:?}",
            actual, expected, test_case
        ),
    }
}

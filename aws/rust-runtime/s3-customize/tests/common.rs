/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

use s3_customize::{AddressingStyle, S3Config, TableRow};
use serde::Deserialize;
use std::collections::HashMap;

#[derive(Deserialize, Debug)]
pub struct TestCase {
    pub bucket: String,
    pub endpoint: Result<String, String>,
    pub region: String,
    pub use_dualstack: bool,
    pub use_s3_accelerate: bool,
    #[serde(default)]
    pub use_arn_region: bool,
    #[serde(default)]
    pub us_east_1_regional_endpoint: String,
    #[serde(default)]
    pub configured_addressing_style: Option<String>,
    #[serde(default)]
    pub extras: HashMap<String, String>,
}

impl TestCase {
    pub fn addressing_style(&self) -> AddressingStyle {
        match &self
            .configured_addressing_style
            .as_ref()
            .map(|s| s.as_str())
        {
            Some("default") => AddressingStyle::Auto,
            Some("virtual") => AddressingStyle::Virtual,
            Some("path") => AddressingStyle::Path,
            Some(other) => todo!("{}", other),
            None => AddressingStyle::Auto,
        }
    }
}

pub fn check(test_case: &TestCase, table: &[TableRow]) {
    let request = s3_customize::Request {
        region: &test_case.region,
        bucket: &test_case.bucket,
        s3_config: S3Config {
            address_style: test_case.addressing_style(),
            dualstack: test_case.use_dualstack,
            accelerate: test_case.use_s3_accelerate,
            use_arn_region: test_case.use_arn_region,
        },
        extras: test_case.extras.clone(),
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

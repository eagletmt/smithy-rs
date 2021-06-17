/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

use http::{Request, Uri};
use s3_customize::{AddressingStyle, S3Config, TableRow};
use serde::Deserialize;
use std::collections::HashMap;
use std::error::Error;
use std::fs;
use std::time::Instant;
mod common;

use common::{check, TestCase};

#[test]
fn run_test_cases() -> Result<(), Box<dyn Error>> {
    let test_cases = fs::read_to_string("test-data/virtual-addressing.json")?;
    let test_cases: Vec<TestCase> = serde_json::from_str(&test_cases)?;
    let test_cases = test_cases
        .into_iter()
        .filter(|test| {
            !(test.region == "us-east-1" && test.us_east_1_regional_endpoint == "legacy")
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

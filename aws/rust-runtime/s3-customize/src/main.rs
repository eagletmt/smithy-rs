/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

use s3_customize::complete_table;

fn main() {
    let full_table = complete_table().unwrap();
    let as_json = serde_json::to_string_pretty(&full_table).unwrap();
    println!("{}", &as_json);
}

/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

struct TestCase {
    bucket: String,
    addressing_style: String,
    expected_uri: String,
    region: String,
    use_dualstack: bool,
    use_s3_accelerate: bool,
}

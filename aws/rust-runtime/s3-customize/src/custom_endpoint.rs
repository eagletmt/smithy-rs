/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

pub struct Config {
    dualstack: bool,
    accelerate: bool,
    virtual_addressing: bool,
}
pub struct CustomEndpoint {
    dns_suffix: &'static str,
    scheme: &'static str,
}

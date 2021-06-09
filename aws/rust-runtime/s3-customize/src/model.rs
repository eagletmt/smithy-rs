/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

use serde::Deserialize;
use std::collections::HashMap;

#[derive(Deserialize)]
pub struct Endpoints {
    pub partitions: Vec<Partition>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Partition {
    pub partition: String,
    pub partition_name: String,
    pub dns_suffix: String,
    pub region_regex: String,
    pub regions: HashMap<String, Region>,
    pub defaults: serde_json::Value,
    pub services: HashMap<String, Service>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Region {
    pub description: String,
}

fn default_regionalized() -> bool {
    true
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Service {
    #[serde(default)]
    pub partition_endpoint: Option<String>,
    #[serde(default = "default_regionalized")]
    pub is_regionalized: bool,
    #[serde(default)]
    pub defaults: serde_json::Value,
    pub endpoints: HashMap<String, serde_json::Value>,
}

#[derive(Deserialize, Debug, Eq, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct Endpoint {
    #[serde(default)]
    pub hostname: Option<String>,
    #[serde(default)]
    pub protocols: Vec<String>,
    #[serde(default)]
    pub signature_versions: Vec<String>,
    #[serde(default)]
    pub credential_scope: CredentialScope,
}

#[derive(Deserialize, Default, Debug, Eq, PartialEq, Clone)]
pub struct CredentialScope {
    #[serde(default)]
    pub region: Option<String>,
    #[serde(default)]
    pub service: Option<String>,
}

/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

use crate::model::{CredentialScope, Endpoint, Endpoints};
use crate::{model, AddressingStyle, TableKey, TableValue, Template};
use regex::Regex;
use serde_json::Value;
use std::error::Error;
use std::fs::File;
use std::io::Read;

#[derive(Debug, Eq, PartialEq)]
pub enum RegionRegex {
    ExactMatch(String),
    RegexMatch(String),
}

pub type InvalidState = String;

#[derive(Debug)]
pub struct TableRow {
    pub key: TableKey,
    pub value: Result<TableValue, InvalidState>,
}

pub fn complete_table() -> Result<Vec<TableRow>, Box<dyn Error>> {
    let mut out = vec![];
    let mut data = vec![];
    File::open("data/endpoints.json")?.read_to_end(&mut data)?;
    let endpoints: model::Endpoints = serde_json::from_slice(&data)?;
    let patterns = region_patterns(endpoints);
    out.extend(virtual_addressing_error_cases().into_iter());
    out.extend(virtual_addressing(&patterns));
    Ok(out)
}
impl RegionRegex {
    pub fn to_regex(&self) -> Regex {
        match self {
            RegionRegex::ExactMatch(region) => Regex::new(&regex::escape(region)).unwrap(),
            RegionRegex::RegexMatch(pattern) => Regex::new(pattern).unwrap(),
        }
    }
}

#[derive(Debug, Eq, PartialEq)]
pub struct DerivedEndpoint {
    hostname: String,
    protocol: &'static str,
    credential_scope: CredentialScope,
}

fn merge(a: &mut Value, b: &Value) {
    match (a, b) {
        (&mut Value::Object(ref mut a), &Value::Object(ref b)) => {
            for (k, v) in b {
                merge(a.entry(k.clone()).or_insert(Value::Null), v);
            }
        }
        (a, b) => {
            *a = b.clone();
        }
    }
}

fn virtual_addressing_error_cases() -> Vec<TableRow> {
    let mut out = vec![];

    let key = TableKey {
        addressing_style: Some(AddressingStyle::Virtual),
        bucket_is_valid_dns: Some(false),
        docs: "Virtual addresses can only be used when the bucket is valid DNS".to_string(),
        ..Default::default()
    };
    out.push(TableRow {
        key,
        value: Err("virtual addressing cannot be used with invalid DNS".into()),
    });

    let key = TableKey {
        addressing_style: Some(AddressingStyle::Path),
        bucket_is_valid_dns: None,
        accelerate: Some(true),
        docs: "Accelerate cannot be combined with path addressing".to_string(),
        ..Default::default()
    };
    out.push(TableRow {
        key,
        value: Err("accelerate and path addressing are incompatible".into()),
    });
    let key = TableKey {
        bucket_is_valid_dns: Some(false),
        accelerate: Some(true),
        docs: "Accelerate cannot used, the bucket name is not DNS compatible".to_string(),
        ..Default::default()
    };
    out.push(TableRow {
        key,
        value: Err("Bucket name is not DNS compatible as required by S3 accelerate".into()),
    });

    out
}

fn virtual_addressing(regions: &[(RegionRegex, DerivedEndpoint)]) -> Vec<TableRow> {
    let mut out = vec![];
    regions.iter().for_each(|(region_regex, derived_endpoint)| {
        let key = TableKey {
            region_regex: Some(region_regex.to_regex()),
            bucket_regex: None,
            addressing_style: Some(AddressingStyle::Virtual),
            dualstack: Some(false),
            accelerate: Some(false),
            use_arn_region: None,
            bucket_is_valid_dns: Some(true),
            docs: "virtual address compatible".to_string(),
        };
        let value = Ok(TableValue {
            uri_template: Template {
                template: format!(
                    "{}://{{bucket:0}}.{}",
                    derived_endpoint.protocol, derived_endpoint.hostname
                ),
                keys: vec!["region".to_string(), "bucket:0".to_string()],
            },
            bucket_regex: Regex::new(".*").unwrap(),
            header_template: Default::default(),
            credential_scope: derived_endpoint.credential_scope.clone(),
            remove_bucket_from_path: true,
        });
        out.push(TableRow { key, value });
        let key = TableKey {
            region_regex: Some(region_regex.to_regex()),
            bucket_regex: None,
            addressing_style: Some(AddressingStyle::Auto),
            dualstack: Some(false),
            accelerate: Some(false),
            use_arn_region: None,
            bucket_is_valid_dns: Some(false),
            docs: "virtual address incompatible".to_string(),
        };
        let value = Ok(TableValue {
            uri_template: Template {
                template: format!(
                    "{}://{}",
                    derived_endpoint.protocol, derived_endpoint.hostname
                ),
                keys: vec!["region".to_string()],
            },
            bucket_regex: Regex::new(".*").unwrap(),
            header_template: Default::default(),
            credential_scope: derived_endpoint.credential_scope.clone(),
            remove_bucket_from_path: false,
        });
        out.push(TableRow { key, value });
    });
    out
}

fn protocol(schemes: &[String]) -> &'static str {
    if schemes.iter().any(|s| s == "https") {
        "https"
    } else if schemes.iter().any(|s| s == "https") {
        "http"
    } else {
        panic!("no schemes possible")
    }
}

pub fn region_patterns(endpoint: Endpoints) -> Vec<(RegionRegex, DerivedEndpoint)> {
    let mut derived_endpoints = vec![];
    for mut partition in endpoint.partitions {
        if let Some(s3_override) = partition.services.remove("s3") {
            let service_default = {
                let mut o = partition.defaults.clone();
                merge(&mut o, &s3_override.defaults);
                o
            };
            let service_endpoint = {
                let parsed_ep: Endpoint =
                    serde_json::from_value(service_default.clone()).expect("must be endpoint");
                DerivedEndpoint {
                    hostname: parsed_ep
                        .hostname
                        .expect("must have hostname")
                        .replace("{service}", "s3")
                        .replace("{dnsSuffix}", &partition.dns_suffix),
                    protocol: protocol(&parsed_ep.protocols),
                    credential_scope: parsed_ep.credential_scope.clone(),
                }
            };
            for (region, ep) in s3_override.endpoints {
                let mut ep_base = service_default.clone();
                merge(&mut ep_base, &ep);
                let endpoint: Endpoint = serde_json::from_value(ep_base).expect("must be valid ep");
                let derived_ep = DerivedEndpoint {
                    hostname: endpoint
                        .hostname
                        .expect("must have hostname")
                        .replace("{service}", "s3")
                        .replace("{dnsSuffix}", &partition.dns_suffix),

                    protocol: protocol(&endpoint.protocols),
                    credential_scope: endpoint.credential_scope.clone(),
                };
                if derived_ep != service_endpoint {
                    derived_endpoints.push((RegionRegex::ExactMatch(region), derived_ep));
                }
            }
            derived_endpoints.push((
                RegionRegex::RegexMatch(partition.region_regex),
                service_endpoint,
            ));
        } else {
            panic!("no s3")
        }
    }
    derived_endpoints
}

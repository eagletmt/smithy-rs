/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

use crate::engine::{DerivedEndpoint, RegionRegex};
use crate::{AddressingStyle, TableKey, TableRow, TableValue, Template};
use itertools::iproduct;
use regex::Regex;

// capture groups:
// 1: partition
// 2: region
// 3: account id
// 4: access point id
fn s3_access_point_regex(partition: &str) -> Regex {
    let base_pattern = format!(
        r#"^arn[:/]({})[:/]s3[:/]([a-zA-Z0-9-]+)[:/]([a-zA-Z0-9-]{{1,63}})[:/]accesspoint[:/]([a-zA-Z0-9-]{{1,63}})$"#,
        partition
    );
    Regex::new(&base_pattern).unwrap()
    // arn:aws:s3:us-west-2:123456789012:accesspoint/fink
}

pub fn access_points_dont_support_accelerate(out: &mut Vec<TableRow>) {
    let key = TableKey {
        region_regex: None,
        bucket_regex: Some(s3_access_point_regex("[a-zA-Z0-9-]+")),
        addressing_style: None,
        dualstack: None,
        accelerate: Some(true),
        use_arn_region: None,
        bucket_is_valid_dns: None,
        docs: "Access points do not support accelerate".to_string(),
    };
    let value = Err("Invalid configuration Access Points do not support accelerate".to_owned());
    out.push(TableRow { key, value });
}

pub fn misc_arn_errors(out: &mut Vec<TableRow>) {
    // service not set to s3
    let invalid_service = (
        "Invalid ARN not S3 ARN",
        Regex::new(r#"arn[:/][a-zA-Z0-9-]+[:/][a-zA-Z0-9-]+[:/]([a-zA-Z0-9-]+)[:/]([a-zA-Z0-9-]{1,63})[:/].*$"#).unwrap()
    );
    let missing_ap_name = (
        "Invalid ARN, missing Access Point name",
        Regex::new(
            r#"arn[:/][a-zA-Z0-9-]+[:/]s3[:/]([a-zA-Z0-9-]+)[:/]([a-zA-Z0-9-]{1,63})[:/]accesspoint$"#,
        )
            .unwrap(),
    );
    let invalid_ap_name = (
        "Invalid ARN, Access Point Name contains invalid character",
        Regex::new(
            r#"arn[:/][a-zA-Z0-9-]+[:/]s3[:/]([a-zA-Z0-9-]+)[:/]([a-zA-Z0-9-]{1,63})[:/]accesspoint:.*$"#,
        )
            .unwrap(),
    );
    // .* in the arn resource
    let unknown_arn_type = (
        "Invalid ARN unknown resource type",
        Regex::new(
            r#"arn[:/][a-zA-Z0-9-]+[:/]s3[:/]([a-zA-Z0-9-]+)[:/]([a-zA-Z0-9-]{1,63})[:/].*"#,
        )
        .unwrap(),
    );

    let missing_region = (
        "Invalid ARN, missing region",
        Regex::new(r#"arn[:/][a-zA-Z0-9-]+[:/]s3[:/][:/]([a-zA-Z0-9-]{1,63})[:/]accesspoint[:/]([a-zA-Z0-9-]{1,63})"#).unwrap()
    );
    let missing_account_id = (
        "Invalid ARN, missing account-id",
        Regex::new(r#"arn[:/][a-zA-Z0-9-]+[:/]s3[:/]([a-zA-Z0-9-]+)[:/][:/].*$"#).unwrap(),
    );
    let invalid_account_id = (
        "Invalid ARN, account-id contains invalid character",
        Regex::new(r#"arn[:/][a-zA-Z0-9-]+[:/]s3[:/]([a-zA-Z0-9-]+)[:/][^:/]+[:/].*$"#).unwrap(),
    );
    for (msg, regex) in &[
        missing_ap_name,
        invalid_ap_name,
        unknown_arn_type,
        missing_region,
        missing_account_id,
        invalid_account_id,
        invalid_service,
    ] {
        out.push(TableRow {
            key: TableKey {
                region_regex: None,
                bucket_regex: Some(regex.clone()),
                addressing_style: None,
                dualstack: None,
                accelerate: None,
                use_arn_region: None,
                bucket_is_valid_dns: None,
                docs: msg.to_string(),
            },
            value: Err(msg.to_string()),
        })
    }
}

pub fn no_fips_in_arn(out: &mut Vec<TableRow>) {
    let fips_in_arn_prefix = Regex::new(r#"arn[:/][a-zA-Z0-9-]+[:/]s3[:/]fips-[a-zA-Z0-9-]+[:/]([a-zA-Z0-9-]{1,63})[:/]accesspoint[:/]([a-zA-Z0-9-]{1,63})"#).unwrap();
    let fips_in_arn_suffix = Regex::new(r#"arn[:/][a-zA-Z0-9-]+[:/]s3[:/][a-zA-Z0-9-]+-fips[:/]([a-zA-Z0-9-]{1,63})[:/]accesspoint[:/]([a-zA-Z0-9-]{1,63})"#).unwrap();
    for regex in &[fips_in_arn_prefix, fips_in_arn_suffix] {
        out.push(TableRow {
            key: TableKey {
                region_regex: None,
                bucket_regex: Some(regex.clone()),
                addressing_style: None,
                dualstack: None,
                accelerate: None,
                use_arn_region: None,
                bucket_is_valid_dns: None,
                docs: "Invalid ARN, FIPS region not allowed in ARN".to_string(),
            },
            value: Err("Invalid ARN, FIPS region not allowed in ARN".to_string()),
        })
    }
}

pub fn cross_partition_error(out: &mut Vec<TableRow>) {
    let key = TableKey {
        region_regex: Some(Regex::new("[a-zA-Z0-9-]+").unwrap()),
        bucket_regex: Some(s3_access_point_regex("[a-zA-Z0-9-]+")),
        addressing_style: None,
        dualstack: None,
        accelerate: None,
        use_arn_region: Some(true),
        bucket_is_valid_dns: None,
        docs: "Wildcard fallback error for mismatch between partition and region".to_string(),
    };
    let value = Err("Invalid configuration, cross partition Access Point ARN".to_string());
    out.push(TableRow { key, value });
}

pub fn fips_meta_regions(
    region_regex: &RegionRegex,
    derived_endpoint: &DerivedEndpoint,
    out: &mut Vec<TableRow>,
) {
    let as_regex = region_regex.to_regex().as_str().to_string();
    let trimmed = as_regex
        .strip_prefix("^")
        .unwrap_or(&as_regex)
        .strip_suffix("$")
        .unwrap_or(&as_regex);
    let meta_regions = &[
        Regex::new(&format!("^fips\\-{}$", trimmed)).unwrap(),
        Regex::new(&format!("^{}\\-fips$", trimmed)).unwrap(),
    ];
    for (use_arn_region, dualstack, region_regex) in
        iproduct!(&[true, false], &[true, false], meta_regions)
    {
        let addressing_style = AddressingStyle::Auto;
        let template_region = "{bucket:2}"; // for fips, we always use the arn region
        let dualstack_segment = match dualstack {
            true => ".dualstack",
            false => "",
        };
        let key = TableKey {
            region_regex: Some(region_regex.clone()),
            bucket_regex: Some(s3_access_point_regex(&derived_endpoint.partition)),
            addressing_style: Some(addressing_style),
            dualstack: Some(*dualstack),
            accelerate: Some(false),
            use_arn_region: Some(*use_arn_region),
            bucket_is_valid_dns: None,
            docs: "s3 access points, dualstack / accelerate disabled".to_string(),
        };
        let value = match (
            use_arn_region,
            derived_endpoint.regional_endpoint,
            &derived_endpoint.uri,
        ) {
            (false, false, _) => {
                Err("Invalid configuration, client region is not a regional endpoint".to_string())
            }
            (_, _, super::Uri::CustomerProvided { .. }) => {
                Err("Invalid configuration, cannot use fips".into())
            }
            (
                ..,
                super::Uri::Templated {
                    hostname,
                    protocol,
                    raw_pattern,
                    dns_suffix,
                },
            ) => Ok(TableValue {
                uri_template: Template {
                    template: format!(
                        "{}://{{bucket:4}}-{{bucket:3}}.s3-accesspoint-fips{}.{}.{}",
                        protocol, dualstack_segment, template_region, dns_suffix
                    ),
                    keys: vec!["region", "bucket:2", "bucket:3", "bucket:4"],
                },
                bucket_regex: s3_access_point_regex(&derived_endpoint.partition),
                header_template: Default::default(),
                credential_scope: Default::default(),
                remove_bucket_from_path: true,
                region_match_regex: Some(Template {
                    template: "^fips-{bucket:2}$".to_string(),
                    keys: vec!["bucket:2"],
                }),
            }),
        };
        out.push(TableRow { key, value });
    }
}

pub fn vanilla_access_point_addressing(
    region_regex: &RegionRegex,
    derived_endpoint: &DerivedEndpoint,
    out: &mut Vec<TableRow>,
) {
    for (use_arn_region, dualstack) in iproduct!(&[true, false], &[true, false]) {
        let addressing_style = AddressingStyle::Auto;
        let template_region = match use_arn_region {
            true => "{bucket:2}",
            false => "{region}",
        };
        let dualstack_segment = match dualstack {
            true => ".dualstack",
            false => "",
        };
        let key = TableKey {
            region_regex: Some(region_regex.to_regex()),
            bucket_regex: Some(s3_access_point_regex(&derived_endpoint.partition)),
            addressing_style: Some(addressing_style),
            dualstack: Some(*dualstack),
            accelerate: Some(false),
            use_arn_region: Some(*use_arn_region),
            bucket_is_valid_dns: None,
            docs: "s3 access points, dualstack / accelerate disabled".to_string(),
        };
        let value = match (use_arn_region, derived_endpoint.regional_endpoint, &derived_endpoint.uri) {
            (false, false, _) => {
                Err("Invalid configuration, client region is not a regional endpoint".to_string())
            },
            (_, _, super::Uri::CustomerProvided { support: super::EndpointSupport { access_points: false, ..} }) => Err("Using an AP was specified but the URL does not support access points".to_string()),
            (_, _, super::Uri::CustomerProvided { support: super::EndpointSupport { access_points: true, ..} }) => Ok(TableValue {
                uri_template: Template {
                    template: "{protocol}://{bucket:4}-{bucket:3}.{endpoint_url}".to_string(),
                    keys: vec!["protocol", "endpoint_url", "bucket:3", "bucket:4"],
                },
                bucket_regex: s3_access_point_regex(&derived_endpoint.partition),
                header_template: Default::default(),
                credential_scope: Default::default(),
                remove_bucket_from_path: true,
                region_match_regex: None
            }),
            (_, _, super::Uri::Templated { protocol, dns_suffix, .. }) => Ok(TableValue {
                uri_template: Template {
                    template: format!(
                        "{protocol}://{{bucket:4}}-{{bucket:3}}.s3-accesspoint{dualstack}.{region}.{dns_suffix}",
                        protocol = protocol,
                        dualstack = dualstack_segment,
                        region = template_region,
                        dns_suffix = dns_suffix
                    ),
                    keys: vec!["region", "bucket:2", "bucket:3", "bucket:4"],
                },
                bucket_regex: s3_access_point_regex(&derived_endpoint.partition),
                header_template: Default::default(),
                credential_scope: Default::default(),
                remove_bucket_from_path: true,
                region_match_regex: if *use_arn_region {
                    None
                } else {
                    Some(Template {
                        template: "{bucket:2}".to_string(),
                        keys: vec!["bucket:2"],
                    })
                },
            }),
        };
        out.push(TableRow { key, value });
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn validate_regex() {
        assert!(s3_access_point_regex("s3")
            .is_match("arn:aws:s3:us-west-2:123456789012:accesspoint:fink"))
    }
}

/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

use http::header::HeaderName;
use http::HeaderValue;
use sha2::{Digest, Sha256};
use smithy_http::middleware::MapRequest;
use smithy_http::operation::Request;
use std::borrow::Cow;

#[non_exhaustive]
#[derive(Clone, Default)]
pub struct AmzSha256;
impl AmzSha256 {
    pub fn new() -> Self {
        AmzSha256
    }
}

impl MapRequest for AmzSha256 {
    type Error = std::convert::Infallible;

    fn apply(&self, request: Request) -> Result<Request, Self::Error> {
        request.augment(|mut req, conf| {
            let checksum = match req.body().bytes() {
                Some(data) => {
                    let mut hasher = Sha256::new();
                    hasher.update(data);
                    Cow::Owned(hex::encode(hasher.finalize()))
                }
                None => Cow::Borrowed("UNSIGNED_PAYLOAD"),
            };
            req.headers_mut().append(
                HeaderName::from_static("x-amz-content-sha256"),
                HeaderValue::from_str(checksum.as_ref())
                    .expect("checksum should always be valid header"),
            );
            Ok(req)
        })
    }
}

#[cfg(test)]
mod test {
    #[test]
    fn adds_header() {}
}

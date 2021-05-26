/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

use crate::query_writer::QueryWriter;
use aws_auth::Credentials;
use aws_sigv4_poc::{SignableBody, SignedBodyHeaderType, SigningSettings, UriEncoding};
use aws_types::region::SigningRegion;
use aws_types::SigningService;
use http::header::HeaderName;
use http::HeaderValue;
use smithy_http::body::SdkBody;
use std::error::Error;
use std::time::SystemTime;

#[derive(Eq, PartialEq, Clone, Copy)]
pub enum SigningAlgorithm {
    SigV4,
}

#[derive(Eq, PartialEq, Clone, Copy)]
pub enum HttpSignatureType {
    /// A signature for a full http request should be computed, with header updates applied to the signing result.
    HttpRequestHeaders,
    /* Currently Unsupported
    /// A signature for a full http request should be computed, with query param updates applied to the signing result.
    ///
    /// This is typically used for presigned URLs & is currently unsupported.
    HttpRequestQueryParams,
     */
}

/// Signing Configuration for an Operation
///
/// Although these fields MAY be customized on a per request basis, they are generally static
/// for a given operation
#[derive(Clone, PartialEq, Eq)]
pub struct OperationSigningConfig {
    pub algorithm: SigningAlgorithm,
    pub signature_type: HttpSignatureType,
    pub signing_options: SigningOptions,
}

impl OperationSigningConfig {
    /// Placeholder method to provide a the signing configuration used for most operation
    ///
    /// In the future, we will code-generate a default configuration for each service
    pub fn default_config() -> Self {
        OperationSigningConfig {
            algorithm: SigningAlgorithm::SigV4,
            signature_type: HttpSignatureType::HttpRequestHeaders,
            signing_options: SigningOptions {
                double_uri_encode: true,
                content_sha256_header: false,
            },
        }
    }
}

#[derive(Clone, Eq, PartialEq)]
#[non_exhaustive]
pub struct SigningOptions {
    pub double_uri_encode: bool,
    pub content_sha256_header: bool,
    /*
    Currently unsupported:
    pub normalize_uri_path: bool,
    pub omit_session_token: bool,
     */
}

/// Signing Configuration for an individual Request
///
/// These fields may vary on a per-request basis
#[derive(Clone, PartialEq, Eq)]
pub struct RequestConfig<'a> {
    pub request_ts: SystemTime,
    pub region: &'a SigningRegion,
    pub service: &'a SigningService,
}

#[derive(Clone, Default)]
pub struct SigV4Signer {
    // In the future, the SigV4Signer will use the CRT signer. This will require constructing
    // and holding an instance of the signer, so prevent people from constructing a SigV4Signer without
    // going through the constructor.
    _private: (),
}

pub type SigningError = Box<dyn Error + Send + Sync>;

impl SigV4Signer {
    pub fn new() -> Self {
        SigV4Signer { _private: () }
    }

    pub fn presigned_url(
        &self,
        operation_config: &OperationSigningConfig,
        request_config: &RequestConfig<'_>,
        credentials: &Credentials,
        request: &http::Request<SdkBody>,
    ) -> Result<http::Uri, SigningError> {
        let mut query_writer = QueryWriter::new(request.uri());
        for (key, value) in
            Self::signature_components(operation_config, request_config, credentials, request)?
        {
            query_writer.insert(
                key,
                value
                    .to_str()
                    .expect("signer should produce headers that are strings"),
            )
        }
        Ok(query_writer.build())
    }

    /// Sign a request using the SigV4 Protocol
    ///
    /// Although the direct signing implementation MAY be used directly. End users will not typically
    /// interact with this code. It is generally used via middleware in the request pipeline. See [`SigV4SigningStage`](crate::middleware::SigV4SigningStage).
    pub fn sign(
        &self,
        operation_config: &OperationSigningConfig,
        request_config: &RequestConfig<'_>,
        credentials: &Credentials,
        request: &mut http::Request<SdkBody>,
    ) -> Result<(), SigningError> {
        // A body that is already in memory can be signed directly. A  body that is not in memory
        // (any sort of streaming body) will be signed via UNSIGNED-PAYLOAD.
        // The final enhancement that will come a bit later is writing a `SignableBody::Precomputed`
        // into the property bag when we have a sha 256 middleware that can compute a streaming checksum
        // for replayable streams but currently even replayable streams will result in `UNSIGNED-PAYLOAD`
        for (key, value) in
            Self::signature_components(operation_config, request_config, credentials, request)?
        {
            request
                .headers_mut()
                .append(HeaderName::from_static(key), value);
        }

        Ok(())
    }

    fn signature_components(
        operation_config: &OperationSigningConfig,
        request_config: &RequestConfig<'_>,
        credentials: &Credentials,
        request: &http::Request<SdkBody>,
    ) -> Result<impl Iterator<Item = (&'static str, HeaderValue)>, SigningError> {
        let mut settings = SigningSettings::default();
        settings.uri_encoding = if operation_config.signing_options.double_uri_encode {
            UriEncoding::Double
        } else {
            UriEncoding::Single
        };
        settings.signed_body_header = if operation_config.signing_options.content_sha256_header {
            SignedBodyHeaderType::XAmzSha256
        } else {
            SignedBodyHeaderType::NoHeader
        };
        let sigv4_config = aws_sigv4_poc::Config {
            access_key: credentials.access_key_id(),
            secret_key: credentials.secret_access_key(),
            security_token: credentials.session_token(),
            region: request_config.region.as_ref(),
            svc: request_config.service.as_ref(),
            date: request_config.request_ts,
            settings,
        };
        let signable_body = request
            .body()
            .bytes()
            .map(SignableBody::Bytes)
            .unwrap_or(SignableBody::UnsignedPayload);
        aws_sigv4_poc::sign_core(request, signable_body, &sigv4_config)
    }
}

#[cfg(test)]
mod test {
    use crate::signer::{OperationSigningConfig, RequestConfig, SigV4Signer};
    use aws_auth::Credentials;
    use aws_types::region::{Region, SigningRegion};
    use aws_types::SigningService;
    use http::header::HOST;
    use http::{Method, Uri};
    use smithy_http::body::SdkBody;
    use std::time::{Duration, UNIX_EPOCH};

    #[test]
    fn presign_url() {
        let operation_config = OperationSigningConfig::default_config();
        let signing_region = SigningRegion::from(Region::new("us-east-2"));
        let signing_service = SigningService::from_static("s3");
        let request_config = RequestConfig {
            request_ts: UNIX_EPOCH + Duration::from_secs(123456),
            region: &signing_region,
            service: &signing_service,
        };

        let signer = SigV4Signer::new();
        let creds = Credentials::from_keys("AKIDEXAMPLE", "aasdfadsfSECRET", None);
        let request = http::Request::builder()
            .method(Method::GET)
            .uri(Uri::from_static(
                "https://iam.amazonaws.com/?Action=ListUsers&Version=2010-05-08",
            ))
            .header(HOST, "iam.amazonaws.com")
            .body(SdkBody::empty())
            .unwrap();
        let presigned_uri = signer
            .presigned_url(&operation_config, &request_config, &creds, &request)
            .expect("failed to generate presigned uri");
        assert_eq!(presigned_uri, Uri::from_static("https://iam.amazonaws.com?Action=ListUsers&Version=2010-05-08&X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=AKIDEXAMPLE%2F20150830%2Fus-east-1%2Fiam%2Faws4_request&X-Amz-Date=20150830T123600Z&X-Amz-Expires=60&X-Amz-SignedHeaders=content-type%3Bhost&X-Amz-Signature=37ac2f4fde00b0ac9bd9eadeb459b1bbee224158d66e7ae5fcadb70b2d181d02"))
    }
}

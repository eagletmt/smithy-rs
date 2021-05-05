/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

use smithy_xml::decode::{expect_data, next_start_element, Document, ScopedDecoder, XmlError};
use std::convert::TryFrom;

pub fn is_error<B>(response: &http::Response<B>) -> bool {
    !response.status().is_success()
}

pub fn body_is_error(body: &[u8]) -> Result<bool, XmlError> {
    let mut doc = Document::try_from(body)?;
    let scoped = doc.scoped()?;
    let root_el = scoped.start_el().name.local.as_ref();
    Ok(root_el == "Error")
}

pub fn error_scope<'a, 'b>(doc: &'a mut Document<'b>) -> Result<ScopedDecoder<'b, 'a>, XmlError> {
    let scoped = doc.scoped()?;
    if scoped.start_el().name.local.as_ref() != "Error" {
        return Err(XmlError::Other {
            msg: "expected error as root",
        });
    }
    Ok(scoped)
}

pub fn parse_generic_error(body: &[u8]) -> Result<smithy_types::Error, XmlError> {
    let mut doc = Document::try_from(body)?;
    let mut root = doc.scoped()?;
    let mut err = smithy_types::Error::default();
    while let Some(el) = next_start_element(&mut root) {
        match el.name.local.as_ref() {
            "Code" => err.code = Some(String::from(expect_data(&mut root)?)),
            "Message" => err.message = Some(String::from(expect_data(&mut root)?)),
            "RequestId" => err.request_id = Some(String::from(expect_data(&mut root)?)),
            _ => {}
        }
    }
    Ok(err)
}

#[cfg(test)]
mod test {
    use super::{body_is_error, parse_generic_error};

    #[test]
    fn parse_unwrapped_error() {
        let xml = br#"<Error>
    <Type>Sender</Type>
    <Code>InvalidGreeting</Code>
    <Message>Hi</Message>
    <AnotherSetting>setting</AnotherSetting>
    <RequestId>foo-id</RequestId>
</Error>"#;
        assert!(body_is_error(xml).unwrap());
        let parsed = parse_generic_error(xml).expect("valid xml");
        assert_eq!(parsed.request_id(), Some("foo-id"));
        assert_eq!(parsed.message(), Some("Hi"));
        assert_eq!(parsed.code(), Some("InvalidGreeting"));
    }
}

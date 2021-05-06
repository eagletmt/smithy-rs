/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

use smithy_xml::decode::{expect_data, Document, ScopedDecoder, XmlError};
use std::convert::TryFrom;

pub fn is_error<B>(response: &http::Response<B>) -> bool {
    !response.status().is_success()
}

pub fn body_is_error(body: &[u8]) -> Result<bool, XmlError> {
    let mut doc = Document::try_from(body)?;
    let scoped = doc.scoped()?;
    let root_el = scoped.start_el().name.local.as_ref();
    Ok(root_el == "ErrorResponse")
}

pub fn parse_generic_error(body: &[u8]) -> Result<smithy_types::Error, XmlError> {
    let mut doc = Document::try_from(body)?;
    let mut root = doc.scoped()?;
    let mut err = smithy_types::Error::default();
    while let Some(mut tag) = root.next_tag() {
        match tag.start_el().local() {
            "Error" => {
                while let Some(mut error_field) = tag.next_tag() {
                    match error_field.start_el().local() {
                        "Code" => err.code = Some(String::from(expect_data(&mut error_field)?)),
                        "Message" => {
                            err.message = Some(String::from(expect_data(&mut error_field)?))
                        }
                        _ => {}
                    }
                }
            }
            "RequestId" => err.request_id = Some(String::from(expect_data(&mut tag)?)),
            _ => {}
        }
    }
    Ok(err)
}

#[allow(unused)]
pub fn error_scope<'a, 'b>(doc: &'a mut Document<'b>) -> Result<ScopedDecoder<'b, 'a>, XmlError> {
    let root = doc
        .next_start_el()
        .ok_or(XmlError::Other { msg: "no root" })?;
    if root.name.local.as_ref() != "ErrorResponse" {
        return Err(XmlError::Other {
            msg: "expected ErrorResponse as root",
        });
    }

    while let Some(el) = doc.next_start_el() {
        match el.name.local.as_ref() {
            "Error" => {
                return Ok(doc.scoped_to(el));
            }
            _ => {}
        }
    }
    Err(XmlError::Other {
        msg: "No Error found inside of ErrorResponse",
    })
}

#[cfg(test)]
mod test {
    use super::{body_is_error, parse_generic_error};
    use crate::rest_xml_wrapped_errors::error_scope;
    use smithy_types::Document;
    use std::convert::TryFrom;

    #[test]
    fn parse_wrapped_error() {
        let xml = br#"<ErrorResponse>
    <Error>
        <Type>Sender</Type>
        <Code>InvalidGreeting</Code>
        <Message>Hi</Message>
        <AnotherSetting>setting</AnotherSetting>
        <Ignore><This/></Ignore>
    </Error>
    <RequestId>foo-id</RequestId>
</ErrorResponse>"#;
        assert!(body_is_error(xml).unwrap());
        let parsed = parse_generic_error(xml).expect("valid xml");
        assert_eq!(parsed.request_id(), Some("foo-id"));
        assert_eq!(parsed.message(), Some("Hi"));
        assert_eq!(parsed.code(), Some("InvalidGreeting"));
    }

    #[test]
    fn test_error_scope() {
        let xml: &[u8] = br#"<ErrorResponse>
    <RequestId>foo-id</RequestId>
    <MorePreamble>foo-id</RequestId>
    <Error>
        <Type>Sender</Type>
        <Code>InvalidGreeting</Code>
        <Message>Hi</Message>
        <AnotherSetting>setting</AnotherSetting>
        <Ignore><This/></Ignore>
    </Error>
    <RequestId>foo-id</RequestId>
</ErrorResponse>"#;
        let mut doc = smithy_xml::decode::Document::try_from(xml).expect("valid");
        let mut error = error_scope(&mut doc).expect("contains error");
        let mut keys = vec![];
        while let Some(tag) = error.next_tag() {
            keys.push(tag.start_el().local().to_owned());
            // read this the full contents of this element
        }
        assert_eq!(
            keys,
            vec!["Type", "Code", "Message", "AnotherSetting", "Ignore",]
        )
    }
}

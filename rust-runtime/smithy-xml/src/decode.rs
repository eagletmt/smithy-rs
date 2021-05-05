/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

use std::borrow::Cow;
use std::convert::TryFrom;
use xmlparser::{ElementEnd, Token, Tokenizer};

#[derive(Eq, PartialEq, Debug)]
pub enum XmlError {
    InvalidXml(xmlparser::Error),
    Other { msg: &'static str },
}

#[derive(PartialEq, Debug)]
pub struct Name<'a> {
    pub prefix: Cow<'a, str>,
    pub local: Cow<'a, str>,
}

#[derive(Debug, PartialEq)]
pub struct Attr<'a> {
    name: Name<'a>,
    value: Cow<'a, str>,
}

#[derive(Debug, PartialEq)]
pub struct StartEl<'a> {
    pub name: Name<'a>,
    pub attributes: Vec<Attr<'a>>,
}

impl<'a> StartEl<'a> {
    pub fn new(local: &'a str, prefix: &'a str) -> Self {
        Self {
            name: Name {
                local: local.into(),
                prefix: prefix.into(),
            },
            attributes: vec![],
        }
    }

    pub fn attr<'b>(&'b self, key: &'b str) -> Option<&'b str> {
        self.attributes
            .iter()
            .find(|attr| attr.name.local == key)
            .map(|attr| attr.value.as_ref())
    }
}

impl StartEl<'_> {
    pub fn end_el(&self, el: ElementEnd) -> bool {
        match el {
            ElementEnd::Open => false,
            ElementEnd::Close(prefix, local) => {
                prefix.as_str() == self.name.prefix && local.as_str() == self.name.local
            }
            ElementEnd::Empty => false,
        }
    }
}

pub struct Document<'a>(Tokenizer<'a>);

impl<'a> TryFrom<&'a [u8]> for Document<'a> {
    type Error = XmlError;

    fn try_from(value: &'a [u8]) -> Result<Self, Self::Error> {
        Ok(Document::new(std::str::from_utf8(value).map_err(|_| {
            XmlError::Other {
                msg: "invalid utf8",
            }
        })?))
    }
}

impl<'inp> Document<'inp> {
    pub fn new(doc: &'inp str) -> Self {
        Document(Tokenizer::from(doc))
    }

    pub fn next_start_el(&mut self) -> Option<StartEl<'inp>> {
        next_start_element(&mut self.0)
    }

    pub fn scoped<'a>(&'a mut self) -> Result<ScopedDecoder<'inp, 'a>, XmlError> {
        let start_el = next_start_element(&mut self.0).ok_or(XmlError::Other {
            msg: "No root element",
        })?;
        Ok(ScopedDecoder {
            tokenizer: &mut self.0,
            start_el,
            depth: 0,
            terminated: false,
        })
    }

    pub fn scoped_to<'a>(&'a mut self, start_el: StartEl<'inp>) -> ScopedDecoder<'inp, 'a> {
        ScopedDecoder {
            tokenizer: &mut self.0,
            start_el,
            terminated: false,
            depth: 0,
        }
    }
}

pub struct ScopedDecoder<'inp, 'a> {
    tokenizer: &'a mut Tokenizer<'inp>,
    start_el: StartEl<'inp>,
    depth: u8,
    terminated: bool,
}

impl Drop for ScopedDecoder<'_, '_> {
    fn drop(&mut self) {
        for _ in self {}
    }
}

impl<'inp> ScopedDecoder<'inp, '_> {
    pub fn start_el<'a>(&'a self) -> &'a StartEl<'inp> {
        &self.start_el
    }

    pub fn scoped_to<'a>(&'a mut self, start_el: StartEl<'inp>) -> ScopedDecoder<'inp, 'a> {
        ScopedDecoder {
            tokenizer: &mut self.tokenizer,
            start_el,
            depth: 0,
            terminated: false,
        }
    }
}

impl<'inp, 'a> Iterator for ScopedDecoder<'inp, 'a> {
    type Item = Result<Token<'inp>, xmlparser::Error>;

    fn next(&mut self) -> Option<Self::Item> {
        if self.terminated {
            return None;
        }
        let tok = self.tokenizer.next()?.ok()?;
        match tok {
            Token::ElementStart { prefix, local, .. }
                if prefix.as_str() == self.start_el.name.prefix
                    && local.as_str() == self.start_el.name.local =>
            {
                self.depth += 1
            }
            Token::ElementEnd { end, .. } if self.start_el.end_el(end) && self.depth == 0 => {
                self.terminated = true;
                return None;
            }
            Token::ElementEnd { end, .. } if self.start_el.end_el(end) => {
                self.depth -= 1;
            }
            _ => {}
        }
        Some(Ok(tok))
    }
}

fn unescape(s: &str) -> Cow<str> {
    s.into()
}

pub fn next_start_element<'a, 'inp>(
    scoped: &'a mut impl Iterator<Item = Result<Token<'inp>, xmlparser::Error>>,
) -> Option<StartEl<'inp>> {
    let mut out = StartEl::new("", "");
    loop {
        match scoped.next() {
            None => return None,
            Some(Ok(Token::ElementStart { local, prefix, .. })) => {
                out.name.local = unescape(local.as_str());
                out.name.prefix = unescape(prefix.as_str());
            }
            Some(Ok(Token::Attribute {
                prefix,
                local,
                value,
                ..
            })) => out.attributes.push(Attr {
                name: Name {
                    local: unescape(local.as_str()),
                    prefix: unescape(prefix.as_str()),
                },
                value: unescape(value.as_str()),
            }),
            Some(Ok(Token::ElementEnd {
                end: ElementEnd::Open,
                ..
            })) => break,
            Some(Ok(Token::ElementEnd {
                end: ElementEnd::Empty,
                ..
            })) => break,
            _ => {}
        }
    }
    Some(out)
}

pub fn expect_data<'a, 'inp>(
    tokens: &'a mut impl Iterator<Item = Result<Token<'inp>, xmlparser::Error>>,
) -> Result<&'inp str, XmlError> {
    loop {
        match dbg!(tokens.next()) {
            None => return Ok(""),
            Some(Ok(Token::Text { text })) => return Ok(text.as_str().trim()),
            Some(Ok(Token::ElementStart { .. })) => {
                return Err(XmlError::Other {
                    msg: "expected data found element start ",
                })
            }
            Some(Err(e)) => return Err(XmlError::InvalidXml(e)),
            _ => {}
        }
    }
}

#[cfg(test)]
mod test {
    use crate::decode::{
        expect_data, next_start_element, Attr, Document, Name, ScopedDecoder, StartEl,
    };
    use xmlparser::Tokenizer;

    #[test]
    fn scoped_tokens() {
        let xml = r#"<Response><A></A></Response>"#;
        let mut doc = Document::new(xml);
        let mut scoped = doc.scoped().expect("valid document");
        assert_eq!(next_start_element(&mut scoped), Some(StartEl::new("A", "")));
        assert_eq!(next_start_element(&mut scoped), None)
    }

    #[test]
    fn handle_depth_properly() {
        let xml = r#"<Response><Response></Response><A/></Response>"#;
        let mut doc = Document::new(xml);
        let mut scoped = doc.scoped().expect("valid document");
        assert_eq!(
            next_start_element(&mut scoped),
            Some(StartEl::new("Response", ""))
        );
        assert_eq!(next_start_element(&mut scoped), Some(StartEl::new("A", "")));
        assert_eq!(next_start_element(&mut scoped), None)
    }

    #[test]
    fn self_closing() {
        let xml = r#"<Response/>"#;
        let mut doc = Document::new(xml);
        let mut scoped = doc.scoped().expect("valid doc");
        assert_eq!(scoped.start_el, StartEl::new("Response", ""));
        assert_eq!(next_start_element(&mut scoped), None)
    }

    #[test]
    fn terminate_scope() {
        let xml = r#"<Response><Struct><A/><Also/></Struct><More/></Response>"#;
        let mut doc = Document::new(xml);
        let mut response_iter = doc.scoped().expect("valid doc");
        let struct_el = next_start_element(&mut response_iter).unwrap();
        let mut struct_iter = response_iter.scoped_to(struct_el);
        assert_eq!(
            next_start_element(&mut struct_iter),
            Some(StartEl::new("A", ""))
        );
        // When the inner iter is dropped, it will read to the end of its scope
        // prevent accidental behavior where we didn't read a full node
        drop(struct_iter);
        assert_eq!(
            next_start_element(&mut response_iter),
            Some(StartEl::new("More", ""))
        );
    }

    #[test]
    fn read_data_invalid() {
        let xml = r#"<Response><A></A></Response>"#;
        let mut tokenizer = Tokenizer::from(xml);
        let root = next_start_element(&mut tokenizer).unwrap();
        let mut scoped = ScopedDecoder::from_tokenizer(root, &mut tokenizer);
        expect_data(&mut scoped).expect_err("no data");
    }

    #[test]
    fn read_data() {
        let xml = r#"<Response>hello</Response>"#;
        let mut tokenizer = Tokenizer::from(xml);
        let root = next_start_element(&mut tokenizer).unwrap();
        let mut scoped = ScopedDecoder::from_tokenizer(root, &mut tokenizer);
        assert_eq!(expect_data(&mut scoped), Ok("hello"));
    }

    #[test]
    fn read_attributes() {
        let xml = r#"<Response xsi:type="CanonicalUser">hello</Response>"#;
        let mut tokenizer = Tokenizer::from(xml);
        let root = next_start_element(&mut tokenizer).unwrap();
        assert_eq!(
            root.attributes,
            vec![Attr {
                name: Name {
                    prefix: "xsi".into(),
                    local: "type".into()
                },
                value: "CanonicalUser".into()
            }]
        )
    }

    #[test]
    fn escape_data() {
        let xml = r#"<Response>&gt;</Response>"#;
        let mut tokenizer = Tokenizer::from(xml);
        let root = next_start_element(&mut tokenizer).unwrap();
        let mut scoped = ScopedDecoder::from_tokenizer(root, &mut tokenizer);
        assert_eq!(expect_data(&mut scoped), Ok(">"));
    }
}

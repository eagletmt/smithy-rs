/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

use crate::urlencode::BASE_SET;
use percent_encoding::utf8_percent_encode;
/// Formatting values into the query string as specified in
/// [httpQuery](https://awslabs.github.io/smithy/1.0/spec/core/http-traits.html#httpquery-trait)
use smithy_types::Instant;
use std::fmt::Debug;

pub fn fmt_default<T: Debug>(t: T) -> String {
    format!("{:?}", t)
}

pub fn fmt_string<T: AsRef<str>>(t: T) -> String {
    utf8_percent_encode(t.as_ref(), BASE_SET).to_string()
}

pub fn fmt_timestamp(t: &Instant, format: smithy_types::instant::Format) -> String {
    fmt_string(t.fmt(format))
}

/// Simple abstraction to enable appending params to a string as query params
///
/// ```rust
/// use smithy_http::query::Writer;
/// let mut s = String::from("www.example.com");
/// let mut q = Writer::new(&mut s);
/// q.push_kv("key", "value");
/// q.push_v("another_value");
/// assert_eq!(s, "www.example.com?key=value&another_value");
/// ```
pub struct Writer<'a> {
    out: &'a mut String,
    prefix: char,
}

impl<'a> Writer<'a> {
    pub fn new(out: &'a mut String) -> Self {
        Writer { out, prefix: '?' }
    }

    pub fn push_kv(&mut self, k: &str, v: &str) {
        self.out.push(self.prefix);
        self.out.push_str(k);
        self.out.push('=');
        self.out.push_str(v);
        self.prefix = '&';
    }

    pub fn push_v(&mut self, v: &str) {
        self.out.push(self.prefix);
        self.out.push_str(v);
    }
}

#[cfg(test)]
mod test {
    use crate::query::fmt_string;

    #[test]
    fn url_encode() {
        assert_eq!(fmt_string("y̆").as_str(), "y%CC%86");
        assert_eq!(fmt_string(" ").as_str(), "%20");
        assert_eq!(fmt_string("foo/baz%20").as_str(), "foo%2Fbaz%2520");
        assert_eq!(fmt_string("&=").as_str(), "%26%3D");
        assert_eq!(fmt_string("🐱").as_str(), "%F0%9F%90%B1");
        // `:` needs to be encoded, but only for AWS services
        assert_eq!(fmt_string("a:b"), "a%3Ab")
    }
}

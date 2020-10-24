/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

// TODO: there is no compelling reason to have this be a shared crate—we should vendor this
// module into the individual crates
pub mod label {
    use smithy_types::Instant;
    use std::fmt::Debug;

    pub fn fmt_default<T: Debug>(t: T) -> String {
        format!("{:?}", t)
    }

    pub fn fmt_string<T: AsRef<str>>(t: T, greedy: bool) -> String {
        let s = t.as_ref();
        if greedy {
            s.to_owned()
        } else {
            s.replace("/", "%2F")
        }
    }

    pub fn fmt_timestamp(t: &Instant, format: smithy_types::instant::Format) -> String {
        t.fmt(format)
    }
}

pub mod header {}

pub mod encode {
    use std::io::Read;

    const BASE64_ENCODE_TABLE: &[u8; 64] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

    /// A correct, small, but not especially fast
    /// base64 implementation
    pub fn base64<T: AsRef<[u8]>>(inp: T) -> String {
        let inp = inp.as_ref();
        fn inner(inp: &[u8]) -> String {
            // Base 64 encodes groups of 6 bits into characters—this means that each
            // 3 byte group (24 bits) is encoded into 4 base64 characters.
            let size = inp.len();
            let block_ct = (size + 2) / 3;
            let mut output = String::with_capacity(4 * block_ct);
            // pattern masks
            let first_6 = 0xFC;
            let last_2 = 0x03;
            let first_4 = 0xF0;
            let last_4 = 0x0F;
            let first_2 = 0xC0;
            let last_6 = 0x3F;
            for chunk in inp.chunks(3) {
                match chunk {
                    [b1,b2,b3] => {
                        let o1 = ((b1 & first_6) >> 2);
                        let o2 = ((b1 & last_2) << 4) | ((b2 & first_4) >> 4);
                        let o3 = ((b2 & last_4) << 2) | ((b3 & first_2) >> 6);
                        let o4 = (b3 & last_6);
                        let idxs = [o1, o2, o3, o4];
                        for idx in idxs.iter() {
                            output.push(BASE64_ENCODE_TABLE[*idx as usize] as char)
                        }
                    },
                    [b1, b2] => {
                        let o1 = ((b1 & first_6) >> 2);
                        let o2 = ((b1 & last_2) << 4) | ((b2 & first_4) >> 4);
                        let o3 = ((b2 & last_4) << 2);
                        let idxs = [o1, o2, o3];
                        for idx in idxs.iter() {
                            output.push(BASE64_ENCODE_TABLE[*idx as usize] as char)
                        }
                        output.push('=');
                    },
                    [b1] => {
                        let o1 = ((b1 & first_6) >> 2);
                        let o2 = ((b1 & last_2) << 4);
                        let idxs = [o1, o2];
                        for idx in idxs.iter() {
                            output.push(BASE64_ENCODE_TABLE[*idx as usize] as char)
                        }
                        output.push_str("==");
                    }
                    _ => panic!("unexpected slice length")
                }
            }
            output
        }
        inner(inp)

    }

    #[cfg(test)]
    mod test {
        use crate::encode::base64;

        #[test]
        fn test_base64() {
            assert_eq!(base64("abc"), "YWJj");
            assert_eq!(base64("any carnal pleasure."), "YW55IGNhcm5hbCBwbGVhc3VyZS4=");
            assert_eq!(base64("any carnal pleasure"), "YW55IGNhcm5hbCBwbGVhc3VyZQ==");
        }

        #[test]
        fn test_base64_long() {
            let decoded = "Alas, eleventy-one years is far too short a time to live among such excellent and admirable hobbits. I don't know half of you half as well as I should like, and I like less than half of you half as well as you deserve.";
            let encoded = "QWxhcywgZWxldmVudHktb25lIHllYXJzIGlzIGZhciB0b28gc2hvcnQgYSB0aW1lIHRvIGxpdmUgYW1vbmcgc3VjaCBleGNlbGxlbnQgYW5kIGFkbWlyYWJsZSBob2JiaXRzLiBJIGRvbid0IGtub3cgaGFsZiBvZiB5b3UgaGFsZiBhcyB3ZWxsIGFzIEkgc2hvdWxkIGxpa2UsIGFuZCBJIGxpa2UgbGVzcyB0aGFuIGhhbGYgb2YgeW91IGhhbGYgYXMgd2VsbCBhcyB5b3UgZGVzZXJ2ZS4=";
            assert_eq!(base64(decoded), encoded);
        }

        #[test]
        fn test_base64_utf8() {
            let decoded = "ユニコードとはか？";
            let encoded = "44Om44OL44Kz44O844OJ44Go44Gv44GL77yf";
            assert_eq!(base64(decoded), encoded);
        }
        #[test]
        fn test_base64_control_chars() {
            let decoded = "hello\tworld\n";
            let encoded = "aGVsbG8Jd29ybGQK";
            assert_eq!(base64(decoded), encoded);
        }
    }
}

pub mod query {
    use std::fmt::Debug;
    use smithy_types::Instant;

    const HEX_CHARS: &[u8; 16] = b"0123456789ABCDEF";

    pub fn fmt_default<T: Debug>(t: T) -> String {
        format!("{:?}", t)
    }

    pub fn fmt_string<T: AsRef<str>>(t: T) -> String {
        let bytes = t.as_ref();
        let final_capacity = bytes.chars().map(|c| if is_valid_query(c) {
            1
        } else {
            c.len_utf8() * 3
        }).sum();
        let mut out = String::with_capacity(final_capacity);
        for char in bytes.chars() {
            url_encode(char, &mut out);
        }
        debug_assert_eq!(out.capacity(), final_capacity);
        out
    }

    pub fn fmt_timestamp(t: &Instant, format: smithy_types::instant::Format) -> String {
        t.fmt(format)
    }

    fn is_valid_query(c: char) -> bool {
        // unreserved
        let explicit_invalid = |c: char| match c {
            '&' | '=' => false,
            _ => true
        };
        let unreserved =
            |c: char| c.is_alphanumeric() || c == '-' || c == '.' || c == '_' || c == '~';
        let sub_delims = |c: char| match c {
            '!' | '$' | '\'' | '(' | ')' | '*' | '+' | ',' | ';' => true,
            // TODO: should &/= be url encoded?
            '&' | '=' => false,
            _ => false,
        };
        let p_char = |c: char| unreserved(c) || sub_delims(c) || c == ':' || c == '@';
        explicit_invalid(c) && (p_char(c) || c == '/' || c == '?')
    }

    fn url_encode(c: char, buff: &mut String) {
        if is_valid_query(c) {
            buff.push(c)
        } else {
            let mut inner_buff = [0; 2];
            let u8_slice = c.encode_utf8(&mut inner_buff).as_bytes();
            for c in u8_slice {
                let upper = (c & 0xf0) >> 4;
                let lower = c & 0x0f;
                buff.push('%');
                buff.push(HEX_CHARS[upper as usize] as char);
                buff.push(HEX_CHARS[lower as usize] as char);
            }
        }
    }

    pub fn write(inp: Vec<(&str, String)>, out: &mut String) {
        let mut prefix = '?';
        for (k, v) in inp {
            out.push(prefix);
            out.push_str(k);
            out.push_str("=");
            out.push_str(&v);
            prefix = '&';
        };
    }


    #[cfg(test)]
    mod test {
        use crate::query::{is_valid_query, fmt_string};

        #[test]
        fn test_valid_query_chars() {
            assert_eq!(is_valid_query(' '), false);
            assert_eq!(is_valid_query('a'), true);
            assert_eq!(is_valid_query('/'), true);
            assert_eq!(is_valid_query('%'), false);
        }

        #[test]
        fn test_url_encode() {
            assert_eq!(fmt_string("y̆").as_str(), "y%CC%86");
            assert_eq!(fmt_string(" ").as_str(), "%20");
            assert_eq!(fmt_string("foo/baz%20").as_str(), "foo/baz%2520");
            assert_eq!(fmt_string("&=").as_str(), "%26%3D");
        }
    }
}

/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

/// A correct, small, but not especially fast
/// base64 implementation

// TODO: Fuzz and test against the base64 crate
const BASE64_ENCODE_TABLE: &[u8; 64] =
    b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

const BASE64_DECODE_TABLE: &[Option<u8>; 256] = &decode_table();

const fn decode_table() -> [Option<u8>; 256] {
    let mut output = [None; 256];
    let mut i = 0;
    while i < 256 {
        if i == 61 {
            output[i] = Some(0xff);
        } else {
            let mut index = 0;
            // inline const index-of implementation
            while index < BASE64_ENCODE_TABLE.len() {
                if BASE64_ENCODE_TABLE[index] as usize == i {
                    output[i as usize] = Some(index as u8);
                    break;
                }
                index += 1;
            }
        }
        i += 1;
    }
    output
}

pub fn encode<T: AsRef<[u8]>>(inp: T) -> String {
    let inp = inp.as_ref();
    encode_inner(inp)
}


fn encode_inner(inp: &[u8]) -> String {
    // Base 64 encodes groups of 6 bits into characters—this means that each
    // 3 byte group (24 bits) is encoded into 4 base64 characters.
    let char_ct = ((inp.len() + 2) / 3) * 4;
    let mut output = String::with_capacity(char_ct);
    for chunk in inp.chunks(3) {
        let mut block: i32 = 0;
        // Write the chunks into the beginning of a 32 bit int
        for (idx, chunk) in chunk.iter().enumerate() {
            block |= (*chunk as i32) << ((3 - idx) * 8);
        }
        let num_sextets = ((chunk.len() * 8) + 5) / 6;
        for idx in 0..num_sextets {
            let slice = block >> (26 - (6 * idx));
            let idx = (slice as u8) & 0b0011_1111;
            output.push(BASE64_ENCODE_TABLE[idx as usize] as char);
        }
        for _ in 0..(4 - num_sextets) {
            output.push('=');
        }
    }
    // be sure we got it right
    debug_assert_eq!(output.capacity(), char_ct);
    output
}

pub fn decode<T: AsRef<str>>(inp: T) -> Result<Vec<u8>, DecodeError> {
    decode_inner(inp.as_ref())
}

#[derive(Debug)]
pub enum DecodeError {
    InvalidCharacter
}

fn decode_inner(inp: &str) -> Result<Vec<u8>, DecodeError> {
    // 4 base 64 characters = 3 bytes
    let chunks = inp.as_bytes().chunks(4);
    let mut ret = Vec::new();
    for chunk in chunks {
        let mut block = 0_i32;
        let mut padding = 0;
        for (idx, chunk) in chunk.iter().enumerate() {
            let bits = BASE64_DECODE_TABLE[*chunk as usize]
                .ok_or(DecodeError::InvalidCharacter)?;
            if bits == 0xFF {
                padding += 1;
            }
            block |= (bits as i32) << dbg!(18 - (idx * 6));
        }
        // each u8 in chunk is 6 bits
        // multiply by 6, add 5 to round up to determine the number of u8s we expect
        let num_u8s = (chunk.len() * 6 + 5) / 8;
        for i in (padding..num_u8s).rev() {
            let byte = ((block >> (i * 8)) & 0xFF) as u8;
            ret.push(byte)
        }

    }
    Ok(ret)
}

#[cfg(test)]
mod test {
    use crate::base64::{encode, decode, BASE64_DECODE_TABLE, BASE64_ENCODE_TABLE};
    use proptest::prelude::*;

    proptest! {
        #[test]
        fn doesnt_crash_encode(v in any::<Vec<u8>>()) {
            encode(v);
        }

        #[test]
        fn doesnt_crash_decode(v in any::<String>()) {
            let _ = decode(v);
        }

        #[test]
        fn round_trip(v in any::<Vec<u8>>()) {
            let as_b64 = encode(v.as_slice());
            let decoded = decode(as_b64).unwrap();
            assert_eq!(v, decoded);
        }

        #[test]
        fn vs_oracle(v in any::<Vec<u8>>()) {
            let correct = ::base64::encode(v.as_slice());
            let ours = encode(v.as_slice());
            assert_eq!(ours, correct);
        }
    }

    #[test]
    fn test_base64() {
        assert_eq!(encode("abc"), "YWJj");
        assert_eq!(decode("YWJj").unwrap(), b"abc");
        assert_eq!(decode("YQ==").unwrap(), b"a");
        assert_eq!(encode("anything you want."), "YW55dGhpbmcgeW91IHdhbnQu");
        assert_eq!(encode("anything you want"), "YW55dGhpbmcgeW91IHdhbnQ=");
        assert_eq!(encode("anything you wan"), "YW55dGhpbmcgeW91IHdhbg==");
    }

    #[test]
    fn test_base64_long() {
        let decoded = "Alas, eleventy-one years is far too short a time to live among such excellent and admirable hobbits. I don't know half of you half as well as I should like, and I like less than half of you half as well as you deserve.";
        let encoded = "QWxhcywgZWxldmVudHktb25lIHllYXJzIGlzIGZhciB0b28gc2hvcnQgYSB0aW1lIHRvIGxpdmUgYW1vbmcgc3VjaCBleGNlbGxlbnQgYW5kIGFkbWlyYWJsZSBob2JiaXRzLiBJIGRvbid0IGtub3cgaGFsZiBvZiB5b3UgaGFsZiBhcyB3ZWxsIGFzIEkgc2hvdWxkIGxpa2UsIGFuZCBJIGxpa2UgbGVzcyB0aGFuIGhhbGYgb2YgeW91IGhhbGYgYXMgd2VsbCBhcyB5b3UgZGVzZXJ2ZS4=";
        assert_eq!(encode(decoded), encoded);
        assert_eq!(decode(encoded).unwrap(), decoded.as_bytes());
    }

    #[test]
    fn test_base64_utf8() {
        let decoded = "ユニコードとはか？";
        let encoded = "44Om44OL44Kz44O844OJ44Go44Gv44GL77yf";
        assert_eq!(encode(decoded), encoded);
        assert_eq!(decode(encoded).unwrap(), decoded.as_bytes());
    }
    #[test]
    fn test_base64_control_chars() {
        let decoded = "hello\tworld\n";
        let encoded = "aGVsbG8Jd29ybGQK";
        assert_eq!(encode(decoded), encoded);
    }

    #[test]
    fn test_decode_table() {
        assert_eq!(BASE64_DECODE_TABLE[0], None);
        assert_eq!(BASE64_DECODE_TABLE['A' as usize], Some(0));
        assert_eq!(BASE64_DECODE_TABLE['B' as usize], Some(1));
        for i in 0..64 {
            let encoded = BASE64_ENCODE_TABLE[i];
            let decoded = BASE64_DECODE_TABLE[encoded as usize];
            assert_eq!(decoded, Some(i as u8))
        }
    }
}

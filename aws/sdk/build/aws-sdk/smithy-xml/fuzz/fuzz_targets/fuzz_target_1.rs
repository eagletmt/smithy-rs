#![no_main]
use libfuzzer_sys::fuzz_target;
use smithy_xml::decode;
use std::convert::TryFrom;
use std::error::Error;

fuzz_target!(|data: &[u8]| {
    fn inner(data: &[u8]) -> Result<(), Box<dyn Error>> {
        let mut doc = decode::Document::try_from(data)?;
        let mut root = doc.root_element()?;
        while let Some(tag) = root.next_tag() {}
        Ok(())
    }
    inner(data);
});

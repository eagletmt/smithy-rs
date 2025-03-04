/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

use serde::de::{Error, Unexpected};
use serde::{Deserialize, Deserializer, Serialize, Serializer};
use smithy_types::{base64, Blob};

pub struct BlobSer<'a>(pub &'a Blob);

impl Serialize for BlobSer<'_> {
    fn serialize<S>(&self, serializer: S) -> Result<<S as Serializer>::Ok, <S as Serializer>::Error>
    where
        S: Serializer,
    {
        serializer.serialize_str(base64::encode(self.0.as_ref()).as_str())
    }
}

pub struct BlobDeser(pub Blob);

impl<'de> Deserialize<'de> for BlobDeser {
    fn deserialize<D>(deserializer: D) -> Result<Self, <D as Deserializer<'de>>::Error>
    where
        D: Deserializer<'de>,
    {
        let data = <&str>::deserialize(deserializer)?;
        let bytes = base64::decode(data)
            .map_err(|_| D::Error::invalid_value(Unexpected::Str(data), &"valid base64"))?;
        Ok(BlobDeser(Blob::new(bytes)))
    }
}

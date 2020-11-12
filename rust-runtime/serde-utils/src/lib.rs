use serde::Serializer;
use smithy_http::base64;
use smithy_types::{Blob, Instant};

pub fn instant_ser_epoch_seconds<S>(
    inp: &Instant,
    serializer: S,
) -> Result<<S as Serializer>::Ok, <S as Serializer>::Error>
where
    S: Serializer,
{
    serializer.serialize_i64(inp.epoch_seconds())
}

pub fn optioninstant_ser_epoch_seconds<S>(
    _inp: &Option<Instant>,
    _serializer: S,
) -> Result<<S as Serializer>::Ok, <S as Serializer>::Error>
where
    S: Serializer,
{
    todo!()
    //serializer.collect_seq(inp.iter().map(|i|SerializableInstant(i, Format::HttpDate)))
}

pub fn blob_ser<S>(
    _inp: &Blob,
    _serializer: S,
) -> Result<<S as Serializer>::Ok, <S as Serializer>::Error>
where
    S: Serializer,
{
    _serializer.serialize_str(&base64::encode(_inp.bytes()))
}

pub fn optionblob_ser<S>(
    _inp: &Option<Blob>,
    _serializer: S,
) -> Result<<S as Serializer>::Ok, <S as Serializer>::Error>
where
    S: Serializer,
{
    todo!()
}

pub fn optionvecblob_ser<S>(
    _inp: &Option<Vec<Blob>>,
    _serializer: S,
) -> Result<<S as Serializer>::Ok, <S as Serializer>::Error>
where
    S: Serializer,
{
    todo!()
}

/*
#[cfg(test)]
mod tests {
    use crate::{Color, ColorSer};
    use smithy_types::Instant;

    #[test]
    fn serialize_as_http_dates() {
        let color = Color {
            r: 1,
            g: 1,
            b: vec![
                Instant::from_epoch_seconds(123213),
                Instant::from_epoch_seconds(1931293),
            ],
        };
        assert_eq!(
            serde_json::to_string_pretty(&ColorSer(color)).unwrap(),
            r#"{
  "r": 1,
  "g": 1,
  "b": [
    "Fri, 02 Jan 1970 10:13:33 GMT",
    "Fri, 23 Jan 1970 08:28:13 GMT"
  ]
}"#
        );
    }
}

use serde::{Serialize, Serializer};
use serde::ser::{SerializeSeq, SerializeStruct};
use smithy_types::instant::Format;
use smithy_types::Instant;
use serde::{Deserialize, Deserializer};

#[derive(Serialize, Deserialize)]
pub struct Color {
    r: u8,
    g: u8,
    #[serde(serialize_with = "ser_helpers::ser_http_vec")]
    #[serde(deserialize_with = "ser_helpers::deser_http_vec")]
    b: Vec<Instant>,
}


// ColorSer can stay private, so we don't leak `Serialize`
struct ColorSer(Color);

mod ser_helpers {
    use smithy_types::Instant;
    use serde::{Serializer, Deserializer, Deserialize};
    use crate::SerializableInstant;
    use smithy_types::instant::Format;
    use serde::de::Visitor;
    use serde::export::Formatter;

    pub fn ser_http_vec<S>(inp: &Vec<Instant>, serializer: S) -> Result<<S as Serializer>::Ok, <S as Serializer>::Error>
        where
            S: Serializer {
        serializer.collect_seq(inp.iter().map(|i|SerializableInstant(i, Format::HttpDate)))
    }

    pub fn deser_http_vec<'de, D>(d: D) -> Result<Vec<Instant>, D::Error> where D: Deserializer<'de> {
        let deser = Vec::<SerializableInstant>::deserialize(d)?;
        todo!()
    }
}

// This gets vendored into each crate that needs it (keeping it private)
struct SerializableInstant<'a, F>(&'a Instant, Format);

impl Serialize for SerializableInstant<'_> {
    fn serialize<S>(&self, serializer: S) -> Result<<S as Serializer>::Ok, <S as Serializer>::Error>
    where
        S: Serializer,
    {
        serializer.serialize_str(&self.0.fmt(self.1))
    }
}

impl<'de> Deserialize<'de> for SerializableInstant<'_> {
    fn deserialize<D>(deserializer: D) -> Result<Self, <D as Deserializer<'de>>::Error> where
        D: Deserializer<'de> {

        let s = String::deserialize(deserializer)?;
        todo!()
    }
}

impl Serialize for ColorSer {
    fn serialize<S>(&self, serializer: S) -> Result<S::Ok, S::Error>
    where
        S: Serializer,
    {
        let mut state = serializer.serialize_struct("Color", 3)?;
        struct VecSer<'a>(&'a Vec<Instant>);
        impl<'a> Serialize for VecSer<'a> {
            fn serialize<S>(
                &self,
                serializer: S,
            ) -> Result<<S as Serializer>::Ok, <S as Serializer>::Error>
            where
                S: Serializer,
            {
                let mut ser = serializer.serialize_seq(Some(self.0.len()))?;
                for e in self.0 {
                    ser.serialize_element(&SerializableInstant(e, Format::HttpDate))?
                }
                ser.end()
            }
        }
        state.serialize_field("r", &self.0.r)?;
        state.serialize_field("g", &self.0.g)?;
        state.serialize_field("b", &VecSer(&self.0.b))?;
        state.end()
    }
}

*/

#[derive(Debug, PartialEq, Clone)]
pub struct Instant {
    seconds: u64,
    nanos: u64
}

#[derive(Debug, PartialEq, Clone)]
pub struct Blob {
    inner: Vec<u8>
}
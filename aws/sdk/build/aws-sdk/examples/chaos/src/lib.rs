use aws_hyper::conn::{HttpService, Standard};
use smithy_http::body::SdkBody;
use std::error::Error;
use std::future::Future;
use std::pin::Pin;
use std::task::{Context, Poll};

#[derive(Clone)]
pub struct ChaosConnection {
    inner: aws_hyper::conn::Standard,
    happy: bool,
}

impl ChaosConnection {
    pub fn new(inner: Standard) -> Self {
        Self { inner, happy: true }
    }

    pub fn set_happy(&mut self, happy: bool) {
        self.happy = happy;
    }

    fn dispatch_happy(
        &mut self,
        req: http::Request<SdkBody>,
    ) -> Pin<Box<dyn Future<Output = Result<http::Response<SdkBody>, BoxError>> + Send>> {
        self.inner.call(req)
    }

    fn http500(&self) -> Result<http::Response<SdkBody>, BoxError> {
        Ok(http::Response::builder()
            .status(500)
            .body("I am a sad body")
            .unwrap()
            .map(SdkBody::from))
    }
}

type BoxError = Box<dyn Error + Send + Sync + 'static>;

impl HttpService for ChaosConnection {
    fn poll_ready(&mut self, cx: &mut Context<'_>) -> Poll<Result<(), BoxError>> {
        self.inner.poll_ready(cx)
    }

    fn call(
        &mut self,
        req: http::Request<SdkBody>,
    ) -> Pin<Box<dyn Future<Output = Result<http::Response<SdkBody>, BoxError>> + Send>> {
        let happy_fut = self.dispatch_happy(req);
        let sad_future = self.http500();
        let happy = self.happy;
        let fut = async move {
            if happy {
                happy_fut.await
            } else {
                sad_future
            }
        };
        Box::pin(fut)
    }

    fn clone_box(&self) -> Box<dyn HttpService> {
        Box::new(self.clone())
    }
}

#[cfg(test)]
mod tests {
    use crate::ChaosConnection;
    use aws_hyper::conn::Standard;
    use dynamodb::{Credentials, Region};

    #[tokio::test]
    async fn connection_errors() {
        let https = aws_hyper::conn::Standard::https();
        let mut conn = ChaosConnection::new(https);
        conn.set_happy(false);
        let conn = Standard::new(conn);
        let client = dynamodb::Client::from_conf_conn(
            dynamodb::Config::builder()
                .region(Region::new("us-east-1"))
                .credentials_provider(Credentials::from_keys("akid", "secret", None))
                .build(),
            conn,
        );
        client
            .list_tables()
            .send()
            .await
            .expect("should crash, connection errored");
    }
}

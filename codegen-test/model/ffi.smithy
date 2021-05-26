namespace com.amazonaws.ffi

use aws.protocols#awsJson1_0

@awsJson1_0
service FFIService {
    operations: [aws_crt_init],
    version: "1"
}

// /* Traits */
@trait
structure pointer{
    description: String
}

@trait
structure opaque{}

// /* Structures */
// /* Primitive Wrappers */

structure IntegerWrapper {
    @required
    data: Integer
}

structure StringWrapper {
    @required
    data: String
}

structure BigIntegerWrapper {
    @required
    data: BigInteger
}

@pointer(description: "void*")
@opaque
structure VoidPointer {}

@pointer(description: "uint8_t*")
@opaque
structure UInt8_TPointer {}

// Self-defined Structs (with pointers as reference)

// /* Auth */
// typedef struct aws_credentials aws_crt_credentials;
@pointer(description: "aws_crt_credentials*")
@opaque
structure aws_crt_credentials {}

// typedef struct _aws_crt_credentials_options aws_crt_credentials_options;
@pointer(description: "aws_crt_credentials_options*")
@opaque
structure aws_crt_credentials_options {}

// /* Credentials providers */
// typedef struct aws_credentials_provider aws_crt_credentials_provider;
@pointer(description: "aws_crt_credentials_provider*")
@opaque
structure aws_crt_credentials_provider {}

// /* Public function definitions */

// /* CRT */
// AWS_CRT_API void aws_crt_init(void);
operation aws_crt_init {
    // errors: []
}

// AWS_CRT_API void aws_crt_clean_up(void);
operation aws_crt_clean_up {
    // errors: []
}

// AWS_CRT_API int aws_crt_test_error(int);
operation aws_crt_test_error {
    input: IntegerWrapper,
    output: IntegerWrapper
    // errors: []
}

// AWS_CRT_API void *aws_crt_mem_acquire(size_t size);
operation aws_crt_mem_acquire {
    input: IntegerWrapper,
    output: VoidPointer
    // errors: []
}

// AWS_CRT_API void aws_crt_mem_release(void *mem);
operation aws_crt_mem_release {
    input: VoidPointer
    // errors: []
}

// /* Errors */
// AWS_CRT_API int aws_crt_last_error(void);
operation aws_crt_last_error {
    output: IntegerWrapper
    // errors: []
}

// AWS_CRT_API const char *aws_crt_error_str(int);
operation aws_crt_error_str {
    input: IntegerWrapper,
    output: StringWrapper
    // errors: []
}
// AWS_CRT_API const char *aws_crt_error_name(int);
operation aws_crt_error_name {
    input: IntegerWrapper,
    output: StringWrapper
    // errors: []
}

// AWS_CRT_API const char *aws_crt_error_debug_str(int);
operation aws_crt_error_debug_str {
    input: IntegerWrapper,
    output: StringWrapper
    // errors: []
}
// AWS_CRT_API void aws_crt_reset_error(void);
operation aws_crt_reset_error {
    // errors: []
}

// /* Auth */
// AWS_CRT_API aws_crt_credentials_options *aws_crt_credentials_options_new(void);
operation aws_crt_credentials_options_new {
    output: aws_crt_credentials_options
    // errors: []
}

// AWS_CRT_API void aws_crt_credentials_options_release(aws_crt_credentials_options *options);
operation aws_crt_credentials_options_release {
    input: aws_crt_credentials_options
    // errors: []
}

// Input for setting aws_crt_credentials_options
structure aws_crt_credentials_options_set_input {
    @required
    options: aws_crt_credentials_options,
    @required
    data: UInt8_TPointer,
    @required
    size: IntegerWrapper
}

// AWS_CRT_API void aws_crt_credentials_options_set_access_key_id(
//     aws_crt_credentials_options *options,
//     const uint8_t *access_key_id,
//     size_t access_key_id_length);

operation aws_crt_credentials_options_set_access_key_id {
    input: aws_crt_credentials_options_set_input
    // errors: []
}

// AWS_CRT_API void aws_crt_credentials_options_set_secret_access_key(
//     aws_crt_credentials_options *options,
//     const uint8_t *secret_access_key,
//     size_t secret_access_key_length);
operation aws_crt_credentials_options_set_secret_access_key {
    input: aws_crt_credentials_options_set_input
    // errors: []
}

// AWS_CRT_API void aws_crt_credentials_options_set_session_token(
//     aws_crt_credentials_options *options,
//     const uint8_t *session_token,
//     size_t session_token_length);
operation aws_crt_credentials_options_set_session_token {
    input: aws_crt_credentials_options_set_input
    // errors: []
}

// AWS_CRT_API void aws_crt_credentials_options_set_expiration_timepoint_seconds(
//     aws_crt_credentials_options *options,
//     uint64_t expiration_timepoint_seconds);
structure aws_crt_credentials_options_set_time_input {
    @required
    options: aws_crt_credentials_options,
    @required
    expiration_timepoint_seconds: BigIntegerWrapper
}

operation aws_crt_credentials_options_set_expiration_timepoint_seconds {
    input: aws_crt_credentials_options_set_time_input
    // errors: []
}

// AWS_CRT_API aws_crt_credentials *aws_crt_credentials_new(const aws_crt_credentials_options *options);
operation aws_crt_credentials_new {
    input: aws_crt_credentials_options,
    output: aws_crt_credentials
    // errors: []
}

// AWS_CRT_API aws_crt_credentials *aws_crt_credentials_acquire(aws_crt_credentials *credentials);
operation aws_crt_credentials_acquire {
    input: aws_crt_credentials,
    output: aws_crt_credentials
    // errors: []
}

// AWS_CRT_API void aws_crt_credentials_release(aws_crt_credentials *credentials);
operation aws_crt_credentials_release {
    input: aws_crt_credentials,
    // errors: []
}

// /* Credentials providers */
// /* Generic credentials provider acquire/release */

// AWS_CRT_API aws_crt_credentials_provider *aws_crt_credentials_provider_acquire(
//     aws_crt_credentials_provider *credentials_provider);
operation aws_crt_credentials_provider_acquire {
    input: aws_crt_credentials_provider,
    output: aws_crt_credentials_provider
    // errors: []
}

// AWS_CRT_API void aws_crt_credentials_provider_release(aws_crt_credentials_provider *credentials_provider);
operation aws_crt_credentials_provider_release {
    input: aws_crt_credentials_provider,
    // errors: []
}

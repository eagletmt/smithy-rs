$version: "1.0"

namespace com.amazon.alexasignalcomputationcontrolservice.source

@documentation("Configuration for signal computation source")
union SourceConfiguration {
    sourceConfiguration: Source,
    sourceConfigurationList: SourceList,
}

list SourceList {
    member: Source,
}

@documentation("A source for input data")
union Source {
    kinesisSource: KinesisSource,
}

@documentation("""
Kinesis source specified with stream name and RecordEncryption type. The stream name format
will be <signal identifier>-<stream name> where the signal identifier is namespace concatenated
with name, and the stream name is specified by the Kinesis source streamName key.
""")
structure KinesisSource {
    @required
    streamName: String,
    @required
    encryptionType: RecordEncryptionType,
}

@enum([
    {
        value: "kms-sse",
        name: "KMS_SSE",
    },
])
string RecordEncryptionType


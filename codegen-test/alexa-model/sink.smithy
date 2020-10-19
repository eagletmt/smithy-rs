$version: "1.0"

namespace com.amazon.alexasignalcomputationcontrolservice.sink

@documentation("Configuration for signal computation sink")
union SinkConfiguration {
    sinkConfiguration: Sink,
    sinkConfigurationList: SinkList,
}

list SinkList {
    member: Sink,
}

@documentation("A location where output data should be sent")
union Sink {
    cloudWatchMetricsSink: CloudWatchMetricsSink,
    kinesisSink: KinesisSink,
    snsSink: SNSSink,
}

// CloudWatch spec
@documentation("CloudWatch metrics sink configuration")
structure CloudWatchMetricsSink {
    @required
    metricConfiguration: MetricConfiguration,
    crossAccountRole: String,
}

@documentation("Metric configuration for CloudWatch metrics sink")
structure MetricConfiguration {
    @required
    namespace: String,
    @required
    valueConfiguration: ValueConfigurationList,
    @required
    dimensions: DimensionConfigurationList,
    @required
    unit: StandardUnit,
}

@documentation("StandardUnit for CloudWatch metrics sink")
@enum([
    {
        value: "Count",
        name: "COUNT",
    },
    {
        value: "None",
        name: "NONE",
    }
])
string StandardUnit

@documentation("Sink configuration type")
@enum([
    {
        value: "Kinesis",
        name: "KINESIS",
    },
    {
        value: "CloudWatch",
        name: "CLOUDWATCH",
    }
])
string SinkConfigurationType

@documentation("Dimension configuration for CloudWatch metrics sink")
structure DimensionConfiguration {
    name: String,
    rowColumn: Integer,
}

@documentation("Value configuration for CloudWatch metrics sink")
structure ValueConfiguration {
    name: String,
    rowColumn: Integer,
}

list ValueConfigurationList {
    member: ValueConfiguration,
}

list DimensionConfigurationList {
    member: DimensionConfiguration,
}

// Kinesis spec
@documentation("""
Kinesis sink configuration. The stream name format will be <signal identifier>-<stream name>
where the signal identifier is namespace concatenated with name, and the stream name is
specified by the Kinesis sink streamName key.
""")
structure KinesisSink {
    @required
    streamName: String,
    crossAccountRole: String,
    kmsAlias: String,
}

// SNS spec
@documentation("SNS sink configuration")
structure SNSSink {
    @required
    arn: String,
    messageAttributeValue: MessageAttributeValue,
    crossAccountRole: String,
    kmsAlias: String,
}

@documentation("MessageAttributeValue for SNS sink configuration. Skipping StringValue for now - will default to UTF_8")
structure MessageAttributeValue {
    dataType: DataType,
}

@documentation("DataType for MessageAttributeValue")
@enum([
    {
        value: "String",
        name: "STRING",
    },
    {
        value: "String.Array",
        name: "STRING_ARRAY",
    },
    {
        value: "Number",
        name: "NUMBER",
    },
    {
        value: "Binary",
        name: "BINARY",
    },
])
string DataType


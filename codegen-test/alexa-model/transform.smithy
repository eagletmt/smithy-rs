$version: "1.0"

namespace com.amazon.alexasignalcomputationcontrolservice.transform

@documentation("Configuration for signal computation transformation")
structure TransformConfiguration {
    transformConfiguration: Transform,
}

@documentation("A transform for signal computation")
union Transform {
    sqlTransform: SqlTransform,
}

@documentation("SQL transform specification.")
structure SqlTransform {
    @documentation("A SQL string")
    @required
    sqlTransformString: String,
    @required
    parallelism: Integer,
}
$version: "1.0"

namespace com.amazon.alexasignalcomputationcontrolservice

use aws.api#service
use aws.auth#sigv4
use aws.protocols#restJson1
use com.amazon.alexasignalcomputationcontrolservice.source#SourceConfiguration
use com.amazon.alexasignalcomputationcontrolservice.filter#FilterConfiguration
use com.amazon.alexasignalcomputationcontrolservice.transform#TransformConfiguration
use com.amazon.alexasignalcomputationcontrolservice.sink#SinkConfiguration
use com.amazon.alexasignalcomputationcontrolservice.exception#DependencyException
use com.amazon.alexasignalcomputationcontrolservice.exception#InvalidInputException

@title("Alexa Signal Computation Service")

// Custom SDK service ID trait.
@aws.api#service(
    sdkId: "AlexaSignalComputationControlServiceLambda",
    arnNamespace: "execute-api",
)

// Enable cross-origin resource sharing
@cors()


@restJson1
@sigv4(name: "alexasignalcomputationservice")
service AlexaSignalComputationService {
    version: "2018-05-10",
    resources: [Signals],
}

@pattern("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
string Uuid

@length(min: 1, max: 40)
@pattern("[a-zA-Z][-a-zA-Z0-9]*")
string SignalName

@length(min: 1, max: 40)
@pattern("[a-zA-Z][-a-zA-Z0-9]*")
string SignalNamespace

@documentation("""
A signal computation on a stream.

A signal computation—or \"signal\"—is the result of some transformation on a stream of data.
A set of input sources form an input stream, to which the computation is applied, and the
results are written to the output sinks.

In the simplest case, the identity computation, the input stream is unmodified and directed
to a sink. More complex computation may perform time-based windowing and aggregation-based
operations.

The signal identifier will be constructed by concatenating namespace with name.
""")
structure SignalConfiguration {
    @required
    name: SignalName,

    @required
    namespace: SignalNamespace,

    @required
    source: SourceConfiguration,
    filter: FilterConfiguration,
    transform: TransformConfiguration,
    @required
    sink: SinkConfiguration,
}

@documentation("Management of the signals resource")
resource Signals {
    identifiers: {
        namespace: SignalNamespace,
        name: SignalName,
    },
    create: CreateSignal,
    read: DescribeSignal,
    update: StartSignal,
    delete: DeleteSignal,
}

structure CreateSignalRequest {
    @required
    signal: SignalConfiguration,
}

structure CreateSignalResponse {
    @required
    @pattern("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
    id: Uuid,
    version: String,
    signal: SignalConfiguration,
}

@documentation("Create a new signal")
@http(code: 200, method: "POST", uri: "/signals",)
@idempotent
operation CreateSignal {
    input: CreateSignalRequest,
    output: CreateSignalResponse,
    errors: [
        DependencyException,
        InvalidInputException,
    ]
}

structure DescribeSignalRequest {
    @required
    @httpLabel
    name: SignalName,

    @required
    @httpLabel
    namespace: SignalNamespace,

    // TODO uncomment once signals database is implemented
    //@required
    //@length(min: 1, max: 40)
    //@pattern("[a-zA-Z][-a-zA-Z0-9]*")
    //@httpLabel
    //version: String,
}

@documentation("Status of signal.")
@enum([
    {
        value: "Signal is ready",
        name: "READY",
    },
    {
        value: "Signal is running",
        name: "RUNNING",
    },
    {
        value: "Signal is starting",
        name: "STARTING",
    },
    {
        value: "Signal is stopping",
        name: "STOPPING",
    },
    {
        value: "Signal creation is in progress",
        name: "CREATE_IN_PROGRESS",
    },
    {
        value: "Signal update is in progress",
        name: "UPDATE_IN_PROGRESS",
    },
    {
        value: "Signal deletion is in progress. Either something went wrong or this was requested by a user.",
        name: "DELETE_IN_PROGRESS",
    },
    {
        value: "Signal has been deleted.",
        name: "DELETED",
    },
    {
        value: "Signal does not exist.",
        name: "DOES_NOT_EXIST",
    },
])
string SignalStatus

structure DescribeSignalResponse {
    signalStatus: SignalStatus,
}

@documentation("Get signal status.")
// TODO add "version" to URI once signals database is implemented
@http(code: 200, method: "GET", uri: "/signals/{namespace}/{name}",)
@readonly
operation DescribeSignal {
    input: DescribeSignalRequest,
    output: DescribeSignalResponse,
    errors: [
        DependencyException,
        InvalidInputException,
    ]
}

structure StartSignalRequest {
    @required
    @httpLabel
    name: SignalName,

    @required
    @httpLabel
    namespace: SignalNamespace,

    restoreFromLatestSnapshot: Boolean,

    // TODO uncomment once signals database is implemented
    //@required
    //@length(min: 1, max: 40)
    //@pattern("[a-zA-Z][-a-zA-Z0-9]*")
    //@httpLabel
    //version: String,
}

structure StartSignalResponse {
    signalStatus: SignalStatus,
}

@documentation("Start computing an existing signal")
@http(code: 200, method: "PUT", uri: "/signals/{namespace}/{name}/start",)
@idempotent
operation StartSignal {
    input: StartSignalRequest,
    output: StartSignalResponse,
    errors: [
        DependencyException,
        InvalidInputException,
    ]
}

structure DeleteSignalRequest {
    @required
    @httpLabel
    name: SignalName,

    @required
    @httpLabel
    namespace: SignalNamespace,
}

structure DeleteSignalResponse {
    signalStatus: SignalStatus,
}

@documentation("Delete an existing signal")
@http(code: 200, method: "DELETE", uri: "/signals/{namespace}/{name}",)
@idempotent
operation DeleteSignal {
    input: DeleteSignalRequest,
    output: DeleteSignalResponse,
    errors: [
        DependencyException,
        InvalidInputException,
    ]
}

$version: "1.0"

namespace com.amazon.alexasignalcomputationcontrolservice.exception

@httpError(500)
@error("server")
structure DependencyException {
    message: String
}

@httpError(400)
@error("client")
structure InvalidInputException {
    message: String
}

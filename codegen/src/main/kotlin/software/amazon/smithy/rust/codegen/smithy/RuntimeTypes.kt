/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.rust.codegen.smithy

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.model.node.ObjectNode
import software.amazon.smithy.model.traits.TimestampFormatTrait
import software.amazon.smithy.rust.codegen.rustlang.CargoDependency
import software.amazon.smithy.rust.codegen.rustlang.CratesIo
import software.amazon.smithy.rust.codegen.rustlang.DependencyLocation
import software.amazon.smithy.rust.codegen.rustlang.InlineDependency
import software.amazon.smithy.rust.codegen.rustlang.Local
import software.amazon.smithy.rust.codegen.rustlang.RustDependency
import software.amazon.smithy.rust.codegen.rustlang.RustType
import software.amazon.smithy.rust.codegen.rustlang.RustWriter
import software.amazon.smithy.rust.codegen.rustlang.asType
import java.util.Optional

sealed class RuntimeCrateLocation {
    data class Path(val path: String) : RuntimeCrateLocation()
    data class Versioned(val version: String) : RuntimeCrateLocation()
}

fun RuntimeCrateLocation.crateLocation(): DependencyLocation = when (this) {
    is RuntimeCrateLocation.Path -> Local(this.path)
    is RuntimeCrateLocation.Versioned -> CratesIo(this.version)
}

data class RuntimeConfig(
    val cratePrefix: String = "smithy",
    val runtimeCrateLocation: RuntimeCrateLocation = RuntimeCrateLocation.Path("../")
) {
    companion object {

        fun fromNode(node: Optional<ObjectNode>): RuntimeConfig {
            return if (node.isPresent) {
                val runtimeCrateLocation = if (node.get().containsMember("version")) {
                    RuntimeCrateLocation.Versioned(node.get().expectStringMember("version").value)
                } else {
                    RuntimeCrateLocation.Path(node.get().getStringMemberOrDefault("relativePath", "../"))
                }
                RuntimeConfig(
                    node.get().getStringMemberOrDefault("cratePrefix", "smithy"),
                    runtimeCrateLocation = runtimeCrateLocation
                )
            } else {
                RuntimeConfig()
            }
        }
    }

    fun runtimeCrate(runtimeCrateName: String, optional: Boolean = false): CargoDependency =
        CargoDependency("$cratePrefix-$runtimeCrateName", runtimeCrateLocation.crateLocation(), optional = optional)
}

data class RuntimeType(val name: String?, val dependency: RustDependency?, val namespace: String) {
    fun toSymbol(): Symbol {
        val builder = Symbol.builder().name(name).namespace(namespace, "::")
            .rustType(RustType.Opaque(name ?: "", namespace = namespace))

        dependency?.run { builder.addDependency(this) }
        return builder.build()
    }

    fun member(member: String): RuntimeType {
        val newName = name?.let { "$name::$member" } ?: member
        return copy(name = newName)
    }

    fun fullyQualifiedName(): String {
        val postFix = name?.let { "::$name" } ?: ""
        return "$namespace$postFix"
    }

    // TODO: refactor to be RuntimeTypeProvider a la Symbol provider that packages the `RuntimeConfig` state.
    companion object {
        fun errorKind(runtimeConfig: RuntimeConfig) = RuntimeType(
            "ErrorKind",
            dependency = CargoDependency.SmithyTypes(runtimeConfig),
            namespace = "${runtimeConfig.cratePrefix}_types::retry"
        )

        fun provideErrorKind(runtimeConfig: RuntimeConfig) = RuntimeType(
            "ProvideErrorKind",
            dependency = CargoDependency.SmithyTypes(runtimeConfig),
            namespace = "${runtimeConfig.cratePrefix}_types::retry"
        )

        val std = RuntimeType(null, dependency = null, namespace = "std")
        val stdfmt = std.member("fmt")

        val AsRef = RuntimeType("AsRef", dependency = null, namespace = "std::convert")
        val ByteSlab = RuntimeType("Vec<u8>", dependency = null, namespace = "std::vec")
        val Clone = std.member("clone::Clone")
        val Debug = stdfmt.member("Debug")
        val Default: RuntimeType = RuntimeType("Default", dependency = null, namespace = "std::default")
        val From = RuntimeType("From", dependency = null, namespace = "std::convert")
        val PartialEq = std.member("cmp::PartialEq")
        val StdError = RuntimeType("Error", dependency = null, namespace = "std::error")
        val String = RuntimeType("String", dependency = null, namespace = "std::string")

        fun Instant(runtimeConfig: RuntimeConfig) =
            RuntimeType("Instant", CargoDependency.SmithyTypes(runtimeConfig), "${runtimeConfig.cratePrefix}_types")

        fun GenericError(runtimeConfig: RuntimeConfig) =
            RuntimeType("Error", CargoDependency.SmithyTypes(runtimeConfig), "${runtimeConfig.cratePrefix}_types")

        fun Blob(runtimeConfig: RuntimeConfig) =
            RuntimeType("Blob", CargoDependency.SmithyTypes(runtimeConfig), "${runtimeConfig.cratePrefix}_types")

        fun Document(runtimeConfig: RuntimeConfig): RuntimeType =
            RuntimeType("Document", CargoDependency.SmithyTypes(runtimeConfig), "${runtimeConfig.cratePrefix}_types")

        fun LabelFormat(runtimeConfig: RuntimeConfig, func: String) =
            RuntimeType(func, CargoDependency.SmithyHttp(runtimeConfig), "${runtimeConfig.cratePrefix}_http::label")

        fun QueryFormat(runtimeConfig: RuntimeConfig, func: String) =
            RuntimeType(func, CargoDependency.SmithyHttp(runtimeConfig), "${runtimeConfig.cratePrefix}_http::query")

        fun Base64Encode(runtimeConfig: RuntimeConfig): RuntimeType =
            RuntimeType(
                "encode",
                CargoDependency.SmithyTypes(runtimeConfig),
                "${runtimeConfig.cratePrefix}_types::base64"
            )

        fun Base64Decode(runtimeConfig: RuntimeConfig): RuntimeType =
            RuntimeType(
                "decode",
                CargoDependency.SmithyTypes(runtimeConfig),
                "${runtimeConfig.cratePrefix}_types::base64"
            )

        fun TimestampFormat(runtimeConfig: RuntimeConfig, format: TimestampFormatTrait.Format): RuntimeType {
            val timestampFormat = when (format) {
                TimestampFormatTrait.Format.EPOCH_SECONDS -> "EpochSeconds"
                TimestampFormatTrait.Format.DATE_TIME -> "DateTime"
                TimestampFormatTrait.Format.HTTP_DATE -> "HttpDate"
                TimestampFormatTrait.Format.UNKNOWN -> TODO()
            }
            return RuntimeType(
                timestampFormat,
                CargoDependency.SmithyTypes(runtimeConfig),
                "${runtimeConfig.cratePrefix}_types::instant::Format"
            )
        }

        fun ProtocolTestHelper(runtimeConfig: RuntimeConfig, func: String): RuntimeType =
            RuntimeType(
                func, CargoDependency.ProtocolTestHelpers(runtimeConfig), "protocol_test_helpers"
            )

        val http = CargoDependency.Http.asType()
        fun Http(path: String): RuntimeType =
            RuntimeType(name = path, dependency = CargoDependency.Http, namespace = "http")

        val HttpRequestBuilder = Http("request::Builder")
        val HttpResponseBuilder = Http("response::Builder")

        fun Serde(path: String) = RuntimeType(
            path, dependency = CargoDependency.Serde, namespace = "serde"
        )

        val Deserialize: RuntimeType = RuntimeType("Deserialize", CargoDependency.Serde, namespace = "serde")
        val Deserializer = RuntimeType("Deserializer", CargoDependency.Serde, namespace = "serde")
        fun SerdeJson(path: String) =
            RuntimeType(path, dependency = CargoDependency.SerdeJson, namespace = "serde_json")

        val serdeJson = RuntimeType(null, dependency = CargoDependency.SerdeJson, namespace = "serde_json")

        fun awsJsonErrors(runtimeConfig: RuntimeConfig) =
            forInlineDependency(InlineDependency.awsJsonErrors(runtimeConfig))

        val DocJson by lazy { forInlineDependency(InlineDependency.docJson()) }

        val InstantEpoch by lazy { forInlineDependency(InlineDependency.instantEpoch()) }
        val InstantHttpDate by lazy { forInlineDependency(InlineDependency.instantHttpDate()) }
        val Instant8601 by lazy { forInlineDependency(InlineDependency.instant8601()) }
        val IdempotencyToken by lazy { forInlineDependency(InlineDependency.idempotencyToken()) }

        val Config = RuntimeType("config", null, "crate")

        fun operation(runtimeConfig: RuntimeConfig) = RuntimeType(
            "Operation",
            dependency = CargoDependency.SmithyHttp(runtimeConfig),
            namespace = "smithy_http::operation"
        )

        fun operationModule(runtimeConfig: RuntimeConfig) = RuntimeType(
            null,
            dependency = CargoDependency.SmithyHttp(runtimeConfig),
            namespace = "smithy_http::operation"
        )

        fun sdkBody(runtimeConfig: RuntimeConfig): RuntimeType =
            RuntimeType("SdkBody", dependency = CargoDependency.SmithyHttp(runtimeConfig), "smithy_http::body")

        fun parseStrict(runtimeConfig: RuntimeConfig) = RuntimeType(
            "ParseStrictResponse",
            dependency = CargoDependency.SmithyHttp(runtimeConfig),
            namespace = "smithy_http::response"
        )

        val Bytes = RuntimeType("Bytes", dependency = CargoDependency.Bytes, namespace = "bytes")
        fun BlobSerde(runtimeConfig: RuntimeConfig) = forInlineDependency(InlineDependency.blobSerde(runtimeConfig))

        fun forInlineDependency(inlineDependency: InlineDependency) =
            RuntimeType(inlineDependency.name, inlineDependency, namespace = "crate")

        fun forInlineFun(name: String, module: String, func: (RustWriter) -> Unit) = RuntimeType(
            name = name,
            dependency = InlineDependency(name, module, listOf(), func),
            namespace = "crate::$module"
        )

        fun byteStream(runtimeConfig: RuntimeConfig) =
            CargoDependency.SmithyHttp(runtimeConfig).asType().member("byte_stream::ByteStream")

        fun parseResponse(runtimeConfig: RuntimeConfig) = RuntimeType(
            "ParseHttpResponse",
            dependency = CargoDependency.SmithyHttp(runtimeConfig),
            namespace = "smithy_http::response"
        )

        fun ec2QueryErrors(runtimeConfig: RuntimeConfig) =
            forInlineDependency(InlineDependency.ec2QueryErrors(runtimeConfig))

        fun wrappedXmlErrors(runtimeConfig: RuntimeConfig) =
            forInlineDependency(InlineDependency.wrappedXmlErrors(runtimeConfig))

        fun unwrappedXmlErrors(runtimeConfig: RuntimeConfig) =
            forInlineDependency(InlineDependency.unwrappedXmlErrors(runtimeConfig))
    }
}

/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 *
 *
 */

package software.amazon.smithy.rust.codegen.smithy

import java.io.File
import java.util.Optional
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.model.node.ObjectNode
import software.amazon.smithy.rust.codegen.lang.RustDependency
import software.amazon.smithy.rust.codegen.lang.RustType

data class RuntimeConfig(val cratePrefix: String = "smithy", val relativePath: String = "../") {
    companion object {

        fun fromNode(node: Optional<ObjectNode>): RuntimeConfig {
            return if (node.isPresent) {
                RuntimeConfig(
                    node.get().getStringMemberOrDefault("createPrefix", "smithy"),
                    File(node.get().getStringMemberOrDefault("relativePath", "../")).absolutePath
                )
            } else {
                RuntimeConfig()
            }
        }
    }
}

data class RuntimeType(val name: String, val dependency: RustDependency?, val namespace: String) {
    fun toSymbol(): Symbol {
        val builder = Symbol.builder().name(name).namespace(namespace, "::")
            .rustType(RustType.Opaque(name))

        dependency.run { builder.addDependency(this) }
        return builder.build()
    }

    companion object {
        // val Blob = RuntimeType("Blob", RustDependency.IO_CORE, "blob")
        val From = RuntimeType("From", dependency = null, namespace = "std::convert")
        val AsRef = RuntimeType("AsRef", dependency = null, namespace = "std::convert")
        fun StdFmt(member: String) = RuntimeType("fmt::$member", dependency = null, namespace = "std")
        val StdError = RuntimeType("Error", dependency = null, namespace = "std::error")
        val HashSet = RuntimeType("HashSet", dependency = null, namespace = "std::collections")

        fun Instant(runtimeConfig: RuntimeConfig) =
            RuntimeType("Instant", RustDependency.SmithyTypes(runtimeConfig), "${runtimeConfig.cratePrefix}_types")
        fun Blob(runtimeConfig: RuntimeConfig) =
            RuntimeType("Blob", RustDependency.SmithyTypes(runtimeConfig), "${runtimeConfig.cratePrefix}_types")
    }
}

/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.rust.codegen.lang

import software.amazon.smithy.codegen.core.SymbolDependency
import software.amazon.smithy.codegen.core.SymbolDependencyContainer
import software.amazon.smithy.rust.codegen.smithy.RuntimeConfig
import software.amazon.smithy.rust.codegen.util.dq

sealed class DependencyScope
object Dev : DependencyScope()
object Compile : DependencyScope()

sealed class DependencyLocation
data class CratesIo(val version: String) : DependencyLocation()
data class Local(val basePath: String) : DependencyLocation()

sealed class RustDependency : SymbolDependencyContainer {
    abstract val name: String
    abstract fun version(): String
    override fun getDependencies(): List<SymbolDependency> {
        return listOf(
            SymbolDependency
                .builder()
                .packageName(name).version(version())
                // We rely on retrieving the structured dependency from the symbol later
                .putProperty(PropertyKey, this).build()
        )
    }

    companion object {
        val Http: CargoDependency = CargoDependency("http", CratesIo("0.2"))
        val SerdeJson: CargoDependency = CargoDependency("serde_json", CratesIo("1"))
        val Serde = CargoDependency("serde", CratesIo("1"), features = listOf("derive"))

        fun SmithyTypes(runtimeConfig: RuntimeConfig) =
            CargoDependency("${runtimeConfig.cratePrefix}-types", Local(runtimeConfig.relativePath))

        fun SmithyHttp(runtimeConfig: RuntimeConfig) = CargoDependency(
            "${runtimeConfig.cratePrefix}-http", Local(runtimeConfig.relativePath)
        )

        fun ProtocolTestHelpers(runtimeConfig: RuntimeConfig) = CargoDependency(
            "protocol-test-helpers", Local(runtimeConfig.relativePath), scope = Dev
        )

        const val PropertyKey = "rustdep"

        fun fromSymbolDependency(symbolDependency: SymbolDependency) =
            symbolDependency.getProperty(PropertyKey, RustDependency::class.java).get()
    }
}

class VendoredDependency(override val name: String, val module: String, val renderer: (RustWriter) -> Unit) : RustDependency() {
    override fun version(): String {
        return renderer(RustWriter.forModule("_")).hashCode().toString()
    }
}

data class CargoDependency(
    override val name: String,
    val location: DependencyLocation,
    val scope: DependencyScope = Compile,
    val features: List<String> = listOf()
) : RustDependency() {

    override fun version(): String = when (location) {
        is CratesIo -> location.version
        is Local -> "local"
    }

    override fun toString(): String {
        val attribs = mutableListOf<String>()
        with(location) {
            attribs.add(
                when (this) {
                    is CratesIo -> """version = ${version.dq()}"""
                    is Local -> {
                        val fullPath = "$basePath/$name"
                        """path = ${fullPath.dq()}"""
                    }
                }
            )
        }
        with(features) {
            if (!isEmpty()) {
                attribs.add("features = [${joinToString(",") { it.dq() }}]")
            }
        }
        return "$name = { ${attribs.joinToString(",")} }"
    }
}

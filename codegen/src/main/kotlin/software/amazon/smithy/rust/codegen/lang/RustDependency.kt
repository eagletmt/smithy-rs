package software.amazon.smithy.rust.codegen.lang

import software.amazon.smithy.codegen.core.SymbolDependency
import software.amazon.smithy.codegen.core.SymbolDependencyContainer
import software.amazon.smithy.rust.codegen.smithy.RuntimeConfig
import software.amazon.smithy.rust.codegen.smithy.RustSettings
import java.nio.file.Path

sealed class DependencyLocation
data class CratesIo(val version: String): DependencyLocation()
data class Local(val path: String? = null): DependencyLocation()


data class RustDependency (
    val name: String,
    val location: DependencyLocation
): SymbolDependencyContainer {
    override fun getDependencies(): List<SymbolDependency> {
        return listOf(
            SymbolDependency.builder().packageName(name).version(this.version()).putProperty(PropKey, this).build()
        )
    }

    private fun version(): String = when(location) {
        is CratesIo -> location.version
        is Local -> "local"
    }

    override fun toString(): String {
        return when(location) {
            is CratesIo -> """$name = $location.version"""
            is Local -> """$name = { path = "${location.path}/$name" }"""
        }
    }



    companion object {
        private val PropKey = "rustdep"
        fun SmithyTypes(runtimeConfig: RuntimeConfig) = RustDependency("${runtimeConfig.cratePrefix}-types", Local(runtimeConfig.relativePath))
        fun fromSymbolDependency(symbolDependency: SymbolDependency) = symbolDependency.getProperty(PropKey, RustDependency::class.java).get()
    }
}


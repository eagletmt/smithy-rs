package software.amazon.smithy.rust.testutil

import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.model.Model
import software.amazon.smithy.rust.codegen.smithy.RuntimeConfig
import software.amazon.smithy.rust.codegen.smithy.SymbolVisitor
import software.amazon.smithy.rust.codegen.smithy.SymbolVisitorConfig
import java.io.File

val TestSymbolVistorConfig = SymbolVisitorConfig(runtimeConfig = RuntimeConfig(relativePath = File("../rust-runtime/").absolutePath), handleOptionality = true, handleRustBoxing = true)
fun testSymbolProvider(model: Model): SymbolProvider = SymbolVisitor(model, "test", TestSymbolVistorConfig)

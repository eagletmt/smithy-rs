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

package software.amazon.smithy.rust.testutil

import software.amazon.smithy.rust.codegen.lang.RustDependency
import software.amazon.smithy.rust.codegen.lang.RustWriter
import software.amazon.smithy.rust.codegen.util.runCommand

// TODO: unify these test helpers a bit
fun String.shouldParseAsRust() {
    // quick hack via rustfmt
    val tempFile = createTempFile(suffix = ".rs")
    tempFile.writeText(this)
    "rustfmt ${tempFile.absolutePath}".runCommand()
}

fun RustWriter.shouldCompile(main: String = "") {
    val deps = this.dependencies.map { RustDependency.fromSymbolDependency(it) }
    this.toString().shouldCompile(deps.toSet(), main)
}

fun String.shouldCompile(deps: Set<RustDependency>, main: String = "") {
    val tempDir = createTempDir()
    // TODO: unify this with CargoTomlGenerator
    val cargoToml = """
    [package]
    name = "test-compile"
    version = "0.0.1"
    authors = ["rcoh@amazon.com"]
    edition = "2018"
    
    [dependencies]
    ${deps.joinToString("\n") { it.toString() }}
    """.trimIndent()
    tempDir.resolve("Cargo.toml").writeText(cargoToml)
    tempDir.resolve("src").mkdirs()
    val mainRs = tempDir.resolve("src/main.rs")
    mainRs.writeText(this)
    if (!this.contains("fn main")) {
        mainRs.appendText("\nfn main() { $main }\n")
    }
    "cargo check".runCommand(tempDir.toPath())
    if (main != "") {
        "cargo run".runCommand(tempDir.toPath())
    }
}

fun String.shouldCompile() {
    this.shouldParseAsRust()
    val tempFile = createTempFile(suffix = ".rs")
    val tempDir = createTempDir()
    tempFile.writeText(this)
    if (!this.contains("fn main")) {
        tempFile.appendText("\nfn main() {}\n")
    }
    println(tempFile.absolutePath)
    "rustc ${tempFile.absolutePath} -o ${tempDir.absolutePath}/output".runCommand()
}

/**
 * Inserts the provided strings as a main function and executes the result. This is intended to be used to validate
 * that generated code compiles and has some basic properties.
 *
 * Example usage:
 * ```
 * "struct A { a: u32 }".quickTest("let a = A { a: 5 }; assert_eq!(a.a, 5);")
 * ```
 */
fun String.quickTest(vararg strings: String) {
    val tempFile = createTempFile(suffix = ".rs")
    val tempDir = createTempDir()
    tempFile.writeText(this)
    tempFile.appendText("\nfn main() { \n ${strings.joinToString("\n")} }")
    "rustc ${tempFile.absolutePath} -o ${tempDir.absolutePath}/output".runCommand()
    "${tempDir.absolutePath}/output".runCommand()
}

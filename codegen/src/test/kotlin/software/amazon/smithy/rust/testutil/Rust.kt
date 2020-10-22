/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.rust.testutil

import software.amazon.smithy.rust.codegen.lang.RustDependency
import software.amazon.smithy.rust.codegen.util.runCommand

fun String.shouldParseAsRust() {
    // quick hack via rustfmt
    val tempFile = createTempFile(suffix = ".rs")
    tempFile.writeText(this)
    "rustfmt ${tempFile.absolutePath}".runCommand()
}

// TODO: should probably unify this with CargoTomlGenerator
fun String.shouldCompile(deps: List<RustDependency>, main: String = "") {
    val tempDir = createTempDir()
    val cargoToml = """
    [package]
    name = "test-compile"
    version = "0.0.1"
    authors = ["rcoh@amazon.com"]
    edition = "2018"
    
    [dependencies]
    ${deps.map { it.toString() }.joinToString("\n") }
    
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

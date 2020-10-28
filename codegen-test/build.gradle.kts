/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

extra["displayName"] = "Smithy :: Rust :: Codegen :: Test"
extra["moduleName"] = "software.amazon.smithy.kotlin.codegen.test"

tasks["jar"].enabled = false

plugins {
    id("software.amazon.smithy").version("0.5.2")
}

val smithyVersion: String by project

dependencies {
    implementation(project(":codegen"))
    implementation("software.amazon.smithy:smithy-aws-protocol-tests:1.3.0")
    implementation("software.amazon.smithy:smithy-protocol-test-traits:$smithyVersion")
    implementation("software.amazon.smithy:smithy-aws-traits:$smithyVersion")
}

tasks.register<Exec>("installCargoToml") {
    commandLine("cp", "Cargo.toml.tmpl", "build/smithyprojections/codegen-test/Cargo.toml")
    dependsOn("build")
}

tasks.register<Exec>("cargoCheck") {
    workingDir("build/smithyprojections/codegen-test/")
    // disallow warnings
    environment("RUSTFLAGS", "-D warnings")
    commandLine("cargo", "check")
    dependsOn("installCargoToml")
}

tasks.register<Exec>("cargoClippy") {
    workingDir("build/smithyprojections/codegen-test/")
    // disallow warnings
    environment("RUSTFLAGS", "-D warnings")
    commandLine("cargo", "clippy")
    dependsOn("installCargoToml")
}

tasks.register<Exec>("cargoTest") {
    workingDir("build/smithyprojections/codegen-test/")
    // disallow warnings
    commandLine("cargo", "test")
    dependsOn("installCargoToml")
}



tasks["test"].finalizedBy("cargoCheck", "cargoClippy", "cargoTest")

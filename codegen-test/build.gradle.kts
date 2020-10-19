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
    implementation("software.amazon.smithy:smithy-protocol-test-traits:$smithyVersion")
    implementation("software.amazon.smithy:smithy-aws-traits:$smithyVersion")
}

tasks.register<Exec>("cargoCheck") {
    workingDir("build/smithyprojections/codegen-test/source/rust-codegen/")
    commandLine("cargo", "check")
    dependsOn("build")
}

tasks["test"].finalizedBy("cargoCheck")


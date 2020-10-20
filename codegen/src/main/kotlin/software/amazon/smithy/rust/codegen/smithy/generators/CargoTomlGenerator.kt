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

package software.amazon.smithy.rust.codegen.smithy.generators

import software.amazon.smithy.rust.codegen.lang.RustDependency
import software.amazon.smithy.rust.codegen.smithy.RustSettings
import software.amazon.smithy.utils.CodeWriter

class CargoTomlGenerator(private val settings: RustSettings, private val writer: CodeWriter, private val dependencies: List<RustDependency>) {
    fun render() {
        writer.write("[package]")
        writer.write("""name = "${settings.moduleName}"""")
        writer.write("""version = "${settings.moduleVersion}"""")
        writer.write("""authors = ["TODO@todo.com"]""")
        // TODO: make edition configurable
        writer.write("""edition = "2018"""")

        writer.insertTrailingNewline()

        if (dependencies.isNotEmpty()) {
            writer.write("[dependencies]")
            dependencies.forEach {
                writer.write(it.toString())
            }
        }
    }
}

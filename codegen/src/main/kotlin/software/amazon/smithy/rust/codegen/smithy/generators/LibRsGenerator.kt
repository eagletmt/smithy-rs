/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.rust.codegen.smithy.generators

import software.amazon.smithy.rust.codegen.lang.RustWriter
import software.amazon.smithy.rust.codegen.smithy.letIf

data class Module(val name: String, val public: Boolean) {
    override fun toString(): String {
        val vis = "".letIf(public) { "pub" }
        return "$vis mod $name"
    }
}
class LibRsGenerator(private val modules: List<Module>, private val writer: RustWriter) {
    fun render() {
        modules.forEach { writer.write("$it;") }
    }
}

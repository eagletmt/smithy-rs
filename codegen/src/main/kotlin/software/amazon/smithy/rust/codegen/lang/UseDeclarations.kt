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

package software.amazon.smithy.rust.codegen.lang

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.vended.ImportContainer

class UseDeclarations(private val filename: String, private val namespace: String) : ImportContainer {
    private val imports: MutableSet<UseStatement> = mutableSetOf()
    fun addImport(moduleName: String, symbolName: String, alias: String = symbolName) {
        imports.add(UseStatement(moduleName, symbolName, alias))
    }

    override fun toString(): String {
        return imports.map { it.toString() }.sorted().joinToString(separator = "\n")
    }

    override fun importSymbol(symbol: Symbol, alias: String?) {
        if (symbol.namespace.isNotEmpty() && symbol.namespace != namespace) {
            addImport(symbol.namespace, symbol.name, alias ?: symbol.name)
        }
    }
}

private data class UseStatement(val moduleName: String, val symbolName: String, val alias: String) {
    val rendered: String
    get() {
        val alias = alias.let { if (it == symbolName) "" else " as $it" }
        return "use $moduleName::$symbolName$alias;"
    }

    override fun toString(): String = rendered
}

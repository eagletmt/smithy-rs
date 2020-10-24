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
 */

package software.amazon.smithy.rust.codegen.smithy.generators

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.traits.HttpTrait
import software.amazon.smithy.rust.codegen.lang.RustType
import software.amazon.smithy.rust.codegen.lang.RustWriter
import software.amazon.smithy.rust.codegen.smithy.RuntimeConfig
import software.amazon.smithy.rust.codegen.smithy.letIf
import software.amazon.smithy.rust.codegen.smithy.rename
import software.amazon.smithy.rust.codegen.smithy.rustType

class OperationGenerator(
    val model: Model,
    private val symbolProvider: SymbolProvider,
    private val runtimeConfig: RuntimeConfig,
    private val writer: RustWriter,
    private val shape: OperationShape
) {
    private val operationName: String = symbolProvider.toSymbol(shape).name

    inner class Replacer(private val old: Shape, val new: Symbol) : SymbolProvider {
        override fun toSymbol(shape: Shape): Symbol = when (shape) {
            old -> new
            else -> symbolProvider.toSymbol(shape)
        }

        override fun toMemberName(shape: MemberShape?): String {
            return symbolProvider.toMemberName(shape)
        }
    }

    fun render() {
        val httpTrait = shape.getTrait(HttpTrait::class.java)
        shape.input.map { model.expectShape(it, StructureShape::class.java) }.map {
            val inputSymbol = symbolProvider.toSymbol(it).rename("${operationName}Input")
            val renamer = Replacer(it, inputSymbol)

            StructureGenerator(model, renamer, writer, it).render()
            httpTrait.map { httpTrait ->
                HttpBindingGenerator(model, renamer, runtimeConfig, writer, shape, it, httpTrait).render()
            }
        }
        shape.output.map { model.expectShape(it, StructureShape::class.java) }.map { renderOutput(it) }
    }

    fun renderInput(shape: StructureShape) {

    }

    fun renderOutput(shape: StructureShape) {
        //StructureGenerator(model, InputRenamer("${operationName}Output"), writer, shape).render()
    }
}

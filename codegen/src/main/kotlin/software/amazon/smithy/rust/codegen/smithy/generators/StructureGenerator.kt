/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.rust.codegen.smithy.generators

import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.traits.ErrorTrait
import software.amazon.smithy.rust.codegen.lang.RustType
import software.amazon.smithy.rust.codegen.lang.RustWriter
import software.amazon.smithy.rust.codegen.lang.render
import software.amazon.smithy.rust.codegen.lang.rustBlock
import software.amazon.smithy.rust.codegen.lang.withBlock
import software.amazon.smithy.rust.codegen.smithy.Configurator
import software.amazon.smithy.rust.codegen.smithy.DefaultConfigurator
import software.amazon.smithy.rust.codegen.smithy.RuntimeType
import software.amazon.smithy.rust.codegen.smithy.canUseDefault
import software.amazon.smithy.rust.codegen.smithy.isOptional
import software.amazon.smithy.rust.codegen.smithy.makeOptional
import software.amazon.smithy.rust.codegen.smithy.rustType
import software.amazon.smithy.rust.codegen.util.dq
import software.amazon.smithy.utils.CaseUtils

// TODO(maybe): extract struct generation from Smithy shapes to support generating body objects
// TODO: generate documentation
class StructureGenerator(
    val model: Model,
    private val symbolProvider: SymbolProvider,
    private val writer: RustWriter,
    private val shape: StructureShape,
    private val configurator: Configurator = DefaultConfigurator(),
    private val renderBuilder: Boolean = true
) {
    private val members: List<MemberShape> = shape.allMembers.values.toList()
    private val structureSymbol = symbolProvider.toSymbol(shape)
    private val builderSymbol =
        RuntimeType("Builder", null, "${structureSymbol.namespace}::${structureSymbol.name.toSnakeCase()}")

    fun render() {
        renderStructure()
        val errorTrait = shape.getTrait(ErrorTrait::class.java)
        errorTrait.map {
            val errorGenerator = ErrorGenerator(model, symbolProvider, writer, shape, it)
            errorGenerator.render()
        }
        if (renderBuilder) {
            val symbol = symbolProvider.toSymbol(shape)
            writer.withModule(symbol.name.toSnakeCase()) {
                renderBuilder(this)
            }
        }
    }

    companion object {
        fun fallibleBuilder(structureShape: StructureShape, symbolProvider: SymbolProvider): Boolean = structureShape
            .allMembers
            .values.map { symbolProvider.toSymbol(it) }.any {
                // If any members are not optional && we can't use a default, we need to
                // generate a fallible builder
                !it.isOptional() && !it.canUseDefault()
            }
    }

    private fun renderStructure() {
        val symbol = symbolProvider.toSymbol(shape)
        // renders annotations & visibility
        configurator.container(shape).render(writer)
        writer.rustBlock("struct ${symbol.name} ${configurator.container(shape).lifetimes()}") {
            members.forEach { member ->
                val memberName = symbolProvider.toMemberName(member)
                val meta = configurator.member(member)
                meta.render(writer)
                write("$memberName: \$T,", symbolProvider.toSymbol(member))
            }
        }

        if (renderBuilder) {
            writer.rustBlock("impl ${symbol.name}") {
                rustBlock("pub fn builder() -> \$T", builderSymbol) {
                    write("\$T::default()", builderSymbol)
                }
            }
        }
    }

    private fun renderBuilder(writer: RustWriter) {
        val builderName = "Builder"
        writer.write("#[non_exhaustive]")
        writer.write("#[derive(Debug, Clone, Default)]")
        writer.rustBlock("pub struct $builderName") {
            members.forEach { member ->
                val memberName = symbolProvider.toMemberName(member)
                // All fields in the builder are optional
                val memberSymbol = symbolProvider.toSymbol(member).makeOptional()
                // TODO: should the builder members be public?
                write("$memberName: \$T,", memberSymbol)
            }
        }

        fun builderConverter(rustType: RustType) = when (rustType) {
            is RustType.String -> "inp.into()"
            else -> "inp"
        }

        writer.rustBlock("impl $builderName") {
            members.forEach { member ->
                val memberName = symbolProvider.toMemberName(member)
                // All fields in the builder are optional
                val memberSymbol = symbolProvider.toSymbol(member)
                val coreType = memberSymbol.rustType().let {
                    when (it) {
                        is RustType.Option -> it.value
                        else -> it
                    }
                }
                val signature = when (coreType) {
                    is RustType.String -> "<T: Into<String>>(mut self, inp: T) -> Self"
                    else -> "(mut self, inp: ${coreType.render()}) -> Self"
                }
                writer.rustBlock("pub fn $memberName$signature") {
                    write("self.$memberName = Some(${builderConverter(coreType)});")
                    write("self")
                }
            }

            val fallibleBuilder = fallibleBuilder(shape, symbolProvider)
            val returnType = when (fallibleBuilder) {
                true -> "Result<\$T, String>"
                false -> "\$T"
            }

            writer.rustBlock("pub fn build(self) -> $returnType", structureSymbol) {
                withBlock("Ok(", ")", conditional = fallibleBuilder) {
                    rustBlock("\$T", structureSymbol) {
                        members.forEach { member ->
                            val memberName = symbolProvider.toMemberName(member)
                            val memberSymbol = symbolProvider.toSymbol(member)
                            val errorWhenMissing = "$memberName is required when building ${structureSymbol.name}"
                            val modifier = when {
                                !memberSymbol.isOptional() && memberSymbol.canUseDefault() -> ".unwrap_or_default()"
                                !memberSymbol.isOptional() -> ".ok_or(${errorWhenMissing.dq()})?"
                                else -> ""
                            }
                            write("$memberName: self.$memberName$modifier,")
                        }
                    }
                }
            }
        }
    }
}

// String extensions
fun String.toSnakeCase(): String {
    return CaseUtils.toSnakeCase(this)
}

fun String.toPascalCase(): String {
    return CaseUtils.toSnakeCase(this).let { CaseUtils.toPascalCase(it) }
}

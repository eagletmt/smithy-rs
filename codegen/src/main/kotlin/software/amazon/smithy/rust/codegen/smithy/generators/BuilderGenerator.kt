/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.rust.codegen.smithy.generators

import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.rust.codegen.rustlang.Attribute
import software.amazon.smithy.rust.codegen.rustlang.RustType
import software.amazon.smithy.rust.codegen.rustlang.RustWriter
import software.amazon.smithy.rust.codegen.rustlang.asOptional
import software.amazon.smithy.rust.codegen.rustlang.conditionalBlock
import software.amazon.smithy.rust.codegen.rustlang.docs
import software.amazon.smithy.rust.codegen.rustlang.documentShape
import software.amazon.smithy.rust.codegen.rustlang.render
import software.amazon.smithy.rust.codegen.rustlang.rust
import software.amazon.smithy.rust.codegen.rustlang.rustBlock
import software.amazon.smithy.rust.codegen.rustlang.stripOuter
import software.amazon.smithy.rust.codegen.rustlang.withBlock
import software.amazon.smithy.rust.codegen.smithy.Default
import software.amazon.smithy.rust.codegen.smithy.RuntimeConfig
import software.amazon.smithy.rust.codegen.smithy.RuntimeType
import software.amazon.smithy.rust.codegen.smithy.RustSymbolProvider
import software.amazon.smithy.rust.codegen.smithy.defaultValue
import software.amazon.smithy.rust.codegen.smithy.expectRustMetadata
import software.amazon.smithy.rust.codegen.smithy.isOptional
import software.amazon.smithy.rust.codegen.smithy.makeOptional
import software.amazon.smithy.rust.codegen.smithy.rustType
import software.amazon.smithy.rust.codegen.util.dq
import software.amazon.smithy.rust.codegen.util.toSnakeCase

fun StructureShape.builderSymbol(symbolProvider: RustSymbolProvider): RuntimeType {
    val symbol = symbolProvider.toSymbol(this)
    return RuntimeType("Builder", null, "${symbol.namespace}::${symbol.name.toSnakeCase()}")
}

fun RuntimeConfig.operationBuildError() = RuntimeType.operationModule(this).member("BuildError")

class OperationBuildError(private val runtimeConfig: RuntimeConfig) {
    fun missingField(w: RustWriter, field: String, details: String) = "${w.format(runtimeConfig.operationBuildError())}::MissingField { field: ${field.dq()}, details: ${details.dq()} }"
    fun invalidField(w: RustWriter, field: String, details: String) = "${w.format(runtimeConfig.operationBuildError())}::InvalidField { field: ${field.dq()}, details: ${details.dq()}.to_string() }"
    fun serializationError(w: RustWriter, error: String) = "${w.format(runtimeConfig.operationBuildError())}::SerializationError($error.into())"
}

/** setter names will never hit a reserved word and therefore never need escaping */
fun MemberShape.setterName(): String = "set_${this.memberName.toSnakeCase()}"

class BuilderGenerator(
    val model: Model,
    private val symbolProvider: RustSymbolProvider,
    private val shape: StructureShape
) {
    private val members: List<MemberShape> = shape.allMembers.values.toList()
    private val runtimeConfig = symbolProvider.config().runtimeConfig
    private val structureSymbol = symbolProvider.toSymbol(shape)
    fun render(writer: RustWriter) {
        val symbol = symbolProvider.toSymbol(shape)
        // TODO: figure out exactly what docs we want on a the builder module
        writer.docs("See #D", symbol)
        val segments = shape.builderSymbol(symbolProvider).namespace.split("::")
        writer.withModule(segments.last()) {
            renderBuilder(this)
        }
    }

    private fun buildFn(implBlockWriter: RustWriter) {
        val fallibleBuilder = StructureGenerator.fallibleBuilder(shape, symbolProvider)
        val outputSymbol = symbolProvider.toSymbol(shape)
        val returnType = when (fallibleBuilder) {
            true -> "std::result::Result<${implBlockWriter.format(outputSymbol)}, ${implBlockWriter.format(runtimeConfig.operationBuildError())}>"
            false -> implBlockWriter.format(outputSymbol)
        }
        implBlockWriter.docs("Consumes the builder and constructs a #D", outputSymbol)
        implBlockWriter.rustBlock("pub fn build(self) -> $returnType") {
            conditionalBlock("Ok(", ")", conditional = fallibleBuilder) {
                // If a wrapper is specified, use the `::new` associated function to construct the wrapper
                coreBuilder(this)
            }
        }
    }

    private fun RustWriter.missingRequiredField(field: String) {
        val detailedMessage = "$field was not specified but it is required when building ${
        symbolProvider.toSymbol(
            shape
        ).name
        }"
        rust(
            """#T::MissingField { field: ${field.dq()}, details: ${detailedMessage.dq()}}""",
            runtimeConfig.operationBuildError()
        )
    }

    fun renderConvenienceMethod(implBlock: RustWriter) {
        val builderSymbol = shape.builderSymbol(symbolProvider)
        implBlock.docs("Creates a new builder-style object to manufacture #D", structureSymbol)
        implBlock.rustBlock("pub fn builder() -> #T", builderSymbol) {
            write("#T::default()", builderSymbol)
        }
    }

    private fun renderBuilder(writer: RustWriter) {
        val builderName = "Builder"

        val symbol = structureSymbol
        writer.docs("A builder for #D", symbol)
        Attribute.NonExhaustive.render(writer)
        // Matching derives to the main structure + `Default` since we are a builder and everything is optional
        val baseDerives = symbol.expectRustMetadata().derives
        val derives = baseDerives.derives.intersect(setOf(RuntimeType.Debug, RuntimeType.PartialEq, RuntimeType.Clone)) + RuntimeType.Default
        baseDerives.copy(derives = derives).render(writer)
        writer.rustBlock("pub struct $builderName") {
            members.forEach { member ->
                val memberName = symbolProvider.toMemberName(member)
                // All fields in the builder are optional
                val memberSymbol = symbolProvider.toSymbol(member).makeOptional()
                // builder members are crate-public to enable using them
                // directly in serializers/deserializers
                write("pub(crate) $memberName: #T,", memberSymbol)
            }
        }

        fun builderConverter(coreType: RustType) = when (coreType) {
            is RustType.String,
            is RustType.Box -> "input.into()"
            else -> "input"
        }

        writer.rustBlock("impl $builderName") {
            members.forEach { member ->
                // All fields in the builder are optional
                val memberSymbol = symbolProvider.toSymbol(member)
                val outerType = memberSymbol.rustType()
                val coreType = outerType.stripOuter<RustType.Option>()
                val memberName = symbolProvider.toMemberName(member)
                // Render a context-aware builder method for certain types, eg. a method for vectors that automatically
                // appends
                when (coreType) {
                    is RustType.Vec -> renderVecHelper(memberName, coreType)
                    is RustType.HashMap -> renderMapHelper(memberName, coreType)
                    else -> {
                        val signature = when (coreType) {
                            is RustType.String,
                            is RustType.Box -> "(mut self, input: impl Into<${coreType.render(true)}>) -> Self"
                            else -> "(mut self, input: ${coreType.render(true)}) -> Self"
                        }
                        writer.documentShape(member, model)
                        writer.rustBlock("pub fn $memberName$signature") {
                            write("self.$memberName = Some(${builderConverter(coreType)});")
                            write("self")
                        }
                    }
                }

                // Render a `set_foo` method. This is useful as a target for code generation, because the argument type
                // is the same as the resulting member type, and is always optional.
                val inputType = outerType.asOptional()
                writer.rustBlock("pub fn ${member.setterName()}(mut self, input: ${inputType.render(true)}) -> Self") {
                    rust("self.$memberName = input; self")
                }
            }
            buildFn(this)
        }
    }

    private fun RustWriter.renderVecHelper(memberName: String, coreType: RustType.Vec) {
        rustBlock("pub fn $memberName(mut self, input: impl Into<${coreType.member.render(true)}>) -> Self") {
            rust(
                """
                let mut v = self.$memberName.unwrap_or_default();
                v.push(input.into());
                self.$memberName = Some(v);
                self
                """
            )
        }
    }

    private fun RustWriter.renderMapHelper(memberName: String, coreType: RustType.HashMap) {
        rustBlock(
            "pub fn $memberName(mut self, k: impl Into<${coreType.key.render(true)}>, v: impl Into<${
            coreType.member.render(
                true
            )
            }>) -> Self"
        ) {
            rust(
                """
                let mut hash_map = self.$memberName.unwrap_or_default();
                hash_map.insert(k.into(), v.into());
                self.$memberName = Some(hash_map);
                self
                """
            )
        }
    }

    /**
     * The core builder of the inner type. If the structure requires a fallible builder, this may use `?` to return
     * errors
     * ```rust
     * SomeStruct {
     *    field: builder.field,
     *    field2: builder.field2,
     *    field3: builder.field3.unwrap_or_default()
     *    field4: builder.field4.ok_or("field4 is required when building SomeStruct")?
     * }
     * ```
     */
    protected fun coreBuilder(writer: RustWriter) {
        writer.rustBlock("#T", structureSymbol) {
            members.forEach { member ->
                val memberName = symbolProvider.toMemberName(member)
                val memberSymbol = symbolProvider.toSymbol(member)
                val default = memberSymbol.defaultValue()
                withBlock("$memberName: self.$memberName", ",") {
                    // Write the modifier
                    when {
                        !memberSymbol.isOptional() && default == Default.RustDefault -> rust(".unwrap_or_default()")
                        !memberSymbol.isOptional() -> withBlock(
                            ".ok_or(",
                            ")?"
                        ) { missingRequiredField(memberName) }
                    }
                }
            }
        }
    }
}

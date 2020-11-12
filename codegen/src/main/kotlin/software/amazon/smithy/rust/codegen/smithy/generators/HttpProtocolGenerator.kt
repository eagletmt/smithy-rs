/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.rust.codegen.smithy.generators

import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.rust.codegen.lang.RustWriter
import software.amazon.smithy.rust.codegen.lang.rustBlock
import software.amazon.smithy.rust.codegen.smithy.Configurator
import software.amazon.smithy.rust.codegen.smithy.RuntimeConfig
import software.amazon.smithy.rust.codegen.smithy.RuntimeType
import software.amazon.smithy.rust.codegen.smithy.SymbolVisitor
import software.amazon.smithy.rust.codegen.smithy.traits.SyntheticInputTrait
import software.amazon.smithy.rust.codegen.smithy.transformers.OperationNormalizer

data class ProtocolConfig(
    val model: Model,
    val symbolProvider: SymbolProvider,
    val runtimeConfig: RuntimeConfig,
    val serviceShape: ServiceShape,
    val protocol: ShapeId
)

interface ProtocolGeneratorFactory<out T : HttpProtocolGenerator> {
    fun buildProtocolGenerator(protocolConfig: ProtocolConfig): T
    fun preprocessModel(model: Model, symbolProvider: SymbolVisitor): Model {
        // don't make a body by default
        return OperationNormalizer(symbolProvider).addOperationInputs(model) {
            null
        }
    }
}

abstract class HttpProtocolGenerator(
    private val protocolConfig: ProtocolConfig
) {
    private val symbolProvider = protocolConfig.symbolProvider
    fun render(writer: RustWriter, inputShape: StructureShape, operationShape: OperationShape) {
        writer.rustBlock("impl ${symbolProvider.toSymbol(inputShape).name}") {
            toHttpRequestImpl(this, inputShape, operationShape)
            val shapeId = inputShape.expectTrait(SyntheticInputTrait::class.java).body
            val body = shapeId?.let { protocolConfig.model.expectShape(it, StructureShape::class.java) }
            toBodyImpl(this, inputShape, body)
        }
    }

    protected fun httpBuilderFun(implBlockWriter: RustWriter, f: RustWriter.() -> Unit) {
        implBlockWriter.rustBlock(
            "pub fn build_http_request(&self) -> \$T",
            RuntimeType.HttpRequestBuilder
        ) {
            f(this)
        }
    }

    /**
     * Add necessary methods to the impl block for the input shape.
     *
     * Your implementation MUST call `httpBuilderFun` to create the public method.
     */
    abstract fun toHttpRequestImpl(implBlockWriter: RustWriter, inputShape: StructureShape, operationShape: OperationShape)

    abstract fun toBodyImpl(implBlockWriter: RustWriter, inputShape: StructureShape, inputBody: StructureShape?)

    open fun bodyConfigurator(base: Configurator): Configurator = base
    open fun modelConfigurator(base: Configurator): Configurator = base
}

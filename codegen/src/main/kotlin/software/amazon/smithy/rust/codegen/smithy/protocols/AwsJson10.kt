/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.rust.codegen.smithy.protocols

import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.rust.codegen.lang.RustWriter
import software.amazon.smithy.rust.codegen.lang.rustBlock
import software.amazon.smithy.rust.codegen.smithy.Configurator
import software.amazon.smithy.rust.codegen.smithy.RuntimeType
import software.amazon.smithy.rust.codegen.smithy.SymbolVisitor
import software.amazon.smithy.rust.codegen.smithy.generators.HttpProtocolGenerator
import software.amazon.smithy.rust.codegen.smithy.generators.ProtocolConfig
import software.amazon.smithy.rust.codegen.smithy.generators.ProtocolGeneratorFactory
import software.amazon.smithy.rust.codegen.smithy.transformers.OperationNormalizer

class AwsJson10Factory : ProtocolGeneratorFactory<AwsJson10Generator> {
    override fun buildProtocolGenerator(
        protocolConfig: ProtocolConfig
    ): AwsJson10Generator = AwsJson10Generator(protocolConfig)

    override fun preprocessModel(model: Model, symbolProvider: SymbolVisitor): Model {
        // For AwsJson10, every input field is in the body
        return OperationNormalizer(symbolProvider).addOperationInputs(model) { body ->
            // if there are no members, there won't be a body
            if (body.members().isEmpty()) {
                null
            } else body
        }
    }
}

class AwsJson10Generator(
    private val protocolConfig: ProtocolConfig
) : HttpProtocolGenerator(protocolConfig.symbolProvider) {
    override fun toHttpRequestImpl(
        implBlockWriter: RustWriter,
        inputShape: StructureShape,
        operationShape: OperationShape
    ) {
        implBlockWriter.rustBlock("pub fn build_http_request(&self) -> \$T", RuntimeType.HttpRequestBuilder) {
            write("let builder = \$T::new();", RuntimeType.HttpRequestBuilder)
            write(
                """
                builder
                   .method("POST")
                   .header("Content-Type", "application/x-amz-json-1.0")
                   .header("X-Amz-Target", "${protocolConfig.serviceShape.id.name}.${operationShape.id.name}")
               """.trimMargin()
            )
        }
    }

    override fun bodyConfigurator(base: Configurator): Configurator {
        return JsonProtocolConfigurator(base, protocolConfig)
    }

    override fun modelConfigurator(base: Configurator): Configurator {
        return JsonProtocolConfigurator(base, protocolConfig)
    }
}

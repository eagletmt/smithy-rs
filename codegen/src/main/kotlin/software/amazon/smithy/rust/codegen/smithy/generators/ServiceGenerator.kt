/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.rust.codegen.smithy.generators

import software.amazon.smithy.aws.traits.protocols.AwsJson1_0Trait
import software.amazon.smithy.aws.traits.protocols.AwsJson1_1Trait
import software.amazon.smithy.aws.traits.protocols.RestJson1Trait
import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.codegen.core.writer.CodegenWriterDelegator
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.ServiceIndex
import software.amazon.smithy.model.knowledge.TopDownIndex
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.traits.Trait
import software.amazon.smithy.rust.codegen.lang.RustWriter
import software.amazon.smithy.rust.codegen.smithy.DefaultConfigurator
import software.amazon.smithy.rust.codegen.smithy.protocols.AwsJson10Factory
import software.amazon.smithy.rust.codegen.smithy.protocols.AwsRestJsonFactory
import software.amazon.smithy.rust.codegen.smithy.traits.SyntheticInputTrait

// TODO: supportedProtocols to be runtime pluggable; 2d
class ProtocolLoader(private val supportedProtocols: Map<ShapeId, ProtocolGeneratorFactory<HttpProtocolGenerator>>) {
    fun protocolFor(
        model: Model,
        serviceShape: ServiceShape
    ): Pair<ShapeId, ProtocolGeneratorFactory<HttpProtocolGenerator>> {
        val protocols: MutableMap<ShapeId, Trait> = ServiceIndex(model).getProtocols(serviceShape)
        val matchingProtocols =
            protocols.keys.mapNotNull { protocolId -> supportedProtocols[protocolId]?.let { protocolId to it } }
        if (matchingProtocols.isEmpty()) {
            throw CodegenException("No matching protocol — service offers: ${protocols.keys}. We offer: ${supportedProtocols.keys}")
        }
        return matchingProtocols.first()
    }

    companion object {
        private val Protocols = mapOf(
            AwsJson1_0Trait.ID to AwsJson10Factory(),
            AwsJson1_1Trait.ID to AwsJson10Factory(),
            RestJson1Trait.ID to AwsRestJsonFactory()
        )
        val Default = ProtocolLoader(Protocols)
    }
}

class ServiceGenerator(
    private val writers: CodegenWriterDelegator<RustWriter>,
    private val protocolGenerator: HttpProtocolGenerator,
    private val config: ProtocolConfig
) {
    private val index = TopDownIndex(config.model)

    fun render() {
        val operations = index.getContainedOperations(config.serviceShape)
        val configurator = protocolGenerator.bodyConfigurator(DefaultConfigurator())
        operations.forEach { operation ->
            val input = operation.input.get().let { config.model.expectShape(it, StructureShape::class.java) }
            writers.useShapeWriter(operation) { writer ->
                // transform ensures that all models have input shapes
                protocolGenerator.render(writer, input, operation)
                HttpProtocolTestGenerator(config, operation, input, writer).render()
            }
            val body = input.expectTrait(SyntheticInputTrait::class.java).body
            val bodyShape = body?.let { config.model.expectShape(it, StructureShape::class.java) }
            bodyShape?.let {
                writers.useShapeWriter(bodyShape) {
                    StructureGenerator(
                        config.model,
                        config.symbolProvider,
                        it,
                        bodyShape,
                        configurator = configurator,
                        renderBuilder = false
                    ).render()
                }
            }
        }
        configurator.close(writers)
    }
}

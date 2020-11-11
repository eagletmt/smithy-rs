/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.rust.codegen.smithy

import software.amazon.smithy.build.PluginContext
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.codegen.core.writer.CodegenWriterDelegator
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.neighbor.Walker
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.ShapeVisitor
import software.amazon.smithy.model.shapes.StringShape
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.shapes.UnionShape
import software.amazon.smithy.model.traits.EnumTrait
import software.amazon.smithy.rust.codegen.lang.RustDependency
import software.amazon.smithy.rust.codegen.lang.RustWriter
import software.amazon.smithy.rust.codegen.smithy.generators.CargoTomlGenerator
import software.amazon.smithy.rust.codegen.smithy.generators.EnumGenerator
import software.amazon.smithy.rust.codegen.smithy.generators.HttpProtocolGenerator
import software.amazon.smithy.rust.codegen.smithy.generators.LibRsGenerator
import software.amazon.smithy.rust.codegen.smithy.generators.Module
import software.amazon.smithy.rust.codegen.smithy.generators.ProtocolConfig
import software.amazon.smithy.rust.codegen.smithy.generators.ProtocolGeneratorFactory
import software.amazon.smithy.rust.codegen.smithy.generators.ProtocolLoader
import software.amazon.smithy.rust.codegen.smithy.generators.ServiceGenerator
import software.amazon.smithy.rust.codegen.smithy.generators.StructureGenerator
import software.amazon.smithy.rust.codegen.smithy.generators.UnionGenerator
import software.amazon.smithy.rust.codegen.util.CommandFailed
import software.amazon.smithy.rust.codegen.util.runCommand
import java.util.logging.Logger

private val Modules = listOf(
    Module("error", true),
    Module("operation", true),
    Module("model", true),
    Module("serializer", false)
)

class CodegenVisitor(context: PluginContext) : ShapeVisitor.Default<Unit>() {

    private val logger = Logger.getLogger(javaClass.name)
    private val settings = RustSettings.from(context.model, context.settings)

    private val symbolProvider: SymbolProvider
    private val writers: CodegenWriterDelegator<RustWriter>
    private val fileManifest = context.fileManifest
    private val protocolConfig: ProtocolConfig
    private val protocolGenerator: ProtocolGeneratorFactory<HttpProtocolGenerator>
    private val httpGenerator: HttpProtocolGenerator
    val model: Model
    init {
        val bootstrapProvider = SymbolVisitor(context.model, config = SymbolVisitorConfig(runtimeConfig = settings.runtimeConfig))
        val service = settings.getService(context.model)
        val (protocol, generator) = ProtocolLoader.Default.protocolFor(context.model, service)
        protocolGenerator = generator

        model = generator.preprocessModel(context.model, bootstrapProvider)
        symbolProvider = SymbolVisitor(model, config = SymbolVisitorConfig(runtimeConfig = settings.runtimeConfig))
        protocolConfig = ProtocolConfig(model, symbolProvider, settings.runtimeConfig, service, protocol)
        writers = CodegenWriterDelegator(
            context.fileManifest,
            // TODO: load symbol visitor from integrations; 2d
            symbolProvider,
            RustWriter.Factory
        )
        httpGenerator = protocolGenerator.buildProtocolGenerator(protocolConfig)
    }

    fun execute() {
        logger.info("generating Rust client...")
        val serviceShapes = Walker(model).walkShapes(protocolConfig.serviceShape)
        serviceShapes.forEach { it.accept(this) }
        writers.useFileWriter("Cargo.toml") {
            val cargoToml = CargoTomlGenerator(
                settings,
                it,
                writers.dependencies.map { dep -> RustDependency.fromSymbolDependency(dep) }.distinct()
            )
            cargoToml.render()
        }
        writers.useFileWriter("src/lib.rs", "crate::lib") {
            // TODO: build a more structured method of signaling what modules should get loaded.
            val modules = Modules.filter { module -> writers.writers.containsKey("src/${module.name}.rs") }
            LibRsGenerator(modules, it).render()
        }
        writers.flushWriters()
        try {
            "cargo fmt".runCommand(fileManifest.baseDir)
        } catch (_: CommandFailed) {
            logger.warning("Generated output did not parse")
        }
    }

    override fun getDefault(shape: Shape?) {
    }

    override fun structureShape(shape: StructureShape) {
        val configurator = httpGenerator.modelConfigurator(DefaultConfigurator())
        writers.useShapeWriter(shape) {
            StructureGenerator(model, symbolProvider, it, shape, configurator = configurator).render()
        }
        configurator.close(writers)
    }

    override fun stringShape(shape: StringShape) {
        val configurator = httpGenerator.modelConfigurator(DefaultConfigurator())
        shape.getTrait(EnumTrait::class.java).map { enum ->
            writers.useShapeWriter(shape) { writer ->
                EnumGenerator(symbolProvider, writer, shape, enum, configurator = configurator).render()
            }
        }
        configurator.close(writers)
    }

    override fun unionShape(shape: UnionShape) {
        writers.useShapeWriter(shape) {
            UnionGenerator(model, symbolProvider, it, shape, configurator = httpGenerator.modelConfigurator(DefaultConfigurator())).render()
        }
    }

    override fun serviceShape(shape: ServiceShape) {
        ServiceGenerator(writers, protocolGenerator.buildProtocolGenerator(protocolConfig), protocolConfig).render()
    }
}

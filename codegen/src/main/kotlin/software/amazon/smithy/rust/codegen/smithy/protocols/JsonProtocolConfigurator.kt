package software.amazon.smithy.rust.codegen.smithy.protocols

import software.amazon.smithy.codegen.core.writer.CodegenWriterDelegator
import software.amazon.smithy.model.knowledge.HttpBinding
import software.amazon.smithy.model.knowledge.HttpBindingIndex
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.traits.TimestampFormatTrait
import software.amazon.smithy.rust.codegen.lang.Custom
import software.amazon.smithy.rust.codegen.lang.Meta
import software.amazon.smithy.rust.codegen.lang.RustType
import software.amazon.smithy.rust.codegen.lang.RustWriter
import software.amazon.smithy.rust.codegen.lang.contains
import software.amazon.smithy.rust.codegen.lang.render
import software.amazon.smithy.rust.codegen.smithy.Configurator
import software.amazon.smithy.rust.codegen.smithy.RuntimeType
import software.amazon.smithy.rust.codegen.smithy.generators.ProtocolConfig
import software.amazon.smithy.rust.codegen.smithy.rustType
import software.amazon.smithy.rust.codegen.util.dq

class JsonProtocolConfigurator(private val base: Configurator, private val protocolConfig: ProtocolConfig) : Configurator {
    val httpBindingIndex = HttpBindingIndex.of(protocolConfig.model)
    private val serializers = mutableMapOf<String, RustType>()
    override fun container(container: Shape): Meta {
        val baseMeta = base.container(container)
        return baseMeta.copy(
            derives = baseMeta.derives.copy(derives = baseMeta.derives.derives + listOf(RuntimeType.Serialize))
            // public = false
        )
    }

    private fun specialSerializer(target: Shape): Custom? {
        val targetRustType = protocolConfig.symbolProvider.toSymbol(target).rustType()
        val instant = RuntimeType.Instant(protocolConfig.runtimeConfig).toSymbol().rustType()
        val blob = RuntimeType.Blob(protocolConfig.runtimeConfig).toSymbol().rustType()
        return if (targetRustType.contains(instant) || targetRustType.contains(blob)) {
            val funcTyped = targetRustType.render().filter { it.isLetterOrDigit() }.toLowerCase()
            val format = if (targetRustType.contains(instant)) {
                val format = httpBindingIndex.determineTimestampFormat(target, HttpBinding.Location.PAYLOAD, TimestampFormatTrait.Format.EPOCH_SECONDS)
                format.name.replace('-', '_').toLowerCase().let { "_$it" }
            } else ""
            val funcName = "${funcTyped}_ser$format"
            val fullFunc = RuntimeType.SerdeUtils(protocolConfig.runtimeConfig, funcName)
            Custom("serde(serialize_with = ${fullFunc.fullyQualifiedName().dq()})", listOf(fullFunc))
        } else {
            null
        }
    }

    override fun member(member: MemberShape): Meta {
        // val target = protocolConfig.model.expectShape(member.target)
        val serAnnotations = specialSerializer(member)?.let { listOf(it) } ?: listOf()
        val rename = listOf(Custom("serde(rename = ${member.memberName.dq()})"))
        val baseMeta = base.member(member)
        return baseMeta.copy(annotations = baseMeta.annotations + rename + serAnnotations)
    }

    override fun close(writers: CodegenWriterDelegator<RustWriter>) {
        /*writers.useFileWriter("src/" + Serializers.filename, "crate::${Serializers.namespace}") { writer ->
            if (!serializers.isEmpty()) {
                println("serializers: ${serializers}")
            }

            serializers.forEach { (funcName, rustType) ->
                writer.rustBlock("""fn $funcName<S>(inp: ${rustType.render()}, serializer: S) -> Result<<S as Serializer>::Ok, <S as Serializer>::Error>
                    | where S: Serializer
                """.trimMargin()) {
                    write("todo!()")
                }
            }


        }*/
    }
}

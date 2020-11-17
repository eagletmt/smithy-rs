package software.amazon.smithy.rust.codegen.smithy.protocols

import org.intellij.lang.annotations.Language
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.HttpBinding
import software.amazon.smithy.model.knowledge.HttpBindingIndex
import software.amazon.smithy.model.shapes.BlobShape
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.StringShape
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.shapes.TimestampShape
import software.amazon.smithy.model.shapes.UnionShape
import software.amazon.smithy.model.traits.TimestampFormatTrait
import software.amazon.smithy.rust.codegen.lang.Custom
import software.amazon.smithy.rust.codegen.lang.Meta
import software.amazon.smithy.rust.codegen.lang.RustType
import software.amazon.smithy.rust.codegen.lang.RustWriter
import software.amazon.smithy.rust.codegen.lang.VendoredDependency
import software.amazon.smithy.rust.codegen.lang.contains
import software.amazon.smithy.rust.codegen.lang.render
import software.amazon.smithy.rust.codegen.lang.rustBlock
import software.amazon.smithy.rust.codegen.smithy.RuntimeType
import software.amazon.smithy.rust.codegen.smithy.SymbolMetadataProvider
import software.amazon.smithy.rust.codegen.smithy.expectMeta
import software.amazon.smithy.rust.codegen.smithy.rustType
import software.amazon.smithy.rust.codegen.util.dq

/**
 * JsonSerializerSymbolProvider annotates shapes and members with `serde` attributes
 */
class JsonSerializerSymbolProvider(
    private val model: Model,
    private val base: SymbolProvider,
    private val defaultTimestampFormat: TimestampFormatTrait.Format

) :
    SymbolMetadataProvider(base) {
    val httpIndex = HttpBindingIndex.of(model)
    override fun memberMeta(memberShape: MemberShape): Meta {
        val currentMeta = base.toSymbol(memberShape).expectMeta()
        val renameAttribute = Custom("serde(rename = ${memberShape.memberName.dq()})")
        val serializer = serializerFor(memberShape)
        val serdeAttribute = serializer?.let {
            listOf(Custom("serde(serialize_with = ${serializer.fullyQualifiedName().dq()})", listOf(it)))
        } ?: listOf()
        return currentMeta.copy(additionalAttributes = currentMeta.additionalAttributes + renameAttribute + serdeAttribute)
    }

    override fun structureMeta(structureShape: StructureShape): Meta {
        val currentMeta = base.toSymbol(structureShape).expectMeta()
        return currentMeta.withDerive(RuntimeType.Serialize)
    }

    override fun unionMeta(unionShape: UnionShape): Meta {
        val currentMeta = base.toSymbol(unionShape).expectMeta()
        return currentMeta.withDerive(RuntimeType.Serialize)
    }

    override fun enumMeta(stringShape: StringShape): Meta {
        val currentMeta = base.toSymbol(stringShape).expectMeta()
        return currentMeta.withDerive(RuntimeType.Serialize)
    }

    private fun serializerFor(memberShape: MemberShape): RuntimeType? {
        val rustType = base.toSymbol(memberShape).rustType()
        val instant = base.toSymbol(TimestampShape.builder().id("dummy#ts").build()).rustType()
        val blob = base.toSymbol(BlobShape.builder().id("dummy#ts").build()).rustType()
        if (!(rustType.contains(blob) || rustType.contains(instant))) {
            return null
        }
        val targetType = when (rustType) {
            is RustType.Reference -> rustType.value
            else -> rustType
        }
        val typeFuncable = targetType.render().filter { it.isLetterOrDigit() }.toLowerCase()
        return when {
            rustType.contains(instant) -> instantSerializer(memberShape, typeFuncable, targetType)
            rustType.contains(blob) -> blobSerializer(memberShape, typeFuncable, targetType)
            else -> null
        }
    }

    private fun serializeFn(rustWriter: RustWriter, functionName: String, symbol: Symbol, targetType: RustType, body: RustWriter.() -> Unit) {
        val ref = RustType.Reference(lifetime = null, value = targetType)
        val newSymbol = symbol.toBuilder().rustType(ref).build()
        rustWriter.rustBlock(
            "pub fn $functionName<S>(_inp: \$T, _serializer: S) -> " +
                "Result<<S as \$T>::Ok, <S as \$T>::Error> where S: \$T",
            newSymbol,
            RuntimeType.Serializer,
            RuntimeType.Serializer,
            RuntimeType.Serializer
        ) {
            body(this)
        }
    }

    private fun blobSerializer(memberShape: MemberShape, baseTypeName: String, argType: RustType): RuntimeType {
        val instantFormat =
            httpIndex.determineTimestampFormat(memberShape, HttpBinding.Location.PAYLOAD, defaultTimestampFormat)
        val symbol = base.toSymbol(memberShape)
        val fnName = "${baseTypeName}_${instantFormat.name.replace('-', '_').toLowerCase()}"
        val serializer: (RustWriter) -> Unit = { rustWriter: RustWriter ->
            serializeFn(rustWriter, fnName, symbol, argType) {
                write("todo!()")
            }
        }
        return RuntimeType(
            fnName,
            VendoredDependency(fnName, "serde_util", serializer),
            namespace = "crate::serde_util"
        )
    }

    private fun instantSerializer(memberShape: MemberShape, baseTypeName: String, argType: RustType): RuntimeType {
        val instantFormat =
            httpIndex.determineTimestampFormat(memberShape, HttpBinding.Location.PAYLOAD, defaultTimestampFormat)
        val symbol = base.toSymbol(memberShape)
        val fnName = "${baseTypeName}_${instantFormat.name.replace('-', '_').toLowerCase()}"
        val serializer: (RustWriter) -> Unit = { rustWriter: RustWriter ->
            serializeFn(rustWriter, fnName, symbol, argType) {
                @Language("Rust", prefix = "fn main() {", suffix = "}")
                val someRust = "let x = 123;"
                write("let x = 5;")
            }
        }
        return RuntimeType(
            fnName,
            VendoredDependency(fnName, "serde_util", serializer),
            namespace = "crate::serde_util"
        )
    }
}

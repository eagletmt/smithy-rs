package software.amazon.smithy.rust.codegen.smithy.protocols

import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.StringShape
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.shapes.UnionShape
import software.amazon.smithy.rust.codegen.lang.Custom
import software.amazon.smithy.rust.codegen.lang.Meta
import software.amazon.smithy.rust.codegen.lang.RustType
import software.amazon.smithy.rust.codegen.smithy.RuntimeType
import software.amazon.smithy.rust.codegen.smithy.SymbolMetadataProvider
import software.amazon.smithy.rust.codegen.smithy.expectMeta
import software.amazon.smithy.rust.codegen.util.dq

/**
 * JsonSerializerSymbolProvider annotates shapes and members with `serde` attributes
 */
class JsonSerializerSymbolProvider(private val model: Model, private val base: SymbolProvider) : SymbolMetadataProvider(base) {
    override fun memberMeta(memberShape: MemberShape): Meta {
        val currentMeta = base.toSymbol(memberShape).expectMeta()
        val renameAttribute = Custom("serde(rename = ${memberShape.memberName.dq()})")
        return currentMeta.copy(additionalAttributes = currentMeta.additionalAttributes + renameAttribute)
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

    private fun serializerFor(rustType: RustType): RuntimeType {
    }
}

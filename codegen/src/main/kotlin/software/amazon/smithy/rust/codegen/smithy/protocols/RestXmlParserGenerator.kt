/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.rust.codegen.smithy.protocols

import software.amazon.smithy.model.knowledge.HttpBinding
import software.amazon.smithy.model.knowledge.HttpBindingIndex
import software.amazon.smithy.model.shapes.MapShape
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.StringShape
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.traits.EnumTrait
import software.amazon.smithy.model.traits.XmlAttributeTrait
import software.amazon.smithy.model.traits.XmlFlattenedTrait
import software.amazon.smithy.model.traits.XmlNameTrait
import software.amazon.smithy.rust.codegen.rustlang.CargoDependency
import software.amazon.smithy.rust.codegen.rustlang.RustType
import software.amazon.smithy.rust.codegen.rustlang.RustWriter
import software.amazon.smithy.rust.codegen.rustlang.asType
import software.amazon.smithy.rust.codegen.rustlang.conditionalBlock
import software.amazon.smithy.rust.codegen.rustlang.escape
import software.amazon.smithy.rust.codegen.rustlang.rust
import software.amazon.smithy.rust.codegen.rustlang.rustBlock
import software.amazon.smithy.rust.codegen.rustlang.rustBlockT
import software.amazon.smithy.rust.codegen.rustlang.rustTemplate
import software.amazon.smithy.rust.codegen.rustlang.withBlock
import software.amazon.smithy.rust.codegen.smithy.RuntimeType
import software.amazon.smithy.rust.codegen.smithy.generators.ProtocolConfig
import software.amazon.smithy.rust.codegen.smithy.generators.StructureGenerator
import software.amazon.smithy.rust.codegen.smithy.generators.builderSymbol
import software.amazon.smithy.rust.codegen.smithy.generators.setterName
import software.amazon.smithy.rust.codegen.smithy.isBoxed
import software.amazon.smithy.rust.codegen.smithy.isOptional
import software.amazon.smithy.rust.codegen.smithy.traits.SyntheticOutputTrait
import software.amazon.smithy.rust.codegen.util.dq
import software.amazon.smithy.rust.codegen.util.expectMember
import software.amazon.smithy.rust.codegen.util.orNull
import software.amazon.smithy.rust.codegen.util.outputShape
import software.amazon.smithy.rust.codegen.util.toSnakeCase

class RestXmlParserGenerator(private val operationShape: OperationShape, protocolConfig: ProtocolConfig) {

    data class XmlName(val local: String, val prefix: String? = null) {
        override fun toString(): String {
            return prefix?.let { "$it:" }.orEmpty() + local
        }
    }

    private val symbolProvider = protocolConfig.symbolProvider
    private val smithyXml = CargoDependency.smithyXml(protocolConfig.runtimeConfig).asType()
    private val xmlError = smithyXml.member("decode::XmlError")

    private val scopedDecoder = smithyXml.member("decode::ScopedDecoder")
    private val codegenScope = arrayOf(
        "Document" to smithyXml.member("decode::Document"),
        "XmlError" to xmlError,
        "next_start_element" to smithyXml.member("decode::next_start_element"),
        "expect_data" to smithyXml.member("decode::expect_data"),
        "ScopedDecoder" to scopedDecoder
    )
    private val model = protocolConfig.model
    private val index = HttpBindingIndex.of(model)
    private val shape = operationShape.outputShape(model)

    data class Ctx(val tag: String, val currentTarget: String?)

    private fun RustWriter.parseLoop(ctx: Ctx, ignoreUnexpected: Boolean = true, inner: RustWriter.(Ctx) -> Unit) {
        rustBlock("while let Some(mut tag) = ${ctx.tag}.next_tag()") {
            rustBlock("match tag.start_el()") {
                inner(ctx.copy(tag = "tag"))
                if (ignoreUnexpected) {
                    rust("_ => {}")
                }
            }
        }
    }

    fun operationParser(): RuntimeType {
        val fnName = shape.id.name.toString().toSnakeCase()
        return RuntimeType.forInlineFun(fnName, "xml_deser") {
            it.rustBlock(
                "pub fn $fnName(inp: &[u8], mut builder: #1T) -> Result<#1T, #2T>",
                shape.builderSymbol(symbolProvider),
                xmlError
            ) {
                val shapeName = XmlName(
                    local = shape.expectTrait(SyntheticOutputTrait::class.java).originalId!!.name,
                    prefix = null
                )
                rustTemplate(
                    """
                    use std::convert::TryFrom;
                    let mut doc = #{Document}::try_from(inp)?;
                    let mut decoder = doc.scoped()?;
                    let start_el = decoder.start_el();
                    if !(${shapeName.compareTo("start_el")}) {
                        return Err(#{XmlError}::Custom(format!("invalid root, expected $shapeName got {:?}", start_el)))
                    }
                    """,
                    *codegenScope
                )
                val members = operationShape.operationXmlMembers()
                members.attributeMembers.forEach { member ->
                    val temp = safeName("attrib")
                    withBlock("let $temp = ", ";") {
                        parseAttributeMember(member, Ctx("decoder", null))
                    }
                    rust("builder = builder.${member.setterName()}($temp);")
                }
                parseLoop(Ctx(tag = "decoder", currentTarget = null)) { ctx ->
                    members.dataMembers.forEach { member ->
                        val memberName = member.xmlName()
                        withBlock("s if ${memberName.compareTo("s")} => {", "},") {
                            val temp = safeName()
                            withBlock("let $temp = ", ";") {
                                parseMember(
                                    member,
                                    ctx.copy(currentTarget = "builder.${symbolProvider.toMemberName(member)}.take()")
                                )
                            }
                            rust("builder = builder.${member.setterName()}($temp);")
                        }
                    }
                }
                rust("Ok(builder)")
            }
        }
    }

    private fun MemberShape.isFlattened(): Boolean {
        return getMemberTrait(model, XmlFlattenedTrait::class.java).isPresent
    }

    private fun RustWriter.parseMember(memberShape: MemberShape, ctx: Ctx) {
        val target = model.expectShape(memberShape.target)
        val symbol = symbolProvider.toSymbol(memberShape)
        conditionalBlock("Some(", ")", symbol.isOptional()) {
            conditionalBlock("Box::new(", ")", symbol.isBoxed()) {
                when (target) {
                    is StringShape -> parseString(target, ctx)
                    is MapShape -> if (memberShape.isFlattened()) {
                        parseFlatMap(target, ctx)
                    } else {
                        parseMap(target, ctx)
                    }
                    is StructureShape -> parseStructure(target, ctx)
                    else -> rust("todo!()")
                }
            }
        }
    }

    private fun RustWriter.parseMap(target: MapShape, ctx: Ctx) {
        val fnName = "deserialize_${target.value.id.name.toSnakeCase()}"
        val mapParser = RuntimeType.forInlineFun(fnName, "xml_deser") {
            it.rustBlockT(
                "pub fn $fnName(mut decoder: &mut #{ScopedDecoder}) -> Result<#{Map}, #{XmlError}>",
                *codegenScope,
                "Map" to symbolProvider.toSymbol(target)
            ) {
                rust("let mut out = #T::new();", RustType.HashMap.RuntimeType)
                parseLoop(Ctx(tag = "decoder", currentTarget = null)) { ctx ->
                    rustBlock("s if ${XmlName(local = "entry").compareTo("s")} => ") {
                        rust("#T(&mut ${ctx.tag}, &mut out)?;", mapEntryParser(target, ctx))
                    }
                }
                rust("Ok(out)")
            }
        }
        rust("#T(&mut ${ctx.tag})?", mapParser)
    }

    private fun RustWriter.parseFlatMap(target: MapShape, ctx: Ctx) {
        val map = safeName("map")
        val entryDecoder = mapEntryParser(target, ctx)
        rust(
            """{
            let mut $map = ${ctx.currentTarget!!}.unwrap_or_default();
            #T(&mut tag, &mut $map)?;
            $map
            }
            """,
            entryDecoder
        )
    }

    private fun mapEntryParser(
        target: MapShape,
        ctx: Ctx
    ): RuntimeType {

        val fnName = target.value.id.name.toSnakeCase() + "_entry"
        return RuntimeType.forInlineFun(fnName, "xml_deser") {
            it.rustBlockT(
                "pub fn $fnName(mut decoder: &mut #{ScopedDecoder}, out: &mut #{Map}) -> Result<(), #{XmlError}>",
                *codegenScope,
                "Map" to symbolProvider.toSymbol(target)
            ) {
                rust("let mut k: Option<String> = None;")
                rust("let mut v: Option<#T> = None;", symbolProvider.toSymbol(model.expectShape(target.value.target)))
                rustBlockT("while let Some(mut tag) = decoder.next_tag()") {
                    rustBlock("match tag.start_el()") {
                        withBlock("s if ${target.key.xmlName().compareTo("s")} => k = Some(", "),") {
                            parseMember(target.key, ctx = ctx.copy(currentTarget = null))
                        }
                        withBlock("s if ${target.value.xmlName().compareTo("s")} => v = Some(", "),") {
                            parseMember(target.value, ctx = ctx.copy(currentTarget = "v"))
                        }
                        rust("_ => {}")
                    }
                }

                rustTemplate(
                    """
                            let k = k.ok_or(#{XmlError}::Other { msg: "missing key value in map "})?;
                            let v = v.ok_or(#{XmlError}::Other { msg: "missing key value in map "})?;
                            out.insert(k, v);
                            Ok(())
                        """,
                    *codegenScope
                )
            }
        }
    }

    private fun RustWriter.parseStringInner(shape: StringShape, provider: RustWriter.() -> Unit) {
        val enumTrait = shape.getTrait(EnumTrait::class.java).orElse(null)
        if (enumTrait == null) {
            provider()
            rust(".to_string()")
        } else {
            val enumSymbol = symbolProvider.toSymbol(shape)
            withBlock("#T::from(", ")", enumSymbol) {
                provider()
            }
        }
    }

    private fun RustWriter.parseString(shape: StringShape, ctx: Ctx) {
        parseStringInner(shape) {
            rustTemplate("#{expect_data}(&mut ${ctx.tag})?", *codegenScope)
        }
    }

    private fun Shape.xmlName(): XmlName {
        val override = this.getMemberTrait(model, XmlNameTrait::class.java).orNull()
        return override?.let {
            val split = it.value.indexOf(':')
            if (split == -1) {
                XmlName(local = it.value, prefix = null)
            } else {
                XmlName(it.value.substring(split + 1), prefix = it.value.substring(0, split))
            }
        } ?: XmlName(local = this.asMemberShape().map { it.memberName }.orElse(this.id.name), prefix = null)
    }

    fun XmlName.compareTo(start_el: String) =
        "$start_el.matches(${this.toString().dq()})"

    data class XmlIndex(val dataMembers: List<MemberShape>, val attributeMembers: List<MemberShape>) {
        companion object {
            fun fromMembers(members: List<MemberShape>): XmlIndex {
                val (attribute, data) = members.partition { it.hasTrait(XmlAttributeTrait::class.java) }
                return XmlIndex(data, attribute)
            }
        }
    }

    private fun OperationShape.operationXmlMembers(): XmlIndex {
        val outputShape = this.outputShape(model)
        val documentMembers =
            index.getResponseBindings(operationShape).filter { it.value.location == HttpBinding.Location.DOCUMENT }
                .keys.map { outputShape.expectMember(it) }
        return XmlIndex.fromMembers(documentMembers)
    }

    private fun StructureShape.xmlMembers(): XmlIndex {
        return XmlIndex.fromMembers(this.members().toList())
    }

    private fun RustWriter.parseAttributeMember(memberShape: MemberShape, ctx: Ctx) {
        val target = model.expectShape(memberShape.target)
        val symbol = symbolProvider.toSymbol(memberShape)
        conditionalBlock("Some(", ")", symbol.isOptional()) {
            rustBlock("") {
                rustTemplate(
                    """let s = ${ctx.tag}
                    .start_el()
                    .attr(${memberShape.xmlName().toString().dq()})
                    .ok_or(#{XmlError}::Other { msg: "attribute missing"})?;""",
                    *codegenScope
                )
                when (target) {
                    is StringShape -> parseStringInner(target) {
                        rust("s")
                    }
                    else -> rust("todo!(${escape(target.toString()).dq()})")
                }
            }
        }
    }

    private fun RustWriter.parseStructure(shape: StructureShape, ctx: Ctx) {
        val fnName = shape.id.name.toString().toSnakeCase() + "_inner"
        val symbol = symbolProvider.toSymbol(shape)
        val nestedParser = RuntimeType.forInlineFun(fnName, "xml_deser") {
            it.rustBlockT(
                "pub fn $fnName(mut decoder: &mut #{ScopedDecoder}) -> Result<#{Shape}, #{XmlError}>",
                *codegenScope, "Shape" to symbol
            ) {
                rustTemplate(
                    """
                    let mut builder = #{Shape}::builder();
                """,
                    *codegenScope, "Shape" to symbol
                )
                val members = shape.xmlMembers()
                members.attributeMembers.forEach { member ->
                    val temp = safeName("attrib")
                    withBlock("let $temp = ", ";") {
                        parseAttributeMember(member, Ctx("decoder", null))
                    }
                    rust("builder = builder.${member.setterName()}($temp)")
                }
                parseLoop(Ctx("decoder", null)) { ctx: Ctx ->
                    members.dataMembers.forEach { member ->
                        val temp = safeName()
                        rustBlock("s if ${member.xmlName().compareTo("s")} => ") {
                            withBlock("let $temp = ", ";") {
                                parseMember(
                                    member,
                                    ctx.copy(currentTarget = "builder.${symbolProvider.toMemberName(member)}.take()")
                                )
                            }
                            rust("builder = builder.${member.setterName()}($temp);")
                        }
                    }
                }
                withBlock("Ok(builder.build()", ")") {
                    if (StructureGenerator.fallibleBuilder(shape, symbolProvider)) {
                        rust(""".map_err(|_|{XmlError}::Other { msg: "missing field"})?""")
                    }
                }
            }
        }
        rust("#T(&mut ${ctx.tag})?", nestedParser)
    }
}

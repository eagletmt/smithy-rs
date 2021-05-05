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
import software.amazon.smithy.model.traits.XmlFlattenedTrait
import software.amazon.smithy.model.traits.XmlNameTrait
import software.amazon.smithy.rust.codegen.rustlang.CargoDependency
import software.amazon.smithy.rust.codegen.rustlang.RustType
import software.amazon.smithy.rust.codegen.rustlang.RustWriter
import software.amazon.smithy.rust.codegen.rustlang.asType
import software.amazon.smithy.rust.codegen.rustlang.conditionalBlock
import software.amazon.smithy.rust.codegen.rustlang.rust
import software.amazon.smithy.rust.codegen.rustlang.rustBlock
import software.amazon.smithy.rust.codegen.rustlang.rustBlockT
import software.amazon.smithy.rust.codegen.rustlang.rustTemplate
import software.amazon.smithy.rust.codegen.rustlang.withBlock
import software.amazon.smithy.rust.codegen.smithy.RuntimeType
import software.amazon.smithy.rust.codegen.smithy.generators.ProtocolConfig
import software.amazon.smithy.rust.codegen.smithy.generators.builderSymbol
import software.amazon.smithy.rust.codegen.smithy.generators.setterName
import software.amazon.smithy.rust.codegen.smithy.traits.SyntheticOutputTrait
import software.amazon.smithy.rust.codegen.util.dq
import software.amazon.smithy.rust.codegen.util.expectMember
import software.amazon.smithy.rust.codegen.util.orNull
import software.amazon.smithy.rust.codegen.util.outputShape
import software.amazon.smithy.rust.codegen.util.toSnakeCase

class RestXmlParserGenerator(private val operationShape: OperationShape, protocolConfig: ProtocolConfig) {

    data class XmlName(val local: String, val prefix: String? = null)

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
                    if !(${shapeName.compareTo("decoder.start_el()")}) {
                        println!("{:?}", decoder.start_el());
                        return Err(#{XmlError}::Other { msg: "invalid root shape; expected $shapeName / ${operationShape.xmlName()}" })
                    }
                    """,
                    *codegenScope, "parse" to structureParser(shape)
                )
                rustBlockT("while let Some(start_el) = #{next_start_element}(&mut decoder)", *codegenScope) {
                    operationShape.xmlMembers().forEach { member ->
                        val memberName = member.xmlName()
                        rustBlock("if ${memberName.compareTo("start_el")}") {
                            val temp = safeName()
                            withBlock("let $temp = ", ";") {
                                parseMember(member, "builder.${symbolProvider.toMemberName(member)}.take()")
                            }
                            rust("builder = builder.${member.setterName()}($temp);")
                            rust("continue")
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

    private fun RustWriter.parseMember(memberShape: MemberShape, current: String) {
        val target = model.expectShape(memberShape.target)
        conditionalBlock("Some(", ")", memberShape.isOptional) {
            when (target) {
                is StringShape -> parseString(target)
                is MapShape -> if (memberShape.isFlattened()) {
                    parseFlatMap(target, current)
                } else {
                    parseMap(target, current)
                }
                else -> rust("todo!()")
            }
        }
    }

    private fun RustWriter.parseMap(target: MapShape, current: String) {
        val fnName = "deserialize_${target.value.id.name.toSnakeCase()}"
        val mapParser = RuntimeType.forInlineFun(fnName, "inerineriner") {
            it.rustBlockT(
                "pub fn $fnName(mut decoder: &mut #{ScopedDecoder}) -> Result<#{Map}, #{XmlError}>",
                *codegenScope,
                "Map" to symbolProvider.toSymbol(target)
            ) {
                rust("let mut out = #T::new();", RustType.HashMap.RuntimeType)
                rustBlockT("while let Some(start_el) = #{next_start_element}(&mut decoder)") {
                    rustBlock("if ${XmlName(local = "entry").compareTo("start_el")}") {
                        rust("#T(&mut decoder.scoped_to(start_el), &mut out)?;", mapEntryParser(target))
                    }
                }
                rust("Ok(out)")
            }
        }
        rust("#T(&mut decoder.scoped_to(start_el))?", mapParser)
    }

    private fun RustWriter.parseFlatMap(target: MapShape, current: String) {
        val map = safeName("map")
        val entryDecoder = mapEntryParser(target)
        rust(
            """{
            let mut $map = $current.unwrap_or_default();
            let mut entry_decoder = decoder.scoped_to(start_el);
            #T(&mut entry_decoder, &mut $map)?;
            $map
            }
            """,
            entryDecoder
        )
    }

    private fun mapEntryParser(
        target: MapShape
    ): RuntimeType {

        val fnName = target.value.id.name.toSnakeCase() + "_entry"
        return RuntimeType.forInlineFun(fnName, "xml_inner_innerer_ser") {
            it.rustBlockT(
                "pub fn $fnName(mut decoder: &mut #{ScopedDecoder}, out: &mut #{Map}) -> Result<(), #{XmlError}>",
                *codegenScope,
                "Map" to symbolProvider.toSymbol(target)
            ) {
                rust("let mut k: Option<String> = None;")
                rust("let mut v: Option<#T> = None;", symbolProvider.toSymbol(model.expectShape(target.value.target)))
                rustBlockT("while let Some(start_el) = #{next_start_element}(&mut decoder)") {
                    rustBlock("if ${target.key.xmlName().compareTo("start_el")}") {
                        withBlock("k = ", ";") {
                            parseMember(target.key, "ERROR")
                        }
                    }
                    rustBlock("if ${target.value.xmlName().compareTo("start_el")}") {
                        withBlock("v = ", ";") {
                            parseMember(target.value, "v")
                        }
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

    private fun RustWriter.parseString(shape: StringShape) {
        val enumTrait = shape.getTrait(EnumTrait::class.java).orElse(null)
        if (enumTrait == null) {
            rustTemplate("#{expect_data}(&mut(decoder))?.to_string()", *codegenScope)
        } else {
            val enumSymbol = symbolProvider.toSymbol(shape)
            rustTemplate("#{Enum}::from(#{expect_data}(&mut(decoder))?)", *codegenScope, "Enum" to enumSymbol)
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

    fun XmlName.compareTo(v: String) =
        "&$v.name.local == &${local.dq()} && &$v.name.prefix == &${prefix.orEmpty().dq()}"

    private fun OperationShape.xmlMembers(): List<MemberShape> {
        val docMembers =
            index.getResponseBindings(operationShape).filter { it.value.location == HttpBinding.Location.DOCUMENT }
        val outputShape = this.outputShape(model)
        return docMembers.keys.map { outputShape.expectMember(it) }
    }

    fun structureParser(shape: StructureShape): RuntimeType {
        val fnName = shape.id.name.toString().toSnakeCase() + "_inner"
        val scopedDecoder = smithyXml.member("decode::ScopedDecoder")
        return RuntimeType.forInlineFun(fnName, "xml_deser_inner") {
            it.rustBlock(
                "pub fn $fnName(mut decoder: &mut #1T) -> Result<#2T, #3T>",
                scopedDecoder,
                shape.builderSymbol(symbolProvider),
                xmlError
            ) {
                val shapeName = shape.xmlName()
                rustTemplate(
                    """
                    let mut builder = builder;
                    let start_el = decoder.start_el();
                    if !(${shapeName.compareTo("start_el")}) {
                        return Err(#{XmlError}::Other { msg: "invalid root shape" })
                    }
                """,
                    *codegenScope
                )
                rustBlockT("while let Some(start_el) = #{next_start_element}(&mut decoder)", *codegenScope) {
                    shape.members().forEach { member ->
                        println("$member, ${member.xmlName()}")
                        val memberName = member.xmlName()
                        rustBlock("if ${memberName.compareTo("start_el")}") {
                            rust("todo!()")
                        }
                    }
                }
                rust("builder.build().unwrap()")
            }
        }
    }
}

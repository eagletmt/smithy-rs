/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.rust.codegen.smithy.generators.http

import software.amazon.smithy.model.knowledge.HttpBinding
import software.amazon.smithy.model.knowledge.HttpBindingIndex
import software.amazon.smithy.model.shapes.BlobShape
import software.amazon.smithy.model.shapes.CollectionShape
import software.amazon.smithy.model.shapes.DocumentShape
import software.amazon.smithy.model.shapes.MapShape
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.StringShape
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.shapes.UnionShape
import software.amazon.smithy.model.traits.EnumTrait
import software.amazon.smithy.model.traits.MediaTypeTrait
import software.amazon.smithy.model.traits.TimestampFormatTrait
import software.amazon.smithy.rust.codegen.rustlang.CargoDependency
import software.amazon.smithy.rust.codegen.rustlang.RustType
import software.amazon.smithy.rust.codegen.rustlang.RustWriter
import software.amazon.smithy.rust.codegen.rustlang.asType
import software.amazon.smithy.rust.codegen.rustlang.render
import software.amazon.smithy.rust.codegen.rustlang.rust
import software.amazon.smithy.rust.codegen.rustlang.rustBlock
import software.amazon.smithy.rust.codegen.rustlang.rustTemplate
import software.amazon.smithy.rust.codegen.rustlang.stripOuter
import software.amazon.smithy.rust.codegen.rustlang.withBlock
import software.amazon.smithy.rust.codegen.smithy.RuntimeType
import software.amazon.smithy.rust.codegen.smithy.generators.ProtocolConfig
import software.amazon.smithy.rust.codegen.smithy.makeOptional
import software.amazon.smithy.rust.codegen.smithy.protocols.HttpBindingDescriptor
import software.amazon.smithy.rust.codegen.smithy.protocols.HttpLocation
import software.amazon.smithy.rust.codegen.smithy.rustType
import software.amazon.smithy.rust.codegen.util.dq
import software.amazon.smithy.rust.codegen.util.hasTrait
import software.amazon.smithy.rust.codegen.util.isStreaming
import software.amazon.smithy.rust.codegen.util.toSnakeCase

class ResponseBindingGenerator(protocolConfig: ProtocolConfig, private val operationShape: OperationShape) {
    private val runtimeConfig = protocolConfig.runtimeConfig
    private val symbolProvider = protocolConfig.symbolProvider
    private val model = protocolConfig.model
    private val service = protocolConfig.serviceShape
    private val index = HttpBindingIndex.of(model)
    private val headerUtil = CargoDependency.SmithyHttp(runtimeConfig).asType().member("header")
    private val defaultTimestampFormat = TimestampFormatTrait.Format.EPOCH_SECONDS
    private val instant = RuntimeType.Instant(runtimeConfig).toSymbol().rustType()

    /**
     * Generate a function to deserialize [binding] from HTTP headers
     *
     * The name of the resulting function is returned as a String
     *
     * Generates a function like:
     * ```rust
     * fn parse_foo(headers: &http::HeaderMap) -> Result<Option<String>, ParseError> {
     *   ...
     * }
     * ```
     */
    fun generateDeserializeHeaderFn(binding: HttpBindingDescriptor): RuntimeType {
        check(binding.location == HttpLocation.HEADER)
        val outputT = symbolProvider.toSymbol(binding.member).makeOptional()
        val fnName = "deser_header_${fnName(operationShape, binding)}"
        return RuntimeType.forInlineFun(fnName, "http_serde") { writer ->
            writer.rustBlock(
                "pub fn $fnName(header_map: &#T::HeaderMap) -> std::result::Result<#T, #T::ParseError>",
                RuntimeType.http,
                outputT,
                headerUtil
            ) {
                rust("let headers = header_map.get_all(${binding.locationName.dq()}).iter();")
                deserializeFromHeader(model.expectShape(binding.member.target), binding.member)
            }
        }
    }

    fun generateDeserializePrefixHeaderFn(binding: HttpBindingDescriptor): RuntimeType {
        check(binding.location == HttpBinding.Location.PREFIX_HEADERS)
        val outputT = symbolProvider.toSymbol(binding.member)
        check(outputT.rustType().stripOuter<RustType.Option>() is RustType.HashMap) { outputT.rustType() }
        val target = model.expectShape(binding.member.target)
        check(target is MapShape)
        val fnName = "deser_prefix_header_${fnName(operationShape, binding)}"
        val inner = RuntimeType.forInlineFun("${fnName}_inner", "http_serde") {
            it.rustBlock(
                "pub fn ${fnName}_inner(headers: #T::header::ValueIter<http::HeaderValue>) -> std::result::Result<Option<#T>, #T::ParseError>",
                RuntimeType.http,
                symbolProvider.toSymbol(model.expectShape(target.value.target)),
                headerUtil
            ) {
                deserializeFromHeader(model.expectShape(target.value.target), binding.member)
            }
        }
        return RuntimeType.forInlineFun(fnName, "http_serde") { writer ->
            writer.rustBlock(
                "pub fn $fnName(header_map: &#T::HeaderMap) -> std::result::Result<#T, #T::ParseError>",
                RuntimeType.http,
                outputT,
                headerUtil
            ) {
                rust(
                    """
                    let headers = #T::headers_for_prefix(&header_map, ${binding.locationName.dq()});
                    let out: std::result::Result<_, _> = headers.map(|(key, header_name)| {
                        let values = header_map.get_all(header_name);
                        #T(values.iter()).map(|v| (key.to_string(), v.unwrap()))
                    }).collect();
                    out.map(Some)
                    """,
                    headerUtil, inner
                )
            }
        }
    }

    /**
     * Generate a function to deserialize `[binding]` from the response payload
     */
    fun generateDeserializePayloadFn(
        binding: HttpBindingDescriptor,
        errorT: RuntimeType,
        // Deserialize a single structure or union member marked as a payload
        structuredHandler: RustWriter.(String) -> Unit,
        // Deserialize a document type marked as a payload
        docHandler: RustWriter.(String) -> Unit
    ): RuntimeType {
        check(binding.location == HttpBinding.Location.PAYLOAD)
        val outputT = symbolProvider.toSymbol(binding.member)
        val fnName = "deser_payload_${fnName(operationShape, binding)}"
        return RuntimeType.forInlineFun(fnName, "http_serde") { rustWriter ->
            if (binding.member.isStreaming(model)) {
                rustWriter.rustBlock(
                    "pub fn $fnName(body: &mut #T) -> std::result::Result<#T, #T>",
                    RuntimeType.sdkBody(runtimeConfig),
                    outputT,
                    errorT
                ) {
                    deserializeStreamingBody(binding)
                }
            } else {
                rustWriter.rustBlock("pub fn $fnName(body: &[u8]) -> std::result::Result<#T, #T>", outputT, errorT) {
                    deserializePayloadBody(
                        binding,
                        errorT,
                        structuredHandler = structuredHandler,
                        docShapeHandler = docHandler
                    )
                }
            }
        }
    }

    private fun RustWriter.deserializeStreamingBody(binding: HttpBindingDescriptor) {
        val member = binding.member
        val targetShape = model.expectShape(member.target)
        check(targetShape is BlobShape)
        rustTemplate(
            """
            // replace the body with an empty body
            let body = std::mem::replace(body, #{sdk_body}::taken());
            Ok(#{byte_stream}::new(body))
            """,
            "byte_stream" to RuntimeType.byteStream(runtimeConfig), "sdk_body" to RuntimeType.sdkBody(runtimeConfig)
        )
    }

    private fun RustWriter.deserializePayloadBody(
        binding: HttpBindingDescriptor,
        errorSymbol: RuntimeType,
        structuredHandler: RustWriter.(String) -> Unit,
        docShapeHandler: RustWriter.(String) -> Unit
    ) {
        val member = binding.member
        val targetShape = model.expectShape(member.target)
        // There is an unfortunate bit of dual behavior caused by an empty body causing the output to be `None` instead
        // of an empty instance of the response type.
        withBlock("(!body.is_empty()).then(||{", "}).transpose()") {
            when (targetShape) {
                is StructureShape, is UnionShape -> this.structuredHandler("body")
                is StringShape -> {
                    rustTemplate(
                        "let body_str = std::str::from_utf8(&body).map_err(#{error_symbol}::unhandled)?;",
                        "error_symbol" to errorSymbol
                    )
                    if (targetShape.hasTrait<EnumTrait>()) {
                        rust(
                            "Ok(#T::from(body_str))",
                            symbolProvider.toSymbol(targetShape)
                        )
                    } else {
                        rust("Ok(body_str.to_string())")
                    }
                }
                is BlobShape -> rust(
                    "Ok(#T::new(body))",
                    RuntimeType.Blob(runtimeConfig)
                )
                is DocumentShape -> this.docShapeHandler("body")
                else -> TODO("unexpected shape: $targetShape")
            }
        }
    }

    /** Parse a value from a header
     * This function produces an expression which produces the precise output type required by the output shape
     */
    private fun RustWriter.deserializeFromHeader(targetType: Shape, memberShape: MemberShape) {
        val rustType = symbolProvider.toSymbol(targetType).rustType().stripOuter<RustType.Option>()
        val (coreType, coreShape) = if (targetType is CollectionShape) {
            rustType.stripOuter<RustType.Container>() to model.expectShape(targetType.member.target)
        } else {
            rustType to targetType
        }
        val parsedValue = safeName()
        if (coreType == instant) {
            val timestampFormat =
                index.determineTimestampFormat(
                    memberShape,
                    HttpBinding.Location.HEADER,
                    defaultTimestampFormat
                )
            val timestampFormatType = RuntimeType.TimestampFormat(runtimeConfig, timestampFormat)
            rust(
                "let $parsedValue: Vec<${coreType.render(true)}> = #T::many_dates(headers, #T)?;",
                headerUtil,
                timestampFormatType
            )
        } else {
            rust(
                "let $parsedValue: Vec<${coreType.render(true)}> = #T::read_many(headers)?;",
                headerUtil
            )
            if (coreShape.hasTrait<MediaTypeTrait>()) {
                rustTemplate(
                    """let $parsedValue: std::result::Result<Vec<_>, _> = $parsedValue
                        .iter().map(|s|
                            #{base_64_decode}(s).map_err(|_|#{header}::ParseError)
                            .and_then(|bytes|String::from_utf8(bytes).map_err(|_|#{header}::ParseError))
                        ).collect();""",
                    "base_64_decode" to RuntimeType.Base64Decode(runtimeConfig),
                    "header" to headerUtil
                )
                rust("let $parsedValue = $parsedValue?;")
            }
        }
        // TODO: this doesn't support non-optional vectors (which may be eventually added)
        when (rustType) {
            is RustType.Vec ->
                rust(
                    """
                    Ok(if !$parsedValue.is_empty() {
                        Some($parsedValue)
                    } else {
                        None
                    })
                    """
                )
            is RustType.HashSet ->
                rust(
                    """
                    Ok(if !$parsedValue.is_empty() {
                        Some($parsedValue.into_iter().collect())
                    } else {
                        None
                    })
                    """
                )
            else -> rustTemplate(
                """
                if $parsedValue.len() > 1 {
                    Err(#{header_util}::ParseError)
                } else {
                    let mut $parsedValue = $parsedValue;
                    Ok($parsedValue.pop())
                }
                """,
                "header_util" to headerUtil
            )
        }
    }

    /**
     * Generate a unique name for the deserializer function for a given operationShape -> member pair
     */
    // rename here technically not required, operations and members cannot be renamed
    private fun fnName(operationShape: OperationShape, binding: HttpBindingDescriptor) =
        "${operationShape.id.getName(service).toSnakeCase()}_${binding.memberName.toSnakeCase()}"
}

/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.rust.codegen.smithy.protocols

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.model.shapes.BlobShape
import software.amazon.smithy.model.shapes.DocumentShape
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.StringShape
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.shapes.UnionShape
import software.amazon.smithy.model.traits.EnumTrait
import software.amazon.smithy.model.traits.ErrorTrait
import software.amazon.smithy.model.traits.TimestampFormatTrait
import software.amazon.smithy.rust.codegen.rustlang.Attribute
import software.amazon.smithy.rust.codegen.rustlang.RustWriter
import software.amazon.smithy.rust.codegen.rustlang.Writable
import software.amazon.smithy.rust.codegen.rustlang.rust
import software.amazon.smithy.rust.codegen.rustlang.rustBlockTemplate
import software.amazon.smithy.rust.codegen.rustlang.rustTemplate
import software.amazon.smithy.rust.codegen.rustlang.withBlock
import software.amazon.smithy.rust.codegen.rustlang.writable
import software.amazon.smithy.rust.codegen.smithy.RuntimeType
import software.amazon.smithy.rust.codegen.smithy.generators.HttpProtocolGenerator
import software.amazon.smithy.rust.codegen.smithy.generators.ProtocolConfig
import software.amazon.smithy.rust.codegen.smithy.generators.StructureGenerator
import software.amazon.smithy.rust.codegen.smithy.generators.builderSymbol
import software.amazon.smithy.rust.codegen.smithy.generators.error.errorSymbol
import software.amazon.smithy.rust.codegen.smithy.generators.http.RequestBindingGenerator
import software.amazon.smithy.rust.codegen.smithy.generators.http.ResponseBindingGenerator
import software.amazon.smithy.rust.codegen.smithy.generators.operationBuildError
import software.amazon.smithy.rust.codegen.smithy.generators.setterName
import software.amazon.smithy.rust.codegen.smithy.isOptional
import software.amazon.smithy.rust.codegen.smithy.protocols.parse.StructuredDataParserGenerator
import software.amazon.smithy.rust.codegen.smithy.protocols.serialize.StructuredDataSerializerGenerator
import software.amazon.smithy.rust.codegen.util.dq
import software.amazon.smithy.rust.codegen.util.expectMember
import software.amazon.smithy.rust.codegen.util.hasStreamingMember
import software.amazon.smithy.rust.codegen.util.hasTrait
import software.amazon.smithy.rust.codegen.util.inputShape
import software.amazon.smithy.rust.codegen.util.isStreaming
import software.amazon.smithy.rust.codegen.util.outputShape
import software.amazon.smithy.rust.codegen.util.toSnakeCase

interface Protocol {
    val httpBindingResolver: HttpBindingResolver

    val defaultTimestampFormat: TimestampFormatTrait.Format

    fun structuredDataParser(operationShape: OperationShape): StructuredDataParserGenerator

    fun structuredDataSerializer(operationShape: OperationShape): StructuredDataSerializerGenerator

    /**
     fn parse_generic(response: &Response<Bytes>) -> smithy_types::error::Error
     **/
    fun parseGenericError(operationShape: OperationShape): RuntimeType
}

class HttpBoundProtocolGenerator(
    private val protocolConfig: ProtocolConfig,
    private val protocol: Protocol,
) : HttpProtocolGenerator(protocolConfig) {
    private val symbolProvider = protocolConfig.symbolProvider
    private val model = protocolConfig.model
    private val runtimeConfig = protocolConfig.runtimeConfig
    private val httpBindingResolver = protocol.httpBindingResolver

    private val codegenScope = arrayOf(
        "ParseStrict" to RuntimeType.parseStrict(runtimeConfig),
        "ParseResponse" to RuntimeType.parseResponse(runtimeConfig),
        "Response" to RuntimeType.Http("Response"),
        "Bytes" to RuntimeType.Bytes,
        "SdkBody" to RuntimeType.sdkBody(runtimeConfig),
        "BuildError" to runtimeConfig.operationBuildError()
    )

    override fun RustWriter.body(self: String, operationShape: OperationShape): BodyMetadata {
        val serializerGenerator = protocol.structuredDataSerializer(operationShape)
        val inputShape = operationShape.inputShape(model)
        val payloadMemberName =
            httpBindingResolver.requestMembers(operationShape, HttpLocation.PAYLOAD).firstOrNull()?.memberName
        return if (payloadMemberName == null) {
            serializerGenerator.operationSerializer(operationShape)?.let { serializer ->
                rust(
                    "#T(&self).map_err(|err|#T::SerializationError(err.into()))?",
                    serializer,
                    runtimeConfig.operationBuildError()
                )
            } ?: rustTemplate("#{SdkBody}::from(\"\")", *codegenScope)
            BodyMetadata(takesOwnership = false)
        } else {
            val member = inputShape.expectMember(payloadMemberName)
            serializeViaPayload(member, serializerGenerator)
        }
    }

    private fun RustWriter.serializeViaPayload(
        member: MemberShape,
        serializerGenerator: StructuredDataSerializerGenerator
    ): BodyMetadata {
        val fnName = "ser_payload_${member.container.name.toSnakeCase()}"
        val bodyMetadata: BodyMetadata = RustWriter.root().renderPayload(member, "payload", serializerGenerator)
        val ref = when (bodyMetadata.takesOwnership) {
            true -> ""
            false -> "&"
        }
        val serializer = RuntimeType.forInlineFun(fnName, "operation_ser") {
            it.rustBlockTemplate(
                "pub fn $fnName(payload: $ref #{Member}) -> std::result::Result<#{SdkBody}, #{BuildError}>",
                "Member" to symbolProvider.toSymbol(member),
                *codegenScope
            ) {
                // If this targets a member & the member is None, return an empty vec
                val ref = when (bodyMetadata.takesOwnership) {
                    false -> ".as_ref()"
                    true -> ""
                }

                if (symbolProvider.toSymbol(member).isOptional()) {
                    rustTemplate(
                        """
                        let payload = match payload$ref {
                            Some(t) => t,
                            None => return Ok(#{SdkBody}::from(""))
                        };""",
                        *codegenScope
                    )
                }
                // When the body is a streaming blob it _literally_ is a SdkBody already
                // mute this clippy warning to make the codegen a little simpler
                Attribute.Custom("allow(clippy::useless_conversion)").render(this)
                withBlock("Ok(#T::from(", "))", RuntimeType.sdkBody(runtimeConfig)) {
                    renderPayload(member, "payload", serializerGenerator)
                }
            }
        }
        rust("#T($ref self.${symbolProvider.toMemberName(member)})?", serializer)
        return bodyMetadata
    }

    private fun RustWriter.renderPayload(
        member: MemberShape,
        payloadName: String,
        serializer: StructuredDataSerializerGenerator
    ): BodyMetadata {
        return when (val targetShape = model.expectShape(member.target)) {
            // Write the raw string to the payload
            is StringShape -> {
                if (targetShape.hasTrait<EnumTrait>()) {
                    rust("$payloadName.as_str()")
                } else {
                    rust("""$payloadName.to_string()""")
                }
                BodyMetadata(takesOwnership = false)
            }

            // This works for streaming & non streaming blobs because they both have `into_inner()` which
            // can be converted into an SDK body!
            is BlobShape -> {
                // Write the raw blob to the payload
                rust("$payloadName.into_inner()")
                BodyMetadata(takesOwnership = true)
            }
            is StructureShape, is UnionShape -> {
                // JSON serialize the structure or union targeted
                rust(
                    """#T(&$payloadName).map_err(|err|#T::SerializationError(err.into()))?""",
                    serializer.payloadSerializer(member), runtimeConfig.operationBuildError()
                )
                BodyMetadata(takesOwnership = false)
            }
            is DocumentShape -> {
                rust(
                    "#T(&$payloadName).map_err(|err|#T::SerializationError(err.into()))?",
                    serializer.documentSerializer(),
                    runtimeConfig.operationBuildError()
                )
                BodyMetadata(takesOwnership = false)
            }
            else -> TODO("Unexpected payload target type")
        }
    }

    override fun traitImplementations(operationWriter: RustWriter, operationShape: OperationShape) {
        val outputSymbol = symbolProvider.toSymbol(operationShape.outputShape(model))
        val operationName = symbolProvider.toSymbol(operationShape).name

        // For streaming response bodies, we need to generate a different implementation of the parse traits.
        // These will first offer the streaming input to the parser & potentially read the body into memory
        // if an error occurred or if the streaming parser indicates that it needs the full data to proceed.
        if (operationShape.outputShape(model).hasStreamingMember(model)) {
            with(operationWriter) {
                renderStreamingTraits(operationName, outputSymbol, operationShape)
            }
        } else {
            with(operationWriter) {
                renderNonStreamingTraits(operationName, outputSymbol, operationShape)
            }
        }
    }

    private fun RustWriter.renderNonStreamingTraits(
        operationName: String?,
        outputSymbol: Symbol,
        operationShape: OperationShape
    ) {
        val successCode = httpBindingResolver.httpTrait(operationShape).code
        rustTemplate(
            """
                impl #{ParseStrict} for $operationName {
                    type Output = std::result::Result<#{O}, #{E}>;
                    fn parse(&self, response: &#{Response}<#{Bytes}>) -> Self::Output {
                         if !response.status().is_success() && response.status().as_u16() != $successCode {
                            #{parse_error}(response)
                         } else {
                            #{parse_response}(response)
                         }
                    }
                }""",
            *codegenScope,
            "O" to outputSymbol,
            "E" to operationShape.errorSymbol(symbolProvider),
            "parse_error" to parseError(operationShape),
            "parse_response" to parseResponse(operationShape)
        )
    }

    private fun RustWriter.renderStreamingTraits(
        operationName: String,
        outputSymbol: Symbol,
        operationShape: OperationShape
    ) {
        val successCode = httpBindingResolver.httpTrait(operationShape).code
        rustTemplate(
            """
                    impl #{ParseResponse}<#{SdkBody}> for $operationName {
                        type Output = std::result::Result<#{O}, #{E}>;
                        fn parse_unloaded(&self, response: &mut http::Response<#{SdkBody}>) -> Option<Self::Output> {
                            // This is an error, defer to the non-streaming parser
                            if !response.status().is_success() && response.status().as_u16() != $successCode {
                                return None;
                            }
                            Some(#{parse_streaming_response}(response))
                        }
                        fn parse_loaded(&self, response: &http::Response<#{Bytes}>) -> Self::Output {
                            // if streaming, we only hit this case if its an error
                            #{parse_error}(response)
                        }
                    }
                """,
            "O" to outputSymbol,
            "E" to operationShape.errorSymbol(symbolProvider),
            "parse_streaming_response" to parseStreamingResponse(operationShape),
            "parse_error" to parseError(operationShape),
            *codegenScope
        )
    }

    private fun parseError(operationShape: OperationShape): RuntimeType {
        val fnName = "parse_${operationShape.id.name.toSnakeCase()}_error"
        val outputShape = operationShape.outputShape(model)
        val outputSymbol = symbolProvider.toSymbol(outputShape)
        val errorSymbol = operationShape.errorSymbol(symbolProvider)
        return RuntimeType.forInlineFun(fnName, "operation_deser") {
            Attribute.Custom("allow(clippy::unnecessary_wraps)").render(it)
            it.rustBlockTemplate(
                "pub fn $fnName(response: &#{Response}<#{Bytes}>) -> std::result::Result<#{O}, #{E}>",
                *codegenScope,
                "O" to outputSymbol,
                "E" to errorSymbol
            ) {

                rust(
                    "let generic = #T(&response).map_err(#T::unhandled)?;",
                    protocol.parseGenericError(operationShape),
                    errorSymbol
                )
                if (operationShape.errors.isNotEmpty()) {
                    rustTemplate(
                        """
                        let error_code = match generic.code() {
                            Some(code) => code,
                            None => return Err(#{error_symbol}::unhandled(generic))
                        };""",
                        "error_symbol" to errorSymbol,
                    )
                    withBlock("Err(match error_code {", "})") {
                        operationShape.errors.forEach { error ->
                            val errorShape = model.expectShape(error, StructureShape::class.java)
                            val variantName = symbolProvider.toSymbol(model.expectShape(error)).name
                            withBlock(
                                "${error.name.dq()} => #1T { meta: generic, kind: #1TKind::$variantName({",
                                "})},",
                                errorSymbol
                            ) {
                                renderShapeParser(
                                    operationShape,
                                    errorShape,
                                    httpBindingResolver.errorResponseBindings(errorShape),
                                    errorSymbol
                                )
                            }
                        }
                        rust("_ => #T::generic(generic)", errorSymbol)
                    }
                } else {
                    rust("Err(#T::generic(generic))", errorSymbol)
                }
            }
        }
    }

    private fun parseStreamingResponse(operationShape: OperationShape): RuntimeType {
        val fnName = "parse_${operationShape.id.name.toSnakeCase()}"
        val outputShape = operationShape.outputShape(model)
        val outputSymbol = symbolProvider.toSymbol(outputShape)
        val errorSymbol = operationShape.errorSymbol(symbolProvider)
        return RuntimeType.forInlineFun(fnName, "operation_deser") {
            Attribute.Custom("allow(clippy::unnecessary_wraps)").render(it)
            it.rustBlockTemplate(
                "pub fn $fnName(response: &mut #{Response}<#{SdkBody}>) -> std::result::Result<#{O}, #{E}>",
                *codegenScope,
                "O" to outputSymbol,
                "E" to errorSymbol
            ) {
                withBlock("Ok({", "})") {
                    renderShapeParser(
                        operationShape,
                        outputShape,
                        httpBindingResolver.responseBindings(operationShape),
                        errorSymbol
                    )
                }
            }
        }
    }

    private fun parseResponse(operationShape: OperationShape): RuntimeType {
        val fnName = "parse_${operationShape.id.name.toSnakeCase()}_response"
        val outputShape = operationShape.outputShape(model)
        val outputSymbol = symbolProvider.toSymbol(outputShape)
        val errorSymbol = operationShape.errorSymbol(symbolProvider)
        return RuntimeType.forInlineFun(fnName, "operation_deser") {
            Attribute.Custom("allow(clippy::unnecessary_wraps)").render(it)
            it.rustBlockTemplate(
                "pub fn $fnName(response: &#{Response}<#{Bytes}>) -> std::result::Result<#{O}, #{E}>",
                *codegenScope,
                "O" to outputSymbol,
                "E" to errorSymbol
            ) {
                withBlock("Ok({", "})") {
                    renderShapeParser(
                        operationShape,
                        outputShape,
                        httpBindingResolver.responseBindings(operationShape),
                        errorSymbol
                    )
                }
            }
        }
    }

    override fun toHttpRequestImpl(
        implBlockWriter: RustWriter,
        operationShape: OperationShape,
        inputShape: StructureShape
    ) {
        val httpBindingGenerator = RequestBindingGenerator(
            protocolConfig,
            protocol.defaultTimestampFormat,
            httpBindingResolver,
            operationShape,
            inputShape,
        )
        val contentType = httpBindingResolver.requestContentType(operationShape)
        httpBindingGenerator.renderUpdateHttpBuilder(implBlockWriter)
        httpBuilderFun(implBlockWriter) {
            rust(
                """
                let builder = #T::new();
                let builder = builder.header("Content-Type", ${contentType.dq()});
                self.update_http_builder(builder)
                """,
                RuntimeType.HttpRequestBuilder
            )
        }
    }

    private fun RustWriter.renderShapeParser(
        operationShape: OperationShape,
        outputShape: StructureShape,
        bindings: List<HttpBindingDescriptor>,
        errorSymbol: RuntimeType,
    ) {
        val httpBindingGenerator = ResponseBindingGenerator(protocolConfig, operationShape)
        val structuredDataParser = protocol.structuredDataParser(operationShape)
        Attribute.AllowUnusedMut.render(this)
        rust("let mut output = #T::default();", outputShape.builderSymbol(symbolProvider))
        // avoid non-usage warnings for response
        rust("let _ = response;")
        if (outputShape.id == operationShape.output.get()) {
            structuredDataParser.operationParser(operationShape)?.also { parser ->
                rust(
                    "output = #T(response.body().as_ref(), output).map_err(#T::unhandled)?;",
                    parser,
                    errorSymbol
                )
            }
        } else {
            check(outputShape.hasTrait<ErrorTrait>()) { "should only be called on outputs or errors $outputShape" }
            structuredDataParser.errorParser(outputShape)?.also { parser ->
                rust(
                    "output = #T(response.body().as_ref(), output).map_err(#T::unhandled)?;",
                    parser, errorSymbol
                )
            }
        }
        for (binding in bindings) {
            val member = binding.member
            val parsedValue = renderBindingParser(binding, operationShape, httpBindingGenerator, structuredDataParser)
            if (parsedValue != null) {
                withBlock("output = output.${member.setterName()}(", ");") {
                    parsedValue(this)
                }
            }
        }

        val err = if (StructureGenerator.fallibleBuilder(outputShape, symbolProvider)) {
            ".map_err(|s|${format(errorSymbol)}::unhandled(s))?"
        } else ""
        rust("output.build()$err")
    }

    /**
     * Generate a parser & a parsed value converter for each output member of `operationShape`
     *
     * Returns a map with key = memberName, value = parsedValue
     */
    private fun renderBindingParser(
        binding: HttpBindingDescriptor,
        operationShape: OperationShape,
        httpBindingGenerator: ResponseBindingGenerator,
        structuredDataParser: StructuredDataParserGenerator,
    ): Writable? {
        val errorSymbol = operationShape.errorSymbol(symbolProvider)
        val member = binding.member
        return when (binding.location) {
            HttpLocation.HEADER -> writable {
                val fnName = httpBindingGenerator.generateDeserializeHeaderFn(binding)
                rust(
                    """
                        #T(response.headers())
                            .map_err(|_|#T::unhandled("Failed to parse ${member.memberName} from header `${binding.locationName}"))?
                    """,
                    fnName, errorSymbol
                )
            }
            HttpLocation.DOCUMENT -> {
                // document is handled separately
                null
            }
            HttpLocation.PAYLOAD -> {
                val docShapeHandler: RustWriter.(String) -> Unit = { body ->
                    rust(
                        "#T($body).map_err(#T::unhandled)",
                        structuredDataParser.documentParser(operationShape),
                        errorSymbol
                    )
                }
                val structureShapeHandler: RustWriter.(String) -> Unit = { body ->
                    rust("#T($body).map_err(#T::unhandled)", structuredDataParser.payloadParser(member), errorSymbol)
                }
                val deserializer = httpBindingGenerator.generateDeserializePayloadFn(
                    binding,
                    errorSymbol,
                    docHandler = docShapeHandler,
                    structuredHandler = structureShapeHandler
                )
                return if (binding.member.isStreaming(model)) {
                    writable { rust("Some(#T(response.body_mut())?)", deserializer) }
                } else {
                    writable { rust("#T(response.body().as_ref())?", deserializer) }
                }
            }
            HttpLocation.RESPONSE_CODE -> writable {
                rust("Some(response.status().as_u16() as _)")
            }
            HttpLocation.PREFIX_HEADERS -> {
                val sym = httpBindingGenerator.generateDeserializePrefixHeaderFn(binding)
                writable {
                    rustTemplate(
                        """
                        #{deser}(response.headers())
                             .map_err(|_|
                                #{err}::unhandled("Failed to parse ${member.memberName} from prefix header `${binding.locationName}")
                             )?
                        """,
                        "deser" to sym, "err" to errorSymbol
                    )
                }
            }
            else -> {
                TODO("Unexpected binding location: ${binding.location}")
            }
        }
    }
}

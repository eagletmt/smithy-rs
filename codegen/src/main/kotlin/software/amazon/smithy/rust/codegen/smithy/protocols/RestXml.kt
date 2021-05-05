/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.rust.codegen.smithy.protocols

import software.amazon.smithy.aws.traits.protocols.RestXmlTrait
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.HttpBinding
import software.amazon.smithy.model.knowledge.HttpBindingIndex
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.traits.HttpTrait
import software.amazon.smithy.rust.codegen.rustlang.Attribute
import software.amazon.smithy.rust.codegen.rustlang.RustWriter
import software.amazon.smithy.rust.codegen.rustlang.Writable
import software.amazon.smithy.rust.codegen.rustlang.rust
import software.amazon.smithy.rust.codegen.rustlang.rustBlock
import software.amazon.smithy.rust.codegen.rustlang.rustTemplate
import software.amazon.smithy.rust.codegen.rustlang.withBlock
import software.amazon.smithy.rust.codegen.rustlang.writable
import software.amazon.smithy.rust.codegen.smithy.RuntimeType
import software.amazon.smithy.rust.codegen.smithy.generators.HttpProtocolGenerator
import software.amazon.smithy.rust.codegen.smithy.generators.ProtocolConfig
import software.amazon.smithy.rust.codegen.smithy.generators.ProtocolGeneratorFactory
import software.amazon.smithy.rust.codegen.smithy.generators.ProtocolSupport
import software.amazon.smithy.rust.codegen.smithy.generators.StructureGenerator
import software.amazon.smithy.rust.codegen.smithy.generators.builderSymbol
import software.amazon.smithy.rust.codegen.smithy.generators.error.errorSymbol
import software.amazon.smithy.rust.codegen.smithy.generators.http.RequestBindingGenerator
import software.amazon.smithy.rust.codegen.smithy.generators.http.ResponseBindingGenerator
import software.amazon.smithy.rust.codegen.smithy.generators.setterName
import software.amazon.smithy.rust.codegen.smithy.traits.SyntheticOutputTrait
import software.amazon.smithy.rust.codegen.smithy.transformers.OperationNormalizer
import software.amazon.smithy.rust.codegen.smithy.transformers.RemoveEventStreamOperations
import software.amazon.smithy.rust.codegen.util.hasStreamingMember
import software.amazon.smithy.rust.codegen.util.isStreaming
import software.amazon.smithy.rust.codegen.util.outputShape

class RestXmlFactory : ProtocolGeneratorFactory<RestXmlGenerator> {
    override fun buildProtocolGenerator(protocolConfig: ProtocolConfig): RestXmlGenerator {
        return RestXmlGenerator(protocolConfig)
    }

    override fun transformModel(model: Model): Model {
        return OperationNormalizer(model).transformModel(
            inputBodyFactory = OperationNormalizer.NoBody,
            outputBodyFactory = OperationNormalizer.NoBody
        ).let(RemoveEventStreamOperations::transform)
    }

    override fun support(): ProtocolSupport {
        return ProtocolSupport(
            requestBodySerialization = false,
            responseDeserialization = true,
            errorDeserialization = false
        )
    }
}

class RestXmlGenerator(
    private val protocolConfig: ProtocolConfig
) : HttpProtocolGenerator(protocolConfig) {
    private val symbolProvider = protocolConfig.symbolProvider
    private val model = protocolConfig.model
    private val runtimeConfig = protocolConfig.runtimeConfig
    private val restXml = protocolConfig.serviceShape.expectTrait(RestXmlTrait::class.java)
    private val restXmlErrors: RuntimeType = when (restXml.isNoErrorWrapping) {
        true -> RuntimeType.unwrappedXmlErrors(runtimeConfig)
        false -> RuntimeType.wrappedXmlErrors(runtimeConfig)
    }
    private val sdkBody = RuntimeType.sdkBody(runtimeConfig)
    private val httpIndex = HttpBindingIndex.of(model)

    override fun traitImplementations(operationWriter: RustWriter, operationShape: OperationShape) {
        val outputSymbol = symbolProvider.toSymbol(operationShape.outputShape(model))
        val operationName = symbolProvider.toSymbol(operationShape).name
        // restJson1 requires all operations to use the HTTP trait
        val httpTrait = operationShape.expectTrait(HttpTrait::class.java)

        // For streaming response bodies, we need to generate a different implementation of the parse traits.
        // These will first offer the streaming input to the parser & potentially read the body into memory
        // if an error occurred or if the streaming parser indicates that it needs the full data to proceed.
        if (operationShape.outputShape(model).hasStreamingMember(model)) {
            renderStreamingTraits(operationWriter, operationName, httpTrait, outputSymbol, operationShape)
        } else {
            renderNonStreamingTraits(operationWriter, operationName, httpTrait, outputSymbol, operationShape)
        }
    }

    private fun renderNonStreamingTraits(
        operationWriter: RustWriter,
        operationName: String,
        httpTrait: HttpTrait,
        outputSymbol: Symbol,
        operationShape: OperationShape
    ) {
        operationWriter.rustTemplate(
            // strict (as in "not lazy") is the opposite of streaming
            """
                impl #{ParseStrict} for $operationName {
                    type Output = Result<#{O}, #{E}>;
                    fn parse(&self, response: &#{Response}<#{Bytes}>) -> Self::Output {
                         if #{rest_xml_errors}::is_error(&response) && response.status().as_u16() != ${httpTrait.code} {
                            self.parse_error(response)
                         } else {
                            self.parse_response(response)
                         }
                    }
                }""",
            "ParseStrict" to RuntimeType.parseStrict(symbolProvider.config().runtimeConfig),
            "O" to outputSymbol,
            "E" to operationShape.errorSymbol(symbolProvider),
            "Response" to RuntimeType.Http("Response"),
            "Bytes" to RuntimeType.Bytes,
            "rest_xml_errors" to restXmlErrors
        )
    }

    private fun renderStreamingTraits(
        operationWriter: RustWriter,
        operationName: String,
        httpTrait: HttpTrait,
        outputSymbol: Symbol,
        operationShape: OperationShape
    ) {
        operationWriter.rustTemplate(
            """
                    impl #{ParseResponse}<#{SdkBody}> for $operationName {
                        type Output = Result<#{O}, #{E}>;
                        fn parse_unloaded(&self, response: &mut http::Response<#{SdkBody}>) -> Option<Self::Output> {
                            // This is an error, defer to the non-streaming parser
                            if #{rest_xml_errors}::is_error(&response) && response.status().as_u16() != ${httpTrait.code} {
                                return None;
                            }
                            Some(self.parse_response(response))
                        }
                        fn parse_loaded(&self, response: &http::Response<#{Bytes}>) -> Self::Output {
                            // if streaming, we only hit this case if its an error
                            self.parse_error(response)
                        }
                    }
                """,
            "ParseResponse" to RuntimeType.parseResponse(runtimeConfig),
            "O" to outputSymbol,
            "E" to operationShape.errorSymbol(symbolProvider),
            "SdkBody" to sdkBody,
            "Response" to RuntimeType.Http("Response"),
            "Bytes" to RuntimeType.Bytes,
            "json_errors" to RuntimeType.awsJsonErrors(runtimeConfig)
        )
    }

    override fun fromResponseImpl(implBlockWriter: RustWriter, operationShape: OperationShape) {
        val outputShape = operationShape.outputShape(model)
        val bodyId = outputShape.expectTrait(SyntheticOutputTrait::class.java).body
        val errorSymbol = operationShape.errorSymbol(symbolProvider)

        /* Render two functions:
            - An error parser `self.parse_error`
            - A happy-path parser: `Self::parse_response`
         */
        implBlockWriter.renderParseError(operationShape, errorSymbol)
        fromResponseFun(implBlockWriter, operationShape) {
            rust("let _ = response;")
            withBlock("Ok({", "})") {
                renderShapeParser(
                    operationShape,
                    outputShape,
                    httpIndex.getResponseBindings(operationShape),
                    errorSymbol
                )
            }
        }
    }

    private fun RustWriter.renderParseError(
        operationShape: OperationShape,
        errorSymbol: RuntimeType
    ) {
        rustBlock(
            "fn parse_error(&self, _response: &http::Response<#T>) -> Result<#T, #T>",
            RuntimeType.Bytes,
            symbolProvider.toSymbol(operationShape.outputShape(model)),
            errorSymbol
        ) {
            rust("todo!()")
            /*
            rustTemplate(
                """
                        let body = #{sj}::from_slice(response.body().as_ref())
                            .unwrap_or_else(|_|#{sj}::json!({}));
                        let generic = #{aws_json_errors}::parse_generic_error(&response, &body);
                        """,
                "aws_json_errors" to jsonErrors, "sj" to RuntimeType.SJ
            )
            if (operationShape.errors.isNotEmpty()) {
                rustTemplate(
                    """

                        let error_code = match generic.code() {
                            Some(code) => code,
                            None => return Err(#{error_symbol}::unhandled(generic))
                        };""",
                    "error_symbol" to errorSymbol
                )
                withBlock("Err(match error_code {", "})") {
                    // approx:
                    /*
                            match error_code {
                                "Code1" => deserialize<Code1>(body),
                                "Code2" => deserialize<Code2>(body)
                            }
                         */
                    parseErrorVariants(operationShape, errorSymbol)
                }
            } else {
                rust("Err(#T::generic(generic))", errorSymbol)
            }
        }
    */
        }
    }

    override fun toBodyImpl(
        implBlockWriter: RustWriter,
        inputShape: StructureShape,
        inputBody: StructureShape?,
        operationShape: OperationShape
    ) {
        bodyBuilderFun(implBlockWriter) {
            rust("Default::default()")
        }
    }

    override fun toHttpRequestImpl(
        implBlockWriter: RustWriter,
        operationShape: OperationShape,
        inputShape: StructureShape
    ) {
        val httpTrait = operationShape.expectTrait(HttpTrait::class.java)

        val httpBindingGenerator = RequestBindingGenerator(
            model,
            symbolProvider,
            runtimeConfig,
            implBlockWriter,
            operationShape,
            inputShape,
            httpTrait
        )
        val contentType =
            httpIndex.determineRequestContentType(operationShape, "application/json").orElse("application/json")
        httpBindingGenerator.renderUpdateHttpBuilder(implBlockWriter)
        httpBuilderFun(implBlockWriter) {
            rust("todo!()")
            /*rust(
                """
            let builder = #T::new();
            let builder = builder.header("Content-Type", ${contentType.dq()});
            self.update_http_builder(builder)
            """,
                requestBuilder
            )*/
        }
    }

    /**
     * Generate a parser for [outputShape] given [bindings].
     *
     * The generated code is an expression with a return type of Result<[outputShape], [errorSymbol]> and can be
     * used for either error shapes or output shapes.
     */
    private fun RustWriter.renderShapeParser(
        operationShape: OperationShape,
        outputShape: StructureShape,
        bindings: Map<String, HttpBinding>,
        errorSymbol: RuntimeType,
    ) {
        val httpBindingGenerator = ResponseBindingGenerator(protocolConfig, operationShape)
        Attribute.AllowUnusedMut.render(this)
        rust("let mut output = #T::default();", outputShape.builderSymbol(symbolProvider))
        outputShape.members().forEach { member ->
            val parsedValue = renderBindingParser(
                bindings[member.memberName]!!,
                operationShape,
                httpBindingGenerator,
            )

            if (parsedValue != null) {
                withBlock("output = output.${member.setterName()}(", ");") {
                    parsedValue(this)
                }
            }
        }
        if (bindings.values.find { it.location == HttpBinding.Location.DOCUMENT } != null) {
            rust("output = #T(response.body().as_ref(), output).unwrap();", RestXmlParserGenerator(operationShape, protocolConfig).operationParser())
        }

        val err = if (StructureGenerator.fallibleBuilder(outputShape, symbolProvider)) {
            ".map_err(|s|${format(errorSymbol)}::unhandled(s))?"
        } else ""
        rust("output.build()$err")
    }

    private fun renderBindingParser(
        binding: HttpBinding,
        operationShape: OperationShape,
        httpBindingGenerator: ResponseBindingGenerator,
    ): Writable? {
        val errorSymbol = operationShape.errorSymbol(symbolProvider)
        val member = binding.member
        return when (binding.location) {
            HttpBinding.Location.HEADER -> writable {
                val fnName = httpBindingGenerator.generateDeserializeHeaderFn(binding)
                rust(
                    """
                        #T(response.headers())
                            .map_err(|_|#T::unhandled("Failed to parse ${member.memberName} from header `${binding.locationName}"))?
                        """,
                    fnName, errorSymbol
                )
            }
            HttpBinding.Location.DOCUMENT -> {
                null
            }
            HttpBinding.Location.PAYLOAD -> {
                val docShapeHandler: RustWriter.(String) -> Unit = { body ->
                    TODO("document types unsupported in restXML")
                }
                val structureShapeHandler: RustWriter.(String) -> Unit = { body ->
                    val parser = RestXmlParserGenerator(operationShape, protocolConfig).operationParser()
                    rust("output")
                }
                val deserializer = httpBindingGenerator.generateDeserializePayloadFn(
                    binding,
                    errorSymbol,
                    docHandler = docShapeHandler,
                    structuredHandler = structureShapeHandler
                )
                return null
                return if (binding.member.isStreaming(model)) {
                    writable { rust("#T(response.body_mut())?", deserializer) }
                } else {
                    writable { rust("#T(response.body().as_ref())?", deserializer) }
                }
            }
            HttpBinding.Location.RESPONSE_CODE -> writable("Some(response.status().as_u16() as _)")
            HttpBinding.Location.PREFIX_HEADERS -> {
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
                // logger.warning("Unhandled response binding type: ${binding.location}")
                TODO("Unexpected binding location: ${binding.location}")
            }
        }
    }
}

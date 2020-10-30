package software.amazon.smithy.rust.codegen.smithy.generators

import java.util.Optional
import software.amazon.smithy.model.node.ArrayNode
import software.amazon.smithy.model.node.BooleanNode
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.node.NumberNode
import software.amazon.smithy.model.node.ObjectNode
import software.amazon.smithy.model.node.StringNode
import software.amazon.smithy.model.shapes.BlobShape
import software.amazon.smithy.model.shapes.ListShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.StringShape
import software.amazon.smithy.model.shapes.TimestampShape
import software.amazon.smithy.model.traits.EnumTrait
import software.amazon.smithy.protocoltests.traits.HttpRequestTestCase
import software.amazon.smithy.protocoltests.traits.HttpRequestTestsTrait
import software.amazon.smithy.rust.codegen.lang.RustWriter
import software.amazon.smithy.rust.codegen.lang.rustBlock
import software.amazon.smithy.rust.codegen.lang.withBlock
import software.amazon.smithy.rust.codegen.smithy.RuntimeType
import software.amazon.smithy.rust.codegen.util.dq

class HttpProtocolTestGenerator(private val protocolConfig: ProtocolConfig) {
    fun render() {
        with(protocolConfig) {
            operationShape.getTrait(HttpRequestTestsTrait::class.java).map {
                renderHttpRequestTests(it)
            }
        }
    }

    fun renderHttpRequestTests(httpRequestTestsTrait: HttpRequestTestsTrait) {
        with(protocolConfig) {
            writer.write("#[cfg(test)]")
            val operationName = symbolProvider.toSymbol(operationShape).name
            val testModuleName = "${operationName.toSnakeCase()}_request_test"
            writer.withModule(testModuleName) {
                httpRequestTestsTrait.testCases.filter { it.protocol == protocol }.forEach { testCase ->
                    renderHttpRequestTestCase(testCase, this)
                }
            }
        }
    }

    private fun applyBuilder(argument: Node, target: Shape, writer: RustWriter) {
        when (argument) {
            is ObjectNode -> {
                when {
                    target.isStructureShape -> {
                        val targetStruct = target.asStructureShape().get()
                        writer.write("\$T::builder()", protocolConfig.symbolProvider.toSymbol(target))
                        argument.members.forEach { (key, value) ->
                            val func = key.value.toSnakeCase()
                            val member =
                                protocolConfig.model.expectShape(targetStruct.getMember(key.value).get().target)
                            if (!value.isNullNode) {
                                writer.withBlock(".$func(", ")") {
                                    applyBuilder(value, member, this)
                                }
                            }
                        }
                        writer.write(".build()")
                        if (StructureGenerator.fallibleBuilder(targetStruct, protocolConfig.symbolProvider)) {
                            writer.write(".unwrap()")
                        }
                    }
                    target.isUnionShape -> {
                        val targetUnion = target.asUnionShape().get()
                        val unionSymbol = protocolConfig.symbolProvider.toSymbol(targetUnion)
                        check(argument.members.size == 1)
                        val variant = argument.members.iterator().next()
                        val memberName = variant.key.value
                        val member = targetUnion.getMember(memberName).get()
                            .let { protocolConfig.model.expectShape(it.target) }
                        writer.write("\$T::${memberName.toPascalCase()}", unionSymbol)
                        // unions should specify exactly one member
                        writer.withBlock("(", ")") {
                            applyBuilder(variant.value, member, this)
                        }
                    }
                    target.isMapShape -> {
                        writer.rustBlock("") {
                            write("let mut ret = \$T::new();", RuntimeType.HashMap)
                            val valueShape =
                                target.asMapShape().get().value.let { protocolConfig.model.expectShape(it.target) }
                            argument.members.forEach { (k, v) ->
                                withBlock("ret.insert(${k.value.dq()}.to_string(),", ");") {
                                    applyBuilder(v, valueShape, this)
                                }
                            }
                            write("ret")
                        }
                    }
                    else -> writer.write("todo!() /* object node $target */")
                }
            }
            is BooleanNode -> writer.write(argument.value.toString())
            is StringNode -> {
                val value = argument.value
                when (target) {
                    is StringShape -> {
                        target.getTrait(EnumTrait::class.java).map {
                            it.instantiate(writer, protocolConfig.symbolProvider.toSymbol(target), argument)
                        }.or {
                            writer.write("${value.dq()}.to_string()")
                            Optional.empty()
                        }
                    }
                    is BlobShape -> {
                        writer.write(
                            "\$T::new(\$T(${value.dq()}).unwrap())",
                            RuntimeType.Blob(protocolConfig.runtimeConfig),
                            RuntimeType.Base64Decode(protocolConfig.runtimeConfig)
                        )
                    }
                    else -> writer.write("todo!() /* $target */")
                }
            }
            is NumberNode -> {
                when (target) {
                    is TimestampShape ->
                        writer.write(
                            "\$T::from_epoch_seconds(${argument.value})",
                            RuntimeType.Instant(protocolConfig.runtimeConfig)
                        )
                    else -> writer.write(argument.value.toString())
                }
            }
            is ArrayNode -> {
                when (target) {
                    is ListShape -> {
                        val member = protocolConfig.model.expectShape(target.member.target)
                        writer.withBlock("vec![", "]") {
                            argument.elements.forEach {
                                applyBuilder(it, member, this)
                                write(",")
                            }
                        }
                    }
                    else -> writer.write("todo!() /* ArrayNode: $target */")
                }
            }
            else -> writer.write("todo!() /* $argument $target */")
        }
    }

    private fun renderHttpRequestTestCase(httpRequestTestCase: HttpRequestTestCase, testModuleWriter: RustWriter) {
        httpRequestTestCase.documentation.map {
            testModuleWriter.setNewlinePrefix("/// ").write(it).setNewlinePrefix("")
        }
        testModuleWriter.write("/// Location: ${httpRequestTestCase.toNode().sourceLocation}")
        testModuleWriter.write("#[test]")
        testModuleWriter.rustBlock("fn test_${httpRequestTestCase.id.toSnakeCase()}()") {
            write("assert_eq!(true, true);")
            writeInline("let input =")
            applyBuilder(httpRequestTestCase.params, protocolConfig.inputShape, this)
            write(";")
            // TODO: we need a real body :-)
            write("let http_request = input.build_http_request().body(()).unwrap();")
            with(httpRequestTestCase) {
                write(
                    """
                    assert_eq!(http_request.method(), ${method.dq()});
                    assert_eq!(http_request.uri(), ${uri.dq()});
                """
                )
                withBlock("let expected_headers = vec![", "];") {
                    queryParams.joinToString(",") { it.dq() }
                }
                write(
                    "\$T(&http_request, expected_headers.as_slice()).unwrap();",
                    RuntimeType.ProtocolTestHelper(protocolConfig.runtimeConfig, "validate_query_string")
                )
                write("/* BODY:\n $body */")
            }
        }
    }
}

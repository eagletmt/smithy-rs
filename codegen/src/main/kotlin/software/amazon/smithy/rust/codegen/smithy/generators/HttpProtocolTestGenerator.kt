package software.amazon.smithy.rust.codegen.smithy.generators

import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.protocoltests.traits.HttpRequestTestCase
import software.amazon.smithy.protocoltests.traits.HttpRequestTestsTrait
import software.amazon.smithy.rust.codegen.lang.RustWriter
import software.amazon.smithy.rust.codegen.lang.rustBlock

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
            writer.rustBlock("mod $testModuleName") {
                httpRequestTestsTrait.testCases.filter { it.protocol == protocol }.forEach { testCase ->
                    renderHttpRequestTestCase(testCase, this)
                }
            }
        }


    }

    private fun renderHttpRequestTestCase(httpRequestTestCase: HttpRequestTestCase, testModuleWriter: RustWriter) {
        testModuleWriter.write("#[test]")
        testModuleWriter.rustBlock("fn test_${httpRequestTestCase.id.toSnakeCase()}()") {
            write("assert_eq!(true, true);")
        }
    }
}
package software.amazon.smithy.rust.codegen.smithy.generators

import org.junit.jupiter.api.Test
import software.amazon.smithy.aws.traits.protocols.AwsJson1_0Trait
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.rust.codegen.lang.RustWriter
import software.amazon.smithy.rust.testutil.TestRuntimeConfig
import software.amazon.smithy.rust.testutil.asSmithyModel
import software.amazon.smithy.rust.testutil.testSymbolProvider

// TODO: make this work

class HttpProtocolTestGeneratorTest {
    private val model = """
        namespace smithy.example

        use smithy.test#httpRequestTests
        use aws.protocols#awsJson1_0
        
        @awsJson1_0
        service Service {
            version: "2006-03-01",
            operations: [SayHello]
        }

        @http(method: "POST", uri: "/")
        @httpRequestTests([
            {
                id: "say_hello",
                protocol: awsJson1_0,
                params: {
                    "greeting": "Hi",
                    "name": "Teddy",
                    "query": "Hello there"
                },
                method: "POST",
                uri: "/",
                queryParams: [
                    "Hi=Hello%20there"
                ],
                headers: {
                    "X-Greeting": "Hi",
                },
                body: "{\"name\": \"Teddy\"}",
                bodyMediaType: "application/json"
            }
        ])
        operation SayHello {
            input: SayHelloInput
        }

        structure SayHelloInput {
            @httpHeader("X-Greeting")
            greeting: String,

            @httpQuery("Hi")
            query: String,

            name: String
        }
    """.asSmithyModel()
    val protocolConfig = ProtocolConfig(
        model,
        testSymbolProvider(model),
        TestRuntimeConfig,
        RustWriter.forModule("operation"),
        model.eS("smithy.example#Service"),
        model.eS("smithy.example#SayHello"),
        model.eS("smithy.example#SayHelloInput"),
        AwsJson1_0Trait.ID
    )
    @Test
    fun `test protocol test generation`() {
    }
}

inline fun <reified T : Shape> Model.eS(id: String): T {
    return this.expectShape(ShapeId.from(id), T::class.java)
}

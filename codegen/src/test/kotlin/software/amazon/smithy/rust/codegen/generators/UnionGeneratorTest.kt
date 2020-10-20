/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 *
 *
 */

package software.amazon.smithy.rust.codegen.generators

import org.junit.jupiter.api.Test
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.UnionShape
import software.amazon.smithy.model.traits.DocumentationTrait
import software.amazon.smithy.rust.codegen.lang.RustWriter
import software.amazon.smithy.rust.codegen.smithy.SymbolVisitor
import software.amazon.smithy.rust.codegen.smithy.generators.UnionGenerator
import software.amazon.smithy.rust.testutil.shouldCompile
import software.amazon.smithy.rust.testutil.shouldParseAsRust

class UnionGeneratorTest {
    @Test
    fun `generate basic unions`() {
        val member1 = MemberShape.builder()
            .id("com.test#MyUnion\$stringConfig")
            .target("smithy.api#String").build()
        val member2 = MemberShape.builder().id("com.test#MyUnion\$intConfig")
            .target("smithy.api#PrimitiveInteger").addTrait(
            DocumentationTrait("This *is* documentation about the member.")
        ).build()
        // val member3 = MemberShape.builder().id("com.test#MyStruct\$baz").target("smithy.api#Integer").build()

        // struct 2 will be of type `Qux` under `MyStruct::quux` member
        val union = UnionShape.builder()
            .id("com.test#MyUnion")
            .addMember(member1)
            .addMember(member2)
            .build()

        val model = Model.assembler()
            .addShapes(union, member1, member2)
            .assemble()
            .unwrap()
        val provider: SymbolProvider = SymbolVisitor(model, "test")
        val writer = RustWriter("model.rs", "model")
        val generator = UnionGenerator(model, provider, writer, union)
        generator.render()
        val result = writer.toString()
        println(result)
        result.shouldParseAsRust()
        result.shouldCompile()
    }
}

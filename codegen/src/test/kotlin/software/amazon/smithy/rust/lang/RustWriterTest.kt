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

package software.amazon.smithy.rust.lang

import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.SetShape
import software.amazon.smithy.model.shapes.StringShape
import software.amazon.smithy.rust.codegen.lang.RustWriter
import software.amazon.smithy.rust.codegen.lang.withBlock
import software.amazon.smithy.rust.codegen.smithy.SymbolVisitor
import software.amazon.smithy.rust.testutil.quickTest
import software.amazon.smithy.rust.testutil.shouldCompile
import software.amazon.smithy.rust.testutil.shouldMatchResource
import software.amazon.smithy.rust.testutil.shouldParseAsRust

class RustWriterTest {
    @Test
    fun `empty file`() {
        val sut = RustWriter("empty.rs", "")
        sut.toString().shouldParseAsRust()
        sut.toString().shouldCompile()
        sut.toString().shouldMatchResource(javaClass, "empty.rs")
    }

    @Test
    fun `manually created struct`() {
        val sut = RustWriter("lib.rs", "")
        val stringShape = StringShape.builder().id("test#Hello").build()
        val set = SetShape.builder()
            .id("foo.bar#Records")
            .member(stringShape.id)
            .build()
        val model = Model.assembler()
            .addShapes(set, stringShape)
            .assemble()
            .unwrap()

        val provider: SymbolProvider = SymbolVisitor(model, "test")
        val setSymbol = provider.toSymbol(set)
        val stringSymbol = provider.toSymbol(stringShape)
        sut.withBlock("struct Test {", "}") {
            write("member: \$T,", setSymbol)
            write("otherMember: \$T,", stringSymbol)
        }
        val output = sut.toString()
        output.shouldCompile()
        output shouldContain "HashSet"
        output shouldContain "struct Test"
        output.quickTest(
            """
        let test = Test { member: HashSet::default(), otherMember: "hello".to_string() };
        assert_eq!(test.otherMember, "hello");
        assert_eq!(test.member.is_empty(), true);
         """
        )
    }
}

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

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import software.amazon.smithy.rust.codegen.lang.UseDeclarations
import software.amazon.smithy.rust.testutil.shouldCompile

class UseDeclarationsTest {
    private fun useDecl() = UseDeclarations("lib.rs", "test")

    @Test
    fun `it produces valid use decls`() {
        val sut = useDecl()
        sut.addImport("std::collections", "HashSet")
        sut.addImport("std::borrow", "Cow")
        sut.toString() shouldBe "use std::borrow::Cow;\nuse std::collections::HashSet;"
        sut.toString().shouldCompile()
    }

    @Test
    fun `it deduplicates use decls`() {
        val sut = useDecl()
        sut.addImport("std::collections", "HashSet")
        sut.addImport("std::collections", "HashSet")
        sut.addImport("std::collections", "HashSet")
        sut.toString() shouldBe "use std::collections::HashSet;"
        sut.toString().shouldCompile()
    }

    @Test
    fun `it supports aliasing`() {
        val sut = useDecl()
        sut.addImport("std::collections", "HashSet", "HSet")
        sut.addImport("std::collections", "HashSet")
        sut.toString() shouldBe "use std::collections::HashSet as HSet;\nuse std::collections::HashSet;"
        sut.toString().shouldCompile()
    }
}

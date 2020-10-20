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

package software.amazon.smithy.rust.codegen.util

import java.nio.file.Path
import java.util.concurrent.TimeUnit
import software.amazon.smithy.rust.codegen.smithy.letIf

fun String.runCommand(workdir: Path? = null): String? {
    val parts = this.split("\\s".toRegex())
    val proc = ProcessBuilder(*parts.toTypedArray())
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .redirectError(ProcessBuilder.Redirect.PIPE)
        .letIf(workdir != null) {
            it.directory(workdir?.toFile())
        }
        .start()

    proc.waitFor(60, TimeUnit.MINUTES)
    if (proc.exitValue() != 0) {
        val output = proc.errorStream.bufferedReader().readText()
        throw AssertionError("Command Failed\n$output")
    }
    return proc.inputStream.bufferedReader().readText()
}

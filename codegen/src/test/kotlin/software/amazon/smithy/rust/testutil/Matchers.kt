package software.amazon.smithy.rust.testutil

import io.kotest.matchers.shouldBe

fun <T> String.shouldMatchResource(clazz: Class<T>, resourceName: String) {
    val resource = clazz.getResource(resourceName).readText()
    this.trim().shouldBe(resource.trim())
}

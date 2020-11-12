package software.amazon.smithy.rust.codegen.smithy

import software.amazon.smithy.codegen.core.writer.CodegenWriterDelegator
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.StringShape
import software.amazon.smithy.rust.codegen.lang.Annotation
import software.amazon.smithy.rust.codegen.lang.Derives
import software.amazon.smithy.rust.codegen.lang.Meta
import software.amazon.smithy.rust.codegen.lang.RustWriter

interface Configurator {
    fun container(container: Shape): Meta
    fun member(member: MemberShape): Meta
    fun close(writers: CodegenWriterDelegator<RustWriter>) {}
}

class DefaultConfigurator : Configurator {
    override fun container(container: Shape): Meta {
        val defaultDerives =
            listOf(RuntimeType.StdFmt("Debug"), RuntimeType.Std("cmp::PartialEq"), RuntimeType.Std("clone::Clone"))
        val derives = when (container) {
            is StringShape -> defaultDerives + listOf(RuntimeType.Std("hash::Hash"), RuntimeType.Std("cmp::Eq"))
            else -> defaultDerives
        }
        return Meta(derives = Derives(derives), annotations = listOf(Annotation.NonExhaustive), public = true, lifetimes = listOf())
    }

    override fun member(member: MemberShape): Meta {
        return Meta(derives = Derives(listOf()), annotations = listOf(), public = true, lifetimes = listOf())
    }
}

package software.amazon.smithy.rust.codegen.smithy

import software.amazon.smithy.build.PluginContext
import software.amazon.smithy.build.SmithyBuildPlugin

class RustCodegenPlugin : SmithyBuildPlugin {
    override fun getName(): String = "rust-codegen"

    override fun execute(context: PluginContext) {
        CodegenVisitor(context).execute()
    }
}

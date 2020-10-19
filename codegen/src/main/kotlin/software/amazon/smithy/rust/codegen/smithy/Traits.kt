package software.amazon.smithy.rust.codegen.smithy

import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.traits.Trait

class RustBox : Trait {
    val ID = ShapeId.from("software.amazon.smithy.rust.codegen.smithy.rust.synthetic#box")
    override fun toNode(): Node = Node.objectNode()

    override fun toShapeId(): ShapeId = ID
}
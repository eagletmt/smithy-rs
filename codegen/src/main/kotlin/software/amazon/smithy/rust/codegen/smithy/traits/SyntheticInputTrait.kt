/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.rust.codegen.smithy.traits

import software.amazon.smithy.model.node.ObjectNode
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.traits.AnnotationTrait

/**
 * Indicates that a shape is a synthetic input (see `OperationNormalizer.kt`)
 */
class SyntheticInputTrait @JvmOverloads constructor(val body: ShapeId?) :
    AnnotationTrait(ID, ObjectNode.fromStringMap(mapOf("body" to body.toString()))) {
    // StringTrait(ID, body?.toString(), sourceLocation) {
    /*class Provider : StringTrait.Provider<SyntheticInput?>(
        ID,
        { shapeId: String, sourceLocation: SourceLocation ->
            SyntheticInput(
                ShapeId.from(shapeId),
                sourceLocation
            )
        }
    )*/

    companion object {
        val ID = ShapeId.from("smithy.api.internal#syntheticInput")
    }
}

class InputBodyTrait(objectNode: ObjectNode = ObjectNode.objectNode()) : AnnotationTrait(ID, objectNode) {
    companion object {
        val ID = ShapeId.from("smithy.api.internal#syntheticInput")
    }
}

class SerializerTrait(objectNode: ObjectNode = ObjectNode.objectNode()) : AnnotationTrait(ID, objectNode) {
    companion object {
        val ID = ShapeId.from("smithy.api.internal#syntheticInput")
    }
}

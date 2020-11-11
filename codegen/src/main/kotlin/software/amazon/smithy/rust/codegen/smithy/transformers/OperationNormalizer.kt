/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.rust.codegen.smithy.transformers

import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.transform.ModelTransformer
import software.amazon.smithy.rust.codegen.smithy.traits.InputBodyTrait
import software.amazon.smithy.rust.codegen.smithy.traits.SyntheticInputTrait
import java.util.Optional
import kotlin.streams.toList

private fun StructureShape.rename(newId: ShapeId): StructureShape {
    val renamedMembers = this.members().map {
        it.toBuilder().id(newId.withMember(it.memberName)).build()
    }
    return this.toBuilder().id(newId).members(renamedMembers).build()
}

/**
 * Generate synthetic Input and Output structures for operations.
 */
// TODO: generate operation outputs as well; 2h
class OperationNormalizer(private val symbolProvider: SymbolProvider) {
    private fun OperationShape.inputId() = ShapeId.fromParts(this.id.namespace, "${symbolProvider.toSymbol(this).name}Input")
    private fun OperationShape.bodyId() = ShapeId.fromParts(this.id.namespace, "${symbolProvider.toSymbol(this).name}InputBody")

    /*fun addOperationBody(model: Model, transform: (StructureShape) -> StructureShape): Model {
        val newShapes = model.shapes(OperationShape::class.java).map { operation ->
            // Generate or modify the input of input `Operation` to be a unique shape
            val inputId = operation.bodyId()
            val newInputShape = operation.input.map { shapeId ->
                model.expectShape(shapeId, StructureShape::class.java).rename(inputId)
            }.orElse(StructureShape.builder().id(inputId).build())
            val renamed = newInputShape.toBuilder().addTrait(SyntheticInput()).build()
            transform(renamed)
        }.toList()
        return model.toBuilder().addShapes(newShapes).build()
    }*/

    private fun emptyStructure(id: ShapeId) = StructureShape.builder().id(id).build()

    private val noBody: (StructureShape) -> StructureShape? = { _ -> null }

    fun addOperationInputs(model: Model, bodyBuilder: ((StructureShape) -> StructureShape?) = noBody): Model {
        val transformer = ModelTransformer.create()
        val newShapes = model.shapes(OperationShape::class.java).toList().flatMap { operation ->
            // Generate or modify the input of input `Operation` to be a unique shape
            val inputId = operation.inputId()
            val newInputShape = operation.input.orElse(null)?.let { shapeId ->
                model.expectShape(shapeId, StructureShape::class.java).rename(inputId)
            } ?: emptyStructure(inputId)
            val bodyShape = bodyBuilder(
                newInputShape.rename(operation.bodyId()).toBuilder().addTrait(InputBodyTrait()).build()
            )
            val inputShape = newInputShape.toBuilder().addTrait(SyntheticInputTrait(bodyShape?.id)).build()
            listOf(bodyShape, inputShape).mapNotNull { it }
        }
        val modelWithOperationInputs = model.toBuilder().addShapes(newShapes).build()
        return transformer.mapShapes(modelWithOperationInputs) {
            // Update all operations to point to their new input shape
            val transformed: Optional<Shape> = it.asOperationShape().map { operation ->
                val inputId = ShapeId.fromParts(operation.id.namespace, "${symbolProvider.toSymbol(operation).name}Input")
                modelWithOperationInputs.expectShape(inputId)
                operation.toBuilder().input(inputId).build()
            }
            transformed.orElse(it)
        }
    }
}

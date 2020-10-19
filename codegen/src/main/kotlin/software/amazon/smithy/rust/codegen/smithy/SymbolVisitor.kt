package software.amazon.smithy.rust.codegen.smithy

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.EnumTrait
import software.amazon.smithy.model.traits.ErrorTrait
import software.amazon.smithy.model.traits.Trait
import software.amazon.smithy.rust.codegen.lang.RustType
import software.amazon.smithy.rust.codegen.lang.RustType.*
import software.amazon.smithy.utils.StringUtils
import software.amazon.smithy.vended.NullableIndex
import java.lang.IllegalStateException

// TODO: currently, respecting integer types.
// Should we not? [Go does not]
val SimpleShapes = mapOf(
    BooleanShape::class to Bool,
    FloatShape::class to Float(32),
    DoubleShape::class to Float(64),
    ByteShape::class to Integer(8),
    ShortShape::class to Integer(16),
    IntegerShape::class to Integer(32),
    LongShape::class to Integer(64),
    StringShape::class to RustType.String
)


// TODO:
// Unions
// Recursive shapes
// Synthetics (blobs, timestamps)
// Operation
// Resources (do we do anything for resources?)
// Services
// Higher-level: Set, List, Map

fun Symbol.referenceClosure(): List<Symbol> {
    val referencedSymbols = this.references.map { it.symbol }
    return listOf(this) + referencedSymbols.flatMap { it.referenceClosure() }
}

data class SymbolVisitorConfig(val runtimeConfig: RuntimeConfig, val handleOptionality: Boolean = true, val handleRustBoxing: Boolean = true)

// TODO: consider if this is better handled as a wrapper
val DefaultConfig = SymbolVisitorConfig(runtimeConfig = RuntimeConfig(), handleOptionality = true, handleRustBoxing = true)

data class SymbolLocation(val filename: String, val namespace: String)

fun Symbol.Builder.locatedIn(symbolLocation: SymbolLocation): Symbol.Builder =
    this.definitionFile("src/${symbolLocation.filename}").namespace("crate::${symbolLocation.namespace}", "::")

val Shapes = SymbolLocation("model.rs", "model")
val Errors = SymbolLocation("error.rs", "error")

class SymbolVisitor(
    private val model: Model,
    private val rootNamespace: String = "crate",
    private val config: SymbolVisitorConfig = DefaultConfig
) : SymbolProvider,
    ShapeVisitor<Symbol> {
    private val nullableIndex = NullableIndex(model)
    override fun toSymbol(shape: Shape): Symbol {
        return shape.accept(this)
    }

    override fun blobShape(shape: BlobShape?): Symbol {
        return RuntimeType.Blob(config.runtimeConfig).toSymbol()
    }

    private fun handleOptionality(symbol: Symbol, shape: Shape): Symbol {
        return if (nullableIndex.isNullable(shape)) {
            val builder = Symbol.builder()
            val rustType = Option(symbol.rustType())
            builder.rustType(rustType)
            builder.addReference(symbol)
            builder.name(rustType.name)
            builder.build()
        } else symbol
    }

    private fun handleRustBoxing(symbol: Symbol, shape: Shape): Symbol {
        return if (shape.isA(RustBox::class.java)) {
            val builder = Symbol.builder()
            val rustType = Box(symbol.rustType())
            builder.rustType(rustType)
            builder.addReference(symbol)
            builder.name(rustType.name)
            builder.build()
        } else symbol
    }

    private fun simpleShape(shape: SimpleShape): Symbol {
        return symbolBuilder(shape, SimpleShapes.getValue(shape::class)).build()
    }

    override fun booleanShape(shape: BooleanShape): Symbol = simpleShape(shape)
    override fun byteShape(shape: ByteShape): Symbol = simpleShape(shape)
    override fun shortShape(shape: ShortShape): Symbol = simpleShape(shape)
    override fun integerShape(shape: IntegerShape): Symbol = simpleShape(shape)
    override fun longShape(shape: LongShape): Symbol = simpleShape(shape)
    override fun floatShape(shape: FloatShape): Symbol = simpleShape(shape)
    override fun doubleShape(shape: DoubleShape): Symbol = simpleShape(shape)
    override fun stringShape(shape: StringShape): Symbol {
        return if (shape.isA(EnumTrait::class.java)) {
            symbolBuilder(shape, Opaque(shape.id.name)).locatedIn(Shapes).build()
        } else {
            simpleShape(shape)
        }
    }

    override fun listShape(shape: ListShape): Symbol {
        val inner = this.toSymbol(shape.member)
        return symbolBuilder(shape, Vec(inner.rustType())).addReference(inner).build()
    }

    override fun setShape(shape: SetShape): Symbol {
        val inner = this.toSymbol(shape.member)
        val builder = if (model.expectShape(shape.member.target).isStringShape) {
            // TODO: refactor / figure out how we want to handle prebaked symbols
            symbolBuilder(shape, HashSet(inner.rustType())).namespace(RuntimeType.HashSet.namespace, "::")
        } else {
            // only strings get put into actual sets because floats are unhashable
            symbolBuilder(shape, Vec(inner.rustType()))
        }
        return builder.addReference(inner).build()
    }

    override fun mapShape(shape: MapShape): Symbol {
        assert(shape.key.isStringShape)
        val key = this.toSymbol(shape.key)
        val value = this.toSymbol(shape.value)
        return symbolBuilder(shape, RustType.HashMap(key.rustType(), value.rustType())).namespace(
            "std::collections",
            "::"
        ).addReference(key).addReference(value).build()
    }


    override fun documentShape(shape: DocumentShape?): Symbol {
        TODO("Not yet implemented")
    }


    override fun bigIntegerShape(shape: BigIntegerShape?): Symbol {
        TODO("Not yet implemented")
    }

    override fun bigDecimalShape(shape: BigDecimalShape?): Symbol {
        TODO("Not yet implemented")
    }

    override fun operationShape(shape: OperationShape?): Symbol {
        TODO("Not yet implemented")
    }

    override fun resourceShape(shape: ResourceShape?): Symbol {
        TODO("Not yet implemented")
    }

    override fun serviceShape(shape: ServiceShape?): Symbol {
        TODO("Not yet implemented")
    }


    override fun structureShape(shape: StructureShape): Symbol {
        val isError = shape.isA(ErrorTrait::class.java)
        val name = StringUtils.capitalize(shape.id.name).letIf(isError) {
            it.replace("Exception", "Error")
        }
        val builder = symbolBuilder(shape, Opaque(name))
        return when {
            isError -> builder.locatedIn(Errors)
            else -> builder.locatedIn(Shapes)
        }.build()

        // not sure why we need a reference to each member but I'm sure we'll find out soon enough
        // add a reference to each member symbol
        //addDeclareMemberReferences(builder, shape.allMembers.values)
    }

    override fun unionShape(shape: UnionShape): Symbol {
        val name = StringUtils.capitalize(shape.id.name)
        val builder = symbolBuilder(shape, Opaque(name)).locatedIn(Shapes)

        return builder.build()
    }

    override fun memberShape(shape: MemberShape): Symbol {
        val target = model.getShape(shape.target).orElseThrow { CodegenException("Shape not found. this is a bug.") }
        val targetSymbol = this.toSymbol(target)
        return targetSymbol.letIf(config.handleOptionality) {
            handleOptionality(it, shape)
        }.letIf(config.handleRustBoxing) {
            handleRustBoxing(it, shape)
        }
    }

    override fun timestampShape(shape: TimestampShape?): Symbol {
        return RuntimeType.Instant(config.runtimeConfig).toSymbol()
    }

    private fun symbolBuilder(shape: Shape?, rustType: RustType): Symbol.Builder {
        val builder = Symbol.builder().putProperty("shape", shape)
        return builder.rustType(rustType)
            .name(rustType.name)
            // Every symbol that actually gets defined somewhere should set a definition file
            // If we ever generate a `thisisabug.rs`, we messed something up
            .definitionFile("thisisabug.rs")
    }
}

// TODO(chore): Move this to a useful place
private const val OPTIONAL_KEY = "optional"
private const val RUST_BOX_KEY = "rustboxed"
private const val RUST_TYPE_KEY = "rusttype"

fun Symbol.Builder.rustType(rustType: RustType): Symbol.Builder {
    return this.putProperty(RUST_TYPE_KEY, rustType)
}

fun Symbol.Builder.setOptional(optional: Boolean): Symbol.Builder {
    return this.putProperty(OPTIONAL_KEY, optional)
}

fun Symbol.Builder.setRustBox(rustBoxed: Boolean): Symbol.Builder {
    return this.putProperty(RUST_BOX_KEY, rustBoxed)
}

fun Shape?.isA(trait: Class<out Trait>): Boolean {
    return this?.hasTrait(trait) ?: false
}

fun Symbol.isOptional(): Boolean = when (this.rustType()) {
    is Option -> true
    else -> false
}

// Symbols should _always_ be created with a Rust type attached
fun Symbol.rustType(): RustType = this.getProperty(RUST_TYPE_KEY, RustType::class.java).get()


private fun boolProp(symbol: Symbol, key: String): Boolean {
    return symbol.getProperty(key).map {
        when (it) {
            is Boolean -> it
            else -> throw IllegalStateException("property was not set to boolean")
        }
    }.orElse(false)
}

fun <T> T.letIf(cond: Boolean, f: (T) -> T): T {
    return if (cond) {
        f(this)
    } else this
}
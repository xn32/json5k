package io.github.xn32.json5k.deserialization

import io.github.xn32.json5k.MissingFieldError
import io.github.xn32.json5k.Settings
import io.github.xn32.json5k.UnexpectedValueError
import io.github.xn32.json5k.format.Token
import io.github.xn32.json5k.isUnsignedNumber
import io.github.xn32.json5k.parsing.InjectableLookaheadParser
import io.github.xn32.json5k.parsing.LookaheadParser
import io.github.xn32.json5k.parsing.Parser
import io.github.xn32.json5k.parsing.ReaderPosition
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.modules.SerializersModule

@OptIn(ExperimentalSerializationApi::class)
internal class MainDecoder(
    override val serializersModule: SerializersModule,
    val parser: InjectableLookaheadParser<Token>,
    val settings: Settings,
) : Decoder {
    private var beginPos: ReaderPosition? = null
    private var polymorphicDecoder: PolymorphicDecoder? = null

    constructor(other: MainDecoder) : this(other.serializersModule, other.parser, other.settings)

    override fun <T> decodeSerializableValue(deserializer: DeserializationStrategy<T>): T {
        try {
            val res = super.decodeSerializableValue(deserializer)
            beginPos = null
            polymorphicDecoder = null
            return res
        } catch (e: MissingFieldException) {
            // Throw the library version of a MissingFieldException (for consistency):
            val firstField = e.missingFields[0]
            throw MissingFieldError(firstField, beginPos!!)
        } catch (e: SerializationException) {
            if (polymorphicDecoder != null && e.message?.contains("polymorphic serialization") == true) {
                val classNameToken = polymorphicDecoder?.classNameToken
                if (classNameToken != null) {
                    // Assume/infer that the error is (probably) caused by a missing serializer:
                    val className = classNameToken.item.string
                    throw UnexpectedValueError("unknown class name '$className'", classNameToken.pos)
                }
            }

            throw e
        }
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        beginPos = parser.peek().pos
        return when (descriptor.kind) {
            StructureKind.CLASS -> if (polymorphicDecoder != null) {
                ClassDecoder(this, setOf(polymorphicDecoder!!.classDiscriminator))
            } else {
                ClassDecoder(this)
            }

            StructureKind.LIST -> ListDecoder(this)
            StructureKind.MAP -> MapDecoder(this)
            StructureKind.OBJECT -> ObjectDecoder(this)
            is PolymorphicKind -> {
                val decoder = PolymorphicDecoder(descriptor, this)
                polymorphicDecoder = decoder
                decoder
            }

            else -> throw UnsupportedOperationException()
        }
    }

    override fun decodeByte(): Byte = parser.getInteger(SignedLimits.BYTE).toByte()
    override fun decodeShort(): Short = parser.getInteger(SignedLimits.SHORT).toShort()
    override fun decodeInt(): Int = parser.getInteger(SignedLimits.INT).toInt()
    override fun decodeLong(): Long = parser.getInteger(SignedLimits.LONG)

    override fun decodeFloat(): Float = decodeDouble().toFloat()

    override fun decodeBoolean(): Boolean = parser.next().extractType<Token.Bool>().bool
    override fun decodeDouble(): Double = parser.next().extractType<Token.FloatingPoint>().number
    override fun decodeString(): String = parser.next().extractType<Token.Str>().string

    override fun decodeChar(): Char {
        val (pos, token) = parser.next().mapType<Token.Str>()
        if (token.string.length != 1) {
            throw UnexpectedValueError("unexpected multi-character string", pos)
        }

        return token.string[0]
    }

    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int {
        val (pos, token) = parser.next().mapType<Token.Str>()

        val value = token.string
        val index = enumDescriptor.getElementIndex(value)

        if (index == CompositeDecoder.UNKNOWN_NAME) {
            throw UnexpectedValueError("unexpected value '$value' supplied to enumeration", pos)
        }

        return index
    }

    override fun decodeInline(descriptor: SerialDescriptor): Decoder = if (descriptor.isUnsignedNumber) {
        UnsignedDecoder(this)
    } else {
        this
    }

    @ExperimentalSerializationApi
    override fun decodeNotNullMark(): Boolean {
        return parser.peek().item !is Token.Null
    }

    @ExperimentalSerializationApi
    override fun decodeNull(): Nothing? {
        parser.next().extractType<Token.Null>()
        return null
    }
}

@ExperimentalSerializationApi
private class UnsignedDecoder(private val parent: MainDecoder) : Decoder {
    override val serializersModule: SerializersModule = parent.serializersModule
    private val parser: LookaheadParser<Token> = parent.parser

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder = throw UnsupportedOperationException()
    override fun decodeBoolean(): Boolean = throw UnsupportedOperationException()
    override fun decodeChar(): Char = throw UnsupportedOperationException()
    override fun decodeDouble(): Double = throw UnsupportedOperationException()
    override fun decodeFloat(): Float = throw UnsupportedOperationException()
    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int = throw UnsupportedOperationException()
    override fun decodeInline(descriptor: SerialDescriptor): Decoder = throw UnsupportedOperationException()
    override fun decodeString(): String = throw UnsupportedOperationException()

    override fun decodeByte(): Byte = parser.getUnsignedInteger(UnsignedLimits.BYTE).toByte()
    override fun decodeShort(): Short = parser.getUnsignedInteger(UnsignedLimits.SHORT).toShort()
    override fun decodeInt(): Int = parser.getUnsignedInteger(UnsignedLimits.INT).toInt()
    override fun decodeLong(): Long = parser.getUnsignedInteger(UnsignedLimits.LONG).toLong()

    override fun decodeNotNullMark(): Boolean = parent.decodeNotNullMark()
    override fun decodeNull(): Nothing? = parent.decodeNull()
}

private object SignedLimits {
    val BYTE = Byte.MIN_VALUE.toLong()..Byte.MAX_VALUE.toLong()
    val SHORT = Short.MIN_VALUE.toLong()..Short.MAX_VALUE.toLong()
    val INT = Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()
    val LONG = Long.MIN_VALUE..Long.MAX_VALUE
}

private object UnsignedLimits {
    val BYTE = UByte.MAX_VALUE.toULong()
    val SHORT = UShort.MAX_VALUE.toULong()
    val INT = UInt.MAX_VALUE.toULong()
    val LONG = ULong.MAX_VALUE
}

private fun Parser<Token>.getInteger(limits: LongRange): Long {
    val (pos, token) = next().mapType<Token.Integer>()

    return if (token is Token.SignedInteger && token.number in limits) {
        token.number
    } else if (token is Token.UnsignedInteger && token.number <= limits.last.toULong()) {
        token.number.toLong()
    } else {
        throw UnexpectedValueError("signed integer in range [$limits] expected", pos)
    }
}

private fun Parser<Token>.getUnsignedInteger(max: ULong): ULong {
    val (pos, token) = next().mapType<Token.UnsignedInteger>()

    return if (token.number <= max) {
        token.number
    } else {
        throw UnexpectedValueError("unsigned integer in range [0..$max] expected", pos)
    }
}

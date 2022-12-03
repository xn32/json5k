package io.github.xn32.json5k.serialization

import io.github.xn32.json5k.Settings
import io.github.xn32.json5k.format.Token
import io.github.xn32.json5k.generation.FormatGenerator
import io.github.xn32.json5k.isUnsignedNumber
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule

@OptIn(ExperimentalSerializationApi::class)
internal class MainEncoder(
    override val serializersModule: SerializersModule,
    val generator: FormatGenerator,
    val settings: Settings,
) : Encoder {
    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder = when (descriptor.kind) {
        StructureKind.CLASS, StructureKind.OBJECT -> ClassEncoder(this)
        StructureKind.LIST -> ListEncoder(this)
        StructureKind.MAP -> MapEncoder(this)
        is PolymorphicKind -> PolymorphicEncoder(descriptor, this)
        else -> throw UnsupportedOperationException()
    }

    override fun encodeBoolean(value: Boolean) {
        generator.put(Token.Bool(value))
    }

    override fun encodeFloat(value: Float) = encodeDouble(value.toDouble())

    override fun encodeByte(value: Byte) = encodeLong(value.toLong())
    override fun encodeShort(value: Short) = encodeLong(value.toLong())
    override fun encodeInt(value: Int) = encodeLong(value.toLong())

    override fun encodeLong(value: Long) {
        generator.put(Token.SignedInteger(value))
    }

    override fun encodeDouble(value: Double) {
        generator.put(Token.FloatingPoint(value))
    }

    override fun encodeString(value: String) {
        generator.put(Token.Str(value))
    }

    override fun encodeChar(value: Char) {
        generator.put(Token.Str(value.toString()))
    }

    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) {
        generator.put(Token.Str(enumDescriptor.getElementName(index)))
    }

    override fun encodeInline(descriptor: SerialDescriptor): Encoder = if (descriptor.isUnsignedNumber) {
        UnsignedEncoder(this)
    } else {
        this
    }

    override fun encodeNull() {
        generator.put(Token.Null)
    }
}

@ExperimentalSerializationApi
private class UnsignedEncoder(private val parent: MainEncoder) : Encoder {
    override val serializersModule: SerializersModule = parent.serializersModule
    private val generator: FormatGenerator = parent.generator

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder = throw UnsupportedOperationException()
    override fun encodeBoolean(value: Boolean) = throw UnsupportedOperationException()
    override fun encodeChar(value: Char) = throw UnsupportedOperationException()
    override fun encodeDouble(value: Double) = throw UnsupportedOperationException()
    override fun encodeFloat(value: Float) = throw UnsupportedOperationException()
    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) = throw UnsupportedOperationException()
    override fun encodeInline(descriptor: SerialDescriptor): Encoder = throw UnsupportedOperationException()
    override fun encodeString(value: String) = throw UnsupportedOperationException()

    private fun encodeUnsigned(value: ULong) {
        generator.put(Token.UnsignedInteger(value))
    }

    override fun encodeByte(value: Byte) = encodeUnsigned(value.toUByte().toULong())
    override fun encodeShort(value: Short) = encodeUnsigned(value.toUShort().toULong())
    override fun encodeInt(value: Int) = encodeUnsigned(value.toUInt().toULong())
    override fun encodeLong(value: Long) = encodeUnsigned(value.toULong())
    override fun encodeNull() = parent.encodeNull()
}

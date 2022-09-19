package io.github.xn32.json5k.serialization

import io.github.xn32.json5k.format.Token
import io.github.xn32.json5k.generation.FormatGenerator
import io.github.xn32.json5k.unsupportedKeyType
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule

internal class MapEncoder(parent: MainEncoder) : StructEncoder(parent) {
    private val keyEncoder: KeyEncoder = KeyEncoder(parent)

    init {
        generator.put(Token.BeginObject)
    }

    override fun getEncoderFor(descriptor: SerialDescriptor, index: Int): Encoder = if (index % 2 == 1) {
        super.getEncoderFor(descriptor, index)
    } else {
        keyEncoder
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        generator.put(Token.EndObject)
    }
}


private class KeyEncoder(parent: MainEncoder) : Encoder {
    override val serializersModule: SerializersModule = parent.serializersModule
    private val generator: FormatGenerator = parent.generator

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder = unsupportedKeyType()
    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) = unsupportedKeyType()

    override fun encodeBoolean(value: Boolean) = unsupportedKeyType()
    override fun encodeByte(value: Byte) = unsupportedKeyType()
    override fun encodeShort(value: Short) = unsupportedKeyType()
    override fun encodeInt(value: Int) = unsupportedKeyType()
    override fun encodeLong(value: Long) = unsupportedKeyType()

    override fun encodeChar(value: Char) = unsupportedKeyType()

    override fun encodeFloat(value: Float) = unsupportedKeyType()
    override fun encodeDouble(value: Double) = unsupportedKeyType()

    @ExperimentalSerializationApi
    override fun encodeNull(): Nothing = unsupportedKeyType()

    override fun encodeInline(descriptor: SerialDescriptor): Encoder = this

    override fun encodeString(value: String) {
        generator.put(Token.MemberName(value))
    }
}

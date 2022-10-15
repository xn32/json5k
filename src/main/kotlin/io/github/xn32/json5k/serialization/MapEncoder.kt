package io.github.xn32.json5k.serialization

import io.github.xn32.json5k.format.Token
import io.github.xn32.json5k.generation.FormatGenerator
import io.github.xn32.json5k.throwKeyTypeException
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

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder = throwKeyTypeException()
    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) = throwKeyTypeException()

    override fun encodeBoolean(value: Boolean) = throwKeyTypeException()
    override fun encodeByte(value: Byte) = throwKeyTypeException()
    override fun encodeShort(value: Short) = throwKeyTypeException()
    override fun encodeInt(value: Int) = throwKeyTypeException()
    override fun encodeLong(value: Long) = throwKeyTypeException()

    override fun encodeChar(value: Char) = throwKeyTypeException()

    override fun encodeFloat(value: Float) = throwKeyTypeException()
    override fun encodeDouble(value: Double) = throwKeyTypeException()

    @ExperimentalSerializationApi
    override fun encodeNull(): Nothing = throwKeyTypeException()

    override fun encodeInline(descriptor: SerialDescriptor): Encoder = this

    override fun encodeString(value: String) {
        generator.put(Token.MemberName(value))
    }
}

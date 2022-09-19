package io.github.xn32.json5k.serialization

import io.github.xn32.json5k.generation.FormatGenerator
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule

@OptIn(ExperimentalSerializationApi::class)
internal abstract class StructEncoder(protected val parent: MainEncoder) : CompositeEncoder {
    override val serializersModule: SerializersModule = parent.serializersModule
    protected val generator: FormatGenerator = parent.generator

    override fun shouldEncodeElementDefault(descriptor: SerialDescriptor, index: Int): Boolean {
        return parent.settings.encodeDefaults
    }

    override fun encodeBooleanElement(descriptor: SerialDescriptor, index: Int, value: Boolean) {
        getEncoderFor(descriptor, index).encodeBoolean(value)
    }

    override fun encodeByteElement(descriptor: SerialDescriptor, index: Int, value: Byte) {
        getEncoderFor(descriptor, index).encodeByte(value)
    }

    override fun encodeCharElement(descriptor: SerialDescriptor, index: Int, value: Char) {
        getEncoderFor(descriptor, index).encodeChar(value)
    }

    override fun encodeDoubleElement(descriptor: SerialDescriptor, index: Int, value: Double) {
        getEncoderFor(descriptor, index).encodeDouble(value)
    }

    override fun encodeFloatElement(descriptor: SerialDescriptor, index: Int, value: Float) {
        getEncoderFor(descriptor, index).encodeFloat(value)
    }

    override fun encodeIntElement(descriptor: SerialDescriptor, index: Int, value: Int) {
        getEncoderFor(descriptor, index).encodeInt(value)
    }

    override fun encodeLongElement(descriptor: SerialDescriptor, index: Int, value: Long) {
        getEncoderFor(descriptor, index).encodeLong(value)
    }

    override fun encodeShortElement(descriptor: SerialDescriptor, index: Int, value: Short) {
        getEncoderFor(descriptor, index).encodeShort(value)
    }

    override fun encodeStringElement(descriptor: SerialDescriptor, index: Int, value: String) {
        getEncoderFor(descriptor, index).encodeString(value)
    }

    override fun encodeInlineElement(descriptor: SerialDescriptor, index: Int): Encoder {
        return getEncoderFor(descriptor, index).encodeInline(descriptor.getElementDescriptor(index))
    }

    open fun getEncoderFor(descriptor: SerialDescriptor, index: Int): Encoder {
        return parent
    }

    @ExperimentalSerializationApi
    override fun <T : Any> encodeNullableSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        serializer: SerializationStrategy<T>,
        value: T?
    ) {
        getEncoderFor(descriptor, index).encodeNullableSerializableValue(serializer, value)
    }

    override fun <T> encodeSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        serializer: SerializationStrategy<T>,
        value: T
    ) {
        getEncoderFor(descriptor, index).encodeSerializableValue(serializer, value)
    }
}

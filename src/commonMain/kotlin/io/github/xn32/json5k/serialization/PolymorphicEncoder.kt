package io.github.xn32.json5k.serialization

import io.github.xn32.json5k.format.Token
import io.github.xn32.json5k.getClassDiscriminator
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Encoder

internal class PolymorphicEncoder(descriptor: SerialDescriptor, parent: MainEncoder) : StructEncoder(parent) {
    private val classDiscriminator: String = descriptor.getClassDiscriminator(parent.settings)
    private var className: String? = null

    init {
        generator.put(Token.BeginObject)
    }

    override fun encodeStringElement(descriptor: SerialDescriptor, index: Int, value: String) {
        className = value
    }

    override fun <T> encodeSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        serializer: SerializationStrategy<T>,
        value: T
    ) {
        TypeInjectingEncoder(parent, classDiscriminator, className!!).encodeSerializableValue(serializer, value)
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        generator.put(Token.EndObject)
    }
}

@OptIn(ExperimentalSerializationApi::class)
private class TypeInjectingEncoder(
    private val parent: MainEncoder,
    private val classDiscriminator: String,
    private val className: String,
) : Encoder {
    override val serializersModule = parent.serializersModule
    private val generator = parent.generator

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        generator.put(Token.MemberName(classDiscriminator))
        generator.put(Token.Str(className))
        return ClassEncoder(parent, noDelimiters = true, reservedKeys = setOf(classDiscriminator))
    }

    override fun encodeBoolean(value: Boolean) = throw UnsupportedOperationException()
    override fun encodeByte(value: Byte) = throw UnsupportedOperationException()
    override fun encodeShort(value: Short) = throw UnsupportedOperationException()
    override fun encodeInt(value: Int) = throw UnsupportedOperationException()
    override fun encodeLong(value: Long) = throw UnsupportedOperationException()
    override fun encodeNull() = throw UnsupportedOperationException()
    override fun encodeFloat(value: Float) = throw UnsupportedOperationException()
    override fun encodeInline(descriptor: SerialDescriptor) = throw UnsupportedOperationException()
    override fun encodeDouble(value: Double) = throw UnsupportedOperationException()
    override fun encodeChar(value: Char) = throw UnsupportedOperationException()
    override fun encodeString(value: String) = throw UnsupportedOperationException()
    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) = throw UnsupportedOperationException()
}

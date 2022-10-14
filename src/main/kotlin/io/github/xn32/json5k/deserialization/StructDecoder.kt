package io.github.xn32.json5k.deserialization

import io.github.xn32.json5k.UnexpectedValueError
import io.github.xn32.json5k.format.Token
import io.github.xn32.json5k.parsing.Event
import io.github.xn32.json5k.parsing.InjectableLookaheadParser
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.modules.SerializersModule

@OptIn(ExperimentalSerializationApi::class)
internal sealed class StructDecoder(protected val parent: MainDecoder, opener: Token) : CompositeDecoder {
    override val serializersModule: SerializersModule = parent.serializersModule
    protected val parser: InjectableLookaheadParser<Token> = parent.parser
    protected val beginEvent: Event<Token>

    init {
        val event = parent.parser.next()
        val (pos, token) = event
        if (token != opener) {
            throw UnexpectedValueError("unexpected structure", pos)
        }

        beginEvent = event
    }

    override fun decodeBooleanElement(descriptor: SerialDescriptor, index: Int) = parent.decodeBoolean()
    override fun decodeByteElement(descriptor: SerialDescriptor, index: Int) = parent.decodeByte()
    override fun decodeCharElement(descriptor: SerialDescriptor, index: Int) = parent.decodeChar()
    override fun decodeDoubleElement(descriptor: SerialDescriptor, index: Int) = parent.decodeDouble()
    override fun decodeFloatElement(descriptor: SerialDescriptor, index: Int) = parent.decodeFloat()
    override fun decodeIntElement(descriptor: SerialDescriptor, index: Int) = parent.decodeInt()
    override fun decodeLongElement(descriptor: SerialDescriptor, index: Int) = parent.decodeLong()
    override fun decodeShortElement(descriptor: SerialDescriptor, index: Int) = parent.decodeShort()
    override fun decodeStringElement(descriptor: SerialDescriptor, index: Int) = parent.decodeString()

    override fun decodeInlineElement(descriptor: SerialDescriptor, index: Int): Decoder {
        return parent.decodeInline(descriptor.getElementDescriptor(index))
    }

    override fun <T> decodeSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T>,
        previousValue: T?
    ): T = MainDecoder(parent).decodeSerializableValue(deserializer)

    @ExperimentalSerializationApi
    override fun <T : Any> decodeNullableSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T?>,
        previousValue: T?
    ): T? = if (deserializer.descriptor.isNullable || parent.decodeNotNullMark()) {
        decodeSerializableElement(descriptor, index, deserializer, previousValue)
    } else {
        parent.decodeNull()
    }
}

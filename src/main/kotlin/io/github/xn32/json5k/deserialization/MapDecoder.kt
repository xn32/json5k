package io.github.xn32.json5k.deserialization

import io.github.xn32.json5k.DuplicateKeyError
import io.github.xn32.json5k.format.Token
import io.github.xn32.json5k.unsupportedKeyType
import io.github.xn32.json5k.parsing.LookaheadParser
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.modules.SerializersModule

internal class MapDecoder(parent: MainDecoder) : StructDecoder(parent, Token.BeginObject) {
    private val keyDecoder = KeyDecoder(parent)
    private var count: Int = 0

    override fun <T> decodeSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T>,
        previousValue: T?
    ): T = if (index % 2 == 0) {
        deserializer.deserialize(keyDecoder)
    } else {
        super.decodeSerializableElement(descriptor, index, deserializer, previousValue)
    }

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        val token = parser.peek().item
        return if (token is Token.EndObject) {
            CompositeDecoder.DECODE_DONE
        } else {
            count++
        }
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        parser.next()
    }
}

private class KeyDecoder(private val parent: MainDecoder) : Decoder {
    override val serializersModule: SerializersModule = parent.serializersModule
    private val specifiedKeys: MutableSet<String> = mutableSetOf()
    private val parser: LookaheadParser<Token> = parent.parser

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder = unsupportedKeyType()
    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int = unsupportedKeyType()

    override fun decodeBoolean(): Boolean = unsupportedKeyType()

    override fun decodeByte(): Byte = unsupportedKeyType()
    override fun decodeShort(): Short = unsupportedKeyType()
    override fun decodeInt(): Int = unsupportedKeyType()
    override fun decodeLong(): Long = unsupportedKeyType()

    override fun decodeChar(): Char = unsupportedKeyType()

    override fun decodeFloat(): Float = unsupportedKeyType()
    override fun decodeDouble(): Double = unsupportedKeyType()

    @ExperimentalSerializationApi
    override fun decodeNotNullMark(): Boolean = unsupportedKeyType()

    @ExperimentalSerializationApi
    override fun decodeNull(): Nothing = unsupportedKeyType()

    override fun decodeInline(descriptor: SerialDescriptor): Decoder = this

    override fun decodeString(): String {
        val (pos, token) = parser.next()
        check(token is Token.MemberName)

        val name = token.name
        if (parent.settings.failOnDuplicateKeys && !specifiedKeys.add(name)) {
            throw DuplicateKeyError(name, pos)
        }

        return name
    }
}
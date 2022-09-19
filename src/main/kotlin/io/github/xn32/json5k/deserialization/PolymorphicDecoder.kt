package io.github.xn32.json5k.deserialization

import io.github.xn32.json5k.MissingFieldError
import io.github.xn32.json5k.format.Token
import io.github.xn32.json5k.getClassDiscriminator
import io.github.xn32.json5k.parsing.InjectableLookaheadParser
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder

internal class PolymorphicDecoder(
    descriptor: SerialDescriptor,
    parent: MainDecoder
) : StructDecoder(parent, Token.BeginObject) {
    private val injectableParser = InjectableLookaheadParser(parent.parser)
    private var lastIndex: Int? = null

    init {
        var objectLevel = 1u
        injectableParser.inject(beginEvent)
        val discriminator = descriptor.getClassDiscriminator(parent.settings)
        while (true) {
            val event = parser.next()
            val token = event.item
            if (objectLevel == 1u && token == Token.EndObject) {
                throw MissingFieldError(discriminator, beginEvent.pos)
            } else if (objectLevel == 1u && token is Token.MemberName && token.name == discriminator) {
                break
            } else if (token == Token.BeginObject) {
                ++objectLevel
            } else if (token == Token.EndObject) {
                --objectLevel
            }

            injectableParser.inject(event)
        }
    }

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        val index = when (lastIndex) {
            null -> 0
            0 -> 1
            1 -> CompositeDecoder.DECODE_DONE
            else -> error("unexpected call sequence")
        }

        lastIndex = index
        return index
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        // Nothing to do. When we reach this, the class decoder spawned by us will already
        // have taken care of the closing brace.
    }

    override fun <T> decodeSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T>,
        previousValue: T?
    ): T {
        val injectedDecoder = MainDecoder(parent.serializersModule, injectableParser, parent.settings)
        return injectedDecoder.decodeSerializableValue(deserializer)
    }
}

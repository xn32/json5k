package io.github.xn32.json5k.deserialization

import io.github.xn32.json5k.MissingFieldError
import io.github.xn32.json5k.format.Token
import io.github.xn32.json5k.getClassDiscriminator
import io.github.xn32.json5k.parsing.Event
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder

internal class PolymorphicDecoder(
    descriptor: SerialDescriptor,
    parent: MainDecoder
) : StructDecoder(parent, Token.BeginObject) {
    val classDiscriminator = descriptor.getClassDiscriminator(parent.settings)
    var classNameToken: Event<Token.Str>? = null
        private set

    private val eventBuffer: MutableList<Event<Token>> = mutableListOf()
    private var nextIndex: Int = 0

    init {
        var objectLevel = 1u
        while (true) {
            val event = parser.next()
            val token = event.item
            if (objectLevel == 1u && token == Token.EndObject) {
                throw MissingFieldError(classDiscriminator, structOpenerEvent.pos)
            } else if (objectLevel == 1u && token is Token.MemberName && token.name == classDiscriminator) {
                classNameToken = parser.peek().mapType()
                break
            } else if (token == Token.BeginObject) {
                ++objectLevel
            } else if (token == Token.EndObject) {
                --objectLevel
            }

            eventBuffer.add(event)
        }
    }

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        val index = nextIndex
        nextIndex = when (nextIndex) {
            0 -> 1
            else -> CompositeDecoder.DECODE_DONE
        }

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
        parser.prepend(eventBuffer)
        parser.prepend(structOpenerEvent)
        return parent.decodeSerializableValue(deserializer)
    }
}

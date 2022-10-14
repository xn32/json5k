package io.github.xn32.json5k.deserialization

import io.github.xn32.json5k.DuplicateKeyError
import io.github.xn32.json5k.UnknownKeyError
import io.github.xn32.json5k.format.Token
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder

@OptIn(ExperimentalSerializationApi::class)
internal class ClassDecoder(parent: MainDecoder) : StructDecoder(parent, Token.BeginObject) {
    private val specifiedKeys: MutableSet<String> = mutableSetOf()

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        val (pos, token) = parser.peek()

        return when (token) {
            Token.EndObject -> CompositeDecoder.DECODE_DONE
            is Token.MemberName -> {
                val name = token.name
                parser.next()

                if (!specifiedKeys.add(name)) {
                    throw DuplicateKeyError(name, pos)
                }

                val index = descriptor.getElementIndex(token.name)

                if (index == CompositeDecoder.UNKNOWN_NAME) {
                    throw UnknownKeyError(name, pos)
                }

                return index
            }

            else -> error("expected member name or end of object")
        }
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        parser.next()
    }
}

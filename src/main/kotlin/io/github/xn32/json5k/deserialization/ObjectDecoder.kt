package io.github.xn32.json5k.deserialization

import io.github.xn32.json5k.UnknownKeyError
import io.github.xn32.json5k.format.Token
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.modules.SerializersModule

internal class ObjectDecoder(parent: MainDecoder) : StructDecoder(parent, Token.BeginObject) {
    override val serializersModule: SerializersModule = parent.serializersModule

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        val (pos, token) = parser.peek()

        if (token is Token.MemberName) {
            throw UnknownKeyError(token.name, pos)
        }

        check(token is Token.EndObject)
        return CompositeDecoder.DECODE_DONE
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        parser.next()
    }
}

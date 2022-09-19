package io.github.xn32.json5k.deserialization

import io.github.xn32.json5k.format.Token
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder

internal class ListDecoder(parent: MainDecoder) : StructDecoder(parent, Token.BeginArray) {
    private var count: Int = 0

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        return if (parser.peek().item is Token.EndArray) {
            CompositeDecoder.DECODE_DONE
        } else {
            count++
        }
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        parser.next()
    }
}

package io.github.xn32.json5k.serialization

import io.github.xn32.json5k.format.Token
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Encoder

@OptIn(ExperimentalSerializationApi::class)
internal class ClassEncoder(parent: MainEncoder, private val noDelimiters: Boolean = false) : StructEncoder(parent) {
    init {
        if (!noDelimiters) {
            generator.put(Token.BeginObject)
        }
    }

    override fun getEncoderFor(descriptor: SerialDescriptor, index: Int): Encoder {
        generator.put(Token.MemberName(descriptor.getElementName(index)))
        return super.getEncoderFor(descriptor, index)
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        if (!noDelimiters) {
            generator.put(Token.EndObject)
        }
    }
}
package io.github.xn32.json5k.serialization

import io.github.xn32.json5k.format.Token
import kotlinx.serialization.descriptors.SerialDescriptor

internal class ListEncoder(parent: MainEncoder) : StructEncoder(parent) {
    init {
        generator.put(Token.BeginArray)
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        generator.put(Token.EndArray)
    }
}

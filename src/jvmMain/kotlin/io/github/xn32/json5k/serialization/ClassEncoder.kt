package io.github.xn32.json5k.serialization

import io.github.xn32.json5k.SerialComment
import io.github.xn32.json5k.format.Token
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Encoder

@OptIn(ExperimentalSerializationApi::class)
internal class ClassEncoder(
    parent: MainEncoder,
    private val noDelimiters: Boolean = false,
    private val reservedKeys: Set<String> = setOf()
) : StructEncoder(parent) {
    init {
        if (!noDelimiters) {
            generator.put(Token.BeginObject)
        }
    }

    override fun getEncoderFor(descriptor: SerialDescriptor, index: Int): Encoder {
        for (comment in descriptor.getElementAnnotations(index).filterIsInstance<SerialComment>()) {
            generator.writeComment(comment.value.trimIndent())
        }

        val memberName = descriptor.getElementName(index)
        if (reservedKeys.contains(memberName)) {
            throw UnsupportedOperationException("member name '$memberName' is reserved")
        }

        generator.put(Token.MemberName(descriptor.getElementName(index)))
        return super.getEncoderFor(descriptor, index)
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        if (!noDelimiters) {
            generator.put(Token.EndObject)
        }
    }
}

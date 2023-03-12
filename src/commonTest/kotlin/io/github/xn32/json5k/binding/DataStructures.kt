package io.github.xn32.json5k.binding

import io.github.xn32.json5k.ClassDiscriminator
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.jvm.JvmInline

@Serializable
internal enum class DummyEnum { ITEM }

@Serializable
internal data class UnsignedContainer(val byte: UByte, val short: UShort, val int: UInt, val long: ULong)

@Serializable
internal data class SignedContainer(val byte: Byte, val short: Short, val int: Int, val long: Long)

@Serializable
internal data class FloatingPointContainer(val float: Float, val double: Double)

@Serializable
internal data class MiscContainer(val char: Char, val str: String, val bool: Boolean, val enum: DummyEnum)

@Serializable
internal object Singleton

@Serializable
internal data class Wrapper<T>(val obj: T)

@JvmInline
@Serializable
internal value class InlineWrapper<T>(val obj: T)

@Serializable
internal sealed interface DefaultInterface

@Serializable
@SerialName("flat")
internal data class FlatDefaultImpl(val a: Int, val b: Int = 100) : DefaultInterface

@Serializable
@SerialName("nested")
internal data class NestedDefaultImpl(val a: Int, val x: Wrapper<Int>) : DefaultInterface

@Serializable
@SerialName("invalid")
internal data class InvalidDefaultImpl(val type: String) : DefaultInterface

@Serializable
@ClassDiscriminator("category")
internal sealed interface CustomInterface

@Serializable
@SerialName("main")
internal data class CustomImpl(val name: String?) : CustomInterface

internal data class Color(val rgb: Int)

internal object ColorAsStringSerializer : KSerializer<Color> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Color", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Color) {
        val string = value.rgb.toString(16).padStart(6, '0')
        encoder.encodeString("0x$string")
    }

    override fun deserialize(decoder: Decoder): Color {
        val string = decoder.decodeString()
        require(string.startsWith("0x"))
        return Color(string.removePrefix("0x").toInt(16))
    }
}

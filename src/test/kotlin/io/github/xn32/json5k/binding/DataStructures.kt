package io.github.xn32.json5k.binding

import io.github.xn32.json5k.ClassDiscriminator
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@JvmInline
@Serializable
internal value class StringWrapper(val str: String)

@Serializable
internal enum class DummyEnum { ITEM }

@Serializable
internal class UnsignedContainer(val byte: UByte, val short: UShort, val int: UInt, val long: ULong)

@Serializable
internal class SignedContainer(val byte: Byte, val short: Short, val int: Int, val long: Long)

@Serializable
internal class FloatingPointContainer(val float: Float, val double: Double)

@Serializable
internal class MiscContainer(val char: Char, val str: String, val bool: Boolean, val enum: DummyEnum)

@Serializable
internal object Singleton

@Serializable
internal data class Wrapper<T>(val obj: T)

@Serializable
internal sealed interface DefaultInterface

@Serializable
@SerialName("valid")
internal data class DefaultImpl(val integer: Int) : DefaultInterface

@Serializable
@SerialName("invalid")
internal data class InvalidDefaultImpl(val type: String) : DefaultInterface

@Serializable
@ClassDiscriminator("category")
internal sealed interface CustomInterface

@Serializable
@SerialName("main")
internal data class CustomImpl(val name: String?) : CustomInterface

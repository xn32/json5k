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

@Serializable
internal sealed interface DefaultInterface

@Serializable
@SerialName("flat")
internal data class FlatDefaultImpl(val integer: Int) : DefaultInterface

@Serializable
@SerialName("nested")
internal data class NestedDefaultImpl(val inner: Wrapper<Int>) : DefaultInterface

@Serializable
@SerialName("invalid")
internal data class InvalidDefaultImpl(val type: String) : DefaultInterface

@Serializable
@ClassDiscriminator("category")
internal sealed interface CustomInterface

@Serializable
@SerialName("main")
internal data class CustomImpl(val name: String?) : CustomInterface

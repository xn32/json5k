package io.github.xn32.json5k.binding

import io.github.xn32.json5k.ClassDiscriminator
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal object Singleton

@Serializable
internal data class Wrapper<T>(val obj: T)

@Serializable
internal sealed interface DefaultInterface

@Serializable
@SerialName("impl")
internal data class DefaultImpl(val integer: Int) : DefaultInterface

@Serializable
@ClassDiscriminator("category")
internal sealed interface CustomInterface

@Serializable
@SerialName("impl")
internal data class CustomImpl(val name: String?) : CustomInterface

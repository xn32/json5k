package io.github.xn32.json5k

import io.github.xn32.json5k.config.ConfigBuilder
import io.github.xn32.json5k.config.Settings
import io.github.xn32.json5k.config.toSettings
import io.github.xn32.json5k.deserialization.MainDecoder
import io.github.xn32.json5k.format.Token
import io.github.xn32.json5k.generation.FormatGenerator
import io.github.xn32.json5k.parsing.FormatParser
import io.github.xn32.json5k.parsing.PlainLookaheadParser
import io.github.xn32.json5k.serialization.MainEncoder
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InheritableSerialInfo
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.StringFormat
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

interface Json5 : StringFormat {
    companion object Default : Json5 by ConfigBuilder().toImpl()

    fun <T> decodeFromStream(deserializer: DeserializationStrategy<T>, inputStream: InputStream): T
    fun <T> encodeToStream(serializer: SerializationStrategy<T>, value: T, outputStream: OutputStream)
}

fun Json5(actions: ConfigBuilder.() -> Unit): Json5 {
    val builder = ConfigBuilder()
    builder.actions()
    return builder.toImpl()
}

@OptIn(ExperimentalSerializationApi::class)
@InheritableSerialInfo
@Target(AnnotationTarget.CLASS)
annotation class ClassDiscriminator(val discriminator: String)

private fun ConfigBuilder.toImpl(): Json5 {
    return Json5Impl(serializersModule ?: EmptySerializersModule(), toSettings())
}

private class Json5Impl(
    override val serializersModule: SerializersModule,
    val settings: Settings,
) : Json5 {
    override fun <T> decodeFromStream(deserializer: DeserializationStrategy<T>, inputStream: InputStream): T {
        val parser = PlainLookaheadParser(FormatParser(inputStream))
        val res = MainDecoder(serializersModule, parser, settings).decodeSerializableValue(deserializer)

        while (true) {
            if (parser.next().item == Token.EndOfFile) {
                break
            }
        }

        return res
    }

    override fun <T> encodeToStream(serializer: SerializationStrategy<T>, value: T, outputStream: OutputStream) {
        val generator = FormatGenerator(outputStream, settings.outputStrategy)
        MainEncoder(serializersModule, generator, settings).encodeSerializableValue(serializer, value)
        generator.put(Token.EndOfFile)
    }

    override fun <T> decodeFromString(deserializer: DeserializationStrategy<T>, string: String): T {
        val stream = string.byteInputStream()
        return decodeFromStream(deserializer, stream)
    }

    override fun <T> encodeToString(serializer: SerializationStrategy<T>, value: T): String {
        val stream = ByteArrayOutputStream()
        encodeToStream(serializer, value, stream)
        return stream.toString()
    }
}

inline fun <reified T> Json5.decodeFromStream(inputStream: InputStream): T =
    decodeFromStream(serializersModule.serializer(), inputStream)

inline fun <reified T> Json5.encodeToStream(value: T, outputStream: OutputStream) =
    encodeToStream(serializersModule.serializer(), value, outputStream)

internal val unsignedDescriptors = setOf(
    UByte.serializer(), UShort.serializer(), UInt.serializer(), ULong.serializer()
).map(DeserializationStrategy<*>::descriptor)

internal val SerialDescriptor.isUnsignedNumber: Boolean
    get() = isInline && this in unsignedDescriptors

@OptIn(ExperimentalSerializationApi::class)
internal fun SerialDescriptor.getClassDiscriminator(settings: Settings): String {
    val annotation = annotations.filterIsInstance<ClassDiscriminator>().firstOrNull()
    return annotation?.discriminator ?: settings.classDiscriminator
}

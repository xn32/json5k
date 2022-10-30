package io.github.xn32.json5k

import io.github.xn32.json5k.deserialization.MainDecoder
import io.github.xn32.json5k.format.Token
import io.github.xn32.json5k.generation.FormatGenerator
import io.github.xn32.json5k.parsing.FormatParser
import io.github.xn32.json5k.parsing.InjectableLookaheadParser
import io.github.xn32.json5k.serialization.MainEncoder
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InheritableSerialInfo
import kotlinx.serialization.SerialInfo
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.StringFormat
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Main interface to serialize a given object hierarchy to JSON5 or vice versa.
 *
 * The default class implementing this interface is available as the [companion object][Json5.Default].
 * It can be used as shown in the following example:
 * ```
 * import io.github.xn32.json5k.Json5
 * import kotlinx.serialization.encodeToString
 *
 * Json5.encodeToString(1000)
 * ```
 *
 * A custom instance is obtained by calling the builder function and populating the
 * associated [ConfigBuilder] object with the desired configuration options.
 */
interface Json5 : StringFormat {
    /**
     * This companion object is the default implementation of the [Json5] interface.
     *
     * It passes an empty `SerializersModule` to the kotlinx.serialization framework and
     * uses `type` as the class discriminator for the serialization and deserialization of
     * polymorphic types. During the serialization process, it does not encode default values
     * and generates compressed output (without line breaks for better readability).
     */
    companion object Default : Json5 by ConfigBuilder().toImpl()

    /**
     * Serializes the given [value] using the provided [serializer] and returns the value as a string.
     *
     * If multi-line output is requested, the function will use UNIX-style line endings. In any case,
     * a final newline character is *not* generated.
     *
     * @param serializer [SerializationStrategy] to use for the serialization.
     * @param value The object hierarchy to serialize.
     * @return The generated JSON5 output (using LF as line terminator).
     */
    override fun <T> encodeToString(serializer: SerializationStrategy<T>, value: T): String

    /**
     * Deserializes the JSON5 input in [string] using the given [deserializer] and returns
     * an equivalent object hierarchy.
     *
     * @param deserializer [DeserializationStrategy] to use for the deserialization.
     * @param string JSON5 input to deserialize into an object hierarchy.
     * @throws ParsingError The provided input cannot be parsed.
     * @throws DecodingError The provided input is incompatible with the data model.
     * @return The created object hierarchy.
     */
    override fun <T> decodeFromString(deserializer: DeserializationStrategy<T>, string: String): T

    /**
     * Serializes the given [value] using the given [serializer] and writes the result to [outputStream].
     *
     * Data written to [outputStream] will have UTF-8 encoding. It is not closed upon completion of this operation.
     *
     * If multi-line output is requested, the function will use UNIX-style line endings. In any case,
     * a final newline character is *not* generated.
     *
     * @param serializer [SerializationStrategy] to use for the serialization.
     * @param value The object hierarchy to serialize.
     * @param outputStream [OutputStream] to write the UTF-8 result to.
     * @throws IOException An error occurred while writing to [outputStream].
     */
    fun <T> encodeToStream(serializer: SerializationStrategy<T>, value: T, outputStream: OutputStream)

    /**
     * Deserializes the JSON5 input from [inputStream] using the given [deserializer] and
     * returns an equivalent object hierarchy.
     *
     * Data read from the [InputStream] is expected to have UTF-8 encoding.
     *
     * @param deserializer [DeserializationStrategy] to use for the deserialization.
     * @param inputStream UTF-8-encoded stream to read the JSON5 input from.
     * @throws ParsingError The provided input cannot be parsed.
     * @throws DecodingError The provided input is incompatible with the data model.
     * @throws IOException An error occurred while reading from [inputStream].
     * @return The created object hierarchy.
     */
    fun <T> decodeFromStream(deserializer: DeserializationStrategy<T>, inputStream: InputStream): T
}

/**
 * Type-safe builder to create a custom [Json5] instance.
 *
 * To configure the returned instance (by specifying a custom [SerializersModule] or adjusting JSON5-specific
 * settings), populate the [ConfigBuilder] object accordingly. The following example shows how to create and
 * use a custom instance:
 * ```
 * import io.github.xn32.json5k.Json5
 * import kotlinx.serialization.encodeToString
 *
 * val json5 = Json5 {
 *     prettyPrint = true
 * }
 *
 * json5.encodeToString(listOf(10, 20, 30))
 * ```
 *
 * A detailed description of available settings can be found in the [ConfigBuilder] documentation.
 */
fun Json5(actions: ConfigBuilder.() -> Unit): Json5 {
    val builder = ConfigBuilder()
    builder.actions()
    return builder.toImpl()
}

/**
 * Annotation to set the class discriminator of polymorphic base types.
 *
 * Apply this to a polymorphic base type (such as an interface) to override the global class
 * discriminator set in the [ConfigBuilder] object.
 */
@OptIn(ExperimentalSerializationApi::class)
@InheritableSerialInfo
@Target(AnnotationTarget.CLASS)
annotation class ClassDiscriminator(
    /**
     * Class discriminator name to use for this base type. This value becomes effective for both
     * serialization and deserialization. It is the caller's responsibility to ensure that the passed
     * value is a single-line JSON5 string.
     */
    val discriminator: String
)

/**
 * Annotation to specify a comment that should be serialized to every JSON5 occurrence of a property.
 *
 * When this annotation is applied to a property and human-readable JSON5 output is generated,
 * every member of a generated JSON5 object that represents this property will be preceded by
 * a JSON5 representation of this comment.
 */
@OptIn(ExperimentalSerializationApi::class)
@Target(AnnotationTarget.PROPERTY)
@SerialInfo
annotation class SerialComment(
    /**
     * Comment to generate whenever the property is serialized into a JSON5 object. This value will be
     * trimmed using the [String.trimIndent] function of Kotlin. Multi-line strings (separated by any
     * of the JSON5 line terminators) are supported.
     */
    val value: String
)

private fun ConfigBuilder.toImpl(): Json5 {
    return Json5Impl(serializersModule ?: EmptySerializersModule(), toSettings())
}

private class Json5Impl(
    override val serializersModule: SerializersModule,
    val settings: Settings,
) : Json5 {
    override fun <T> decodeFromStream(deserializer: DeserializationStrategy<T>, inputStream: InputStream): T {
        val parser = InjectableLookaheadParser(FormatParser(inputStream))
        val res = MainDecoder(serializersModule, parser, settings).decodeSerializableValue(deserializer)
        parser.next()
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
        return stream.toString("UTF-8")
    }
}

/**
 * Convenience function that calls [Json5.decodeFromStream] with the default serializer for the type.
 */
inline fun <reified T> Json5.decodeFromStream(inputStream: InputStream): T =
    decodeFromStream(serializersModule.serializer(), inputStream)

/**
 * Convenience function that calls [Json5.encodeToStream] with the default serializer for the type.
 */
inline fun <reified T> Json5.encodeToStream(value: T, outputStream: OutputStream) =
    encodeToStream(serializersModule.serializer(), value, outputStream)

private val unsignedDescriptors = setOf(
    UByte.serializer(), UShort.serializer(), UInt.serializer(), ULong.serializer()
).map(DeserializationStrategy<*>::descriptor)

internal val SerialDescriptor.isUnsignedNumber: Boolean
    get() = this in unsignedDescriptors

@OptIn(ExperimentalSerializationApi::class)
internal fun SerialDescriptor.getClassDiscriminator(settings: Settings): String {
    val discriminator = annotations.filterIsInstance<ClassDiscriminator>().firstOrNull()?.discriminator
    return discriminator ?: settings.classDiscriminator
}

package io.github.xn32.json5k

import io.github.xn32.json5k.deserialization.MainDecoder
import io.github.xn32.json5k.format.Token
import io.github.xn32.json5k.generation.FormatGenerator
import io.github.xn32.json5k.generation.OutputSink
import io.github.xn32.json5k.generation.StringOutputSink
import io.github.xn32.json5k.parsing.FormatParser
import io.github.xn32.json5k.parsing.InjectableLookaheadParser
import io.github.xn32.json5k.parsing.InputSource
import io.github.xn32.json5k.parsing.StringInputSource
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
sealed class Json5 constructor(
    override val serializersModule: SerializersModule,
    private val settings: Settings
) : StringFormat {

    constructor(builder: ConfigBuilder) : this(
        serializersModule = builder.serializersModule ?: EmptySerializersModule(),
        settings = builder.toSettings()
    )

    /**
     * This companion object is the default implementation of the [Json5] interface.
     *
     * It passes an empty [SerializersModule] to the kotlinx.serialization framework and
     * uses `type` as the class discriminator for the serialization and deserialization of
     * polymorphic types. During the serialization process, it does not encode default values
     * and generates compressed output (without line breaks for better readability).
     */
    companion object Default : Json5(ConfigBuilder())

    /**
     * Serializes the given [value] using the provided [serializer] and returns the value as a string.
     *
     * If human-readable output is requested, the function will use UNIX-style line endings.
     * A final newline character is *not* generated.
     *
     * @param serializer [SerializationStrategy] to use for the serialization.
     * @param value The object hierarchy to serialize.
     * @return The generated JSON5 output.
     */
    override fun <T> encodeToString(serializer: SerializationStrategy<T>, value: T): String {
        val sink = StringOutputSink()
        encode(serializer, value, sink)
        return sink.toString()
    }

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
    override fun <T> decodeFromString(deserializer: DeserializationStrategy<T>, string: String): T {
        return decode(deserializer, StringInputSource(string))
    }

    internal fun <T> decode(deserializer: DeserializationStrategy<T>, reader: InputSource): T {
        val parser = InjectableLookaheadParser(FormatParser(reader))
        val res = MainDecoder(serializersModule, parser, settings).decodeSerializableValue(deserializer)
        parser.next()
        return res
    }

    internal fun <T> encode(serializer: SerializationStrategy<T>, value: T, sink: OutputSink) {
        val generator = FormatGenerator(sink, settings.outputStrategy)
        MainEncoder(serializersModule, generator, settings).encodeSerializableValue(serializer, value)
        generator.put(Token.EndOfFile)
        sink.finalize()
    }
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
    return Json5Impl(builder)
}

private class Json5Impl(builder: ConfigBuilder) : Json5(builder)

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

private val unsignedDescriptors = setOf(UByte.serializer(), UShort.serializer(), UInt.serializer(), ULong.serializer())
    .map(DeserializationStrategy<*>::descriptor)

internal val SerialDescriptor.isUnsignedNumber: Boolean
    get() = this in unsignedDescriptors

@OptIn(ExperimentalSerializationApi::class)
internal fun SerialDescriptor.getClassDiscriminator(settings: Settings): String {
    val discriminator = annotations.filterIsInstance<ClassDiscriminator>().firstOrNull()?.discriminator
    return discriminator ?: settings.classDiscriminator
}

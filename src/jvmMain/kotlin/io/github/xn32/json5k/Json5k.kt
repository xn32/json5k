package io.github.xn32.json5k

import io.github.xn32.json5k.generation.OutputSink
import io.github.xn32.json5k.parsing.InputSource
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.serializer
import java.io.InputStream
import java.io.OutputStream

/**
 * Serializes the given [value] using the given [serializer] and writes the result to [outputStream].
 *
 * Data written to [outputStream] will have UTF-8 encoding. It is not closed upon completion of this operation.
 *
 * If human-readable output is requested, the function will use UNIX-style line endings.
 * A final newline character is *not* generated.
 *
 * @param serializer [SerializationStrategy] to use for the serialization.
 * @param value The object hierarchy to serialize.
 * @param outputStream [OutputStream] to write the UTF-8 result to.
 * @throws java.io.IOException An error occurred while writing to [outputStream].
 */
fun <T> Json5.encodeToStream(serializer: SerializationStrategy<T>, value: T, outputStream: OutputStream) {
    encode(serializer, value, OutputStreamSink(outputStream))
}

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
 * @throws java.io.IOException An error occurred while reading from [inputStream].
 * @return The created object hierarchy.
 */
fun <T> Json5.decodeFromStream(deserializer: DeserializationStrategy<T>, inputStream: InputStream): T {
    return decode(deserializer, InputStreamSource(inputStream))
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

internal class InputStreamSource(stream: InputStream) : InputSource {
    private val reader = stream.bufferedReader()
    private var next: Int = reader.read()

    override val done get() = next < 0

    override fun peek(): Char {
        check(next >= 0)
        return next.toChar()
    }

    override fun consume(): Char {
        val c = peek()
        next = reader.read()
        return c
    }
}

internal class OutputStreamSink(stream: OutputStream) : OutputSink {
    private val writer = stream.bufferedWriter()
    override fun write(char: Char) {
        writer.append(char)
    }

    override fun finalize() {
        writer.flush()
    }
}

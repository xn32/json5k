package io.github.xn32.json5k

import io.github.xn32.json5k.format.Specification
import io.github.xn32.json5k.parsing.InputReader
import io.github.xn32.json5k.parsing.ReaderPosition

/**
 * Base class of all Exceptions that the library raises in response to invalid JSON5 input.
 */
sealed class InputError constructor(msg: String, pos: ReaderPosition) : Exception() {
    /**
     * Human-readable description of the error along with its position (line and column)
     * in the input stream.
     */
    override val message: String = "$msg at position $pos"

    /**
     * Line of the input stream position that the error is associated with.
     */
    val line: UInt = pos.line

    /**
     * Column of the input stream position that the error is associated with.
     */
    val column: UInt = pos.column
}

/**
 * Base class for all errors related to parsing the JSON5 input.
 */
sealed class ParsingError constructor(msg: String, pos: ReaderPosition) : InputError(msg, pos)

/**
 * Exception class thrown if an unexpected literal is encountered in the JSON5 input.
 */
class LiteralError internal constructor(val literal: String, pos: ReaderPosition) :
    ParsingError("unexpected literal '$literal'", pos)

/**
 * Exception class thrown if an unexpected character is encountered in the JSON5 input.
 */
class CharError internal constructor(val char: Char, pos: ReaderPosition) :
    ParsingError("unexpected character '${char.display()}'", pos)

/**
 * Exception class thrown if an unexpected end of the input stream is encountered.
 */
class EndOfFileError internal constructor(pos: ReaderPosition) :
    ParsingError("unexpected end of file", pos)

/**
 * Exception class thrown if a number in the JSON5 input exceeds the range that the parser is able to handle.
 */
class OverflowError internal constructor(pos: ReaderPosition) :
    ParsingError("integer exceeds internal value range", pos)

/**
 * Base class for errors related to the translation of syntactically correct JSON5 input into an object hierarchy.
 */
sealed class DecodingError constructor(msg: String, pos: ReaderPosition) : InputError(msg, pos)

/**
 * Exception class thrown if a required field of a Kotlin class is missing.
 */
class MissingFieldError internal constructor(val key: String, pos: ReaderPosition) :
    DecodingError("missing field '$key' in object", pos)

/**
 * Exception class thrown if a specified object key is not part of the data model.
 */
class UnknownKeyError internal constructor(val key: String, pos: ReaderPosition) :
    DecodingError("unknown key '$key'", pos)

/**
 * Exception class thrown if a specific key is encountered for the second time in the same JSON5 object.
 */
class DuplicateKeyError internal constructor(val key: String, pos: ReaderPosition) :
    DecodingError("duplicate key '$key'", pos)

/**
 * Exception class thrown if a specified value in the JSON5 input is not in line with the underlying data model.
 */
class UnexpectedValueError internal constructor(baseMsg: String, pos: ReaderPosition) :
    DecodingError(baseMsg, pos)

private fun Char.display(): String = if (isUnicodeOther()) {
    Specification.REVERSE_ESCAPE_CHAR_MAP[this]?.let { "\\$it" } ?: "U+${"%04X".format(code)}"
} else {
    toString()
}

internal fun InputReader.throwTokenError(): Nothing = throw if (done) {
    EndOfFileError(pos)
} else {
    CharError(peek(), pos)
}

internal fun throwKeyTypeException(): Nothing {
    throw UnsupportedOperationException("map key must be a non-nullable string")
}

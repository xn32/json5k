package io.github.xn32.json5k

import io.github.xn32.json5k.format.Specification
import io.github.xn32.json5k.parsing.LinePosition
import io.github.xn32.json5k.parsing.SourceReader

/**
 * Base class of all Exceptions that the library raises in response to invalid JSON5 input.
 */
sealed class InputError(val violation: String, pos: LinePosition) : Exception("$violation at position $pos") {
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
sealed class ParsingError(msg: String, pos: LinePosition) : InputError(msg, pos)

/**
 * Exception class thrown if an unexpected literal is encountered in the JSON5 input.
 */
class LiteralError internal constructor(val literal: String, pos: LinePosition) :
    ParsingError("unexpected literal '$literal'", pos)

/**
 * Exception class thrown if an unexpected character is encountered in the JSON5 input.
 */
class CharError internal constructor(val char: Char, pos: LinePosition) :
    ParsingError("unexpected character '${char.display()}'", pos)

/**
 * Exception class thrown if an unexpected end of the input stream is encountered.
 */
class EndOfFileError internal constructor(pos: LinePosition) :
    ParsingError("unexpected end of file", pos)

/**
 * Base class for errors related to the translation of syntactically correct JSON5 input into an object hierarchy.
 */
sealed class DecodingError(msg: String, pos: LinePosition) : InputError(msg, pos)

/**
 * Exception class thrown if a required field of a Kotlin class is missing.
 */
class MissingFieldError internal constructor(val key: String, pos: LinePosition) :
    DecodingError("missing field '$key' in object", pos)

/**
 * Exception class thrown if a specified object key is not part of the data model.
 */
class UnknownKeyError internal constructor(val key: String, pos: LinePosition) :
    DecodingError("unknown key '$key'", pos)

/**
 * Exception class thrown if a specific key is encountered for the second time in the same JSON5 object.
 */
class DuplicateKeyError internal constructor(val key: String, pos: LinePosition) :
    DecodingError("duplicate key '$key'", pos)

/**
 * Exception class thrown if a specified value in the JSON5 input is not in line with the underlying data model.
 */
class UnexpectedValueError internal constructor(baseMsg: String, pos: LinePosition) :
    DecodingError(baseMsg, pos)

private fun Char.display(): String = if (isUnicodeOther()) {
    Specification.REVERSE_ESCAPE_CHAR_MAP[this]?.let { "\\$it" } ?: "U+${toHexString()}"
} else {
    toString()
}

internal fun SourceReader.throwTokenError(): Nothing = throw if (done) {
    EndOfFileError(pos)
} else {
    CharError(peek(), pos)
}

internal fun throwKeyTypeException(): Nothing {
    throw UnsupportedOperationException("map key must be a non-nullable string")
}

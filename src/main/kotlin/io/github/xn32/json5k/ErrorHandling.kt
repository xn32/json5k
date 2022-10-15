package io.github.xn32.json5k

import io.github.xn32.json5k.format.Specification
import io.github.xn32.json5k.parsing.InputReader
import io.github.xn32.json5k.parsing.ReaderPosition
import kotlinx.serialization.SerializationException

sealed class InputError constructor(msg: String, pos: ReaderPosition) : Exception() {
    override val message: String = "$msg at position $pos"
    val line: UInt = pos.line
    val column: UInt = pos.column
}

sealed class ParsingError constructor(msg: String, pos: ReaderPosition) : InputError(msg, pos)

class LiteralError internal constructor(val literal: String, pos: ReaderPosition) :
    ParsingError("unexpected literal '$literal'", pos)

class CharError internal constructor(val char: Char, pos: ReaderPosition) :
    ParsingError("unexpected character '${char.display()}'", pos)

class EndOfFileError internal constructor(pos: ReaderPosition) :
    ParsingError("unexpected end of file", pos)

class OverflowError internal constructor(pos: ReaderPosition) :
    ParsingError("integer exceeds internal value range", pos)

sealed class DecodingError constructor(msg: String, pos: ReaderPosition) : InputError(msg, pos)

class MissingFieldError internal constructor(val key: String, pos: ReaderPosition) :
    DecodingError("missing field '$key' in object", pos)

class UnknownKeyError internal constructor(val key: String, pos: ReaderPosition) :
    DecodingError("unknown key '$key'", pos)

class DuplicateKeyError internal constructor(val key: String, pos: ReaderPosition) :
    DecodingError("duplicate key '$key'", pos)

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

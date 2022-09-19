package io.github.xn32.json5k

import io.github.xn32.json5k.format.Specification
import io.github.xn32.json5k.util.InputReader
import io.github.xn32.json5k.util.ReaderPosition
import io.github.xn32.json5k.util.isUnicodeOther

interface PositionProvider {
    val line: UInt
    val column: UInt
}

sealed class ParsingError constructor(msg: String, pos: ReaderPosition) : Exception(), PositionProvider {
    override val message: String = "$msg at position $pos"
    override val line: UInt = pos.line
    override val column: UInt = pos.column
}

class CharError internal constructor(val char: Char, pos: ReaderPosition) :
    ParsingError("unexpected character '${char.display()}'", pos)

class EndOfFileError internal constructor(pos: ReaderPosition) :
    ParsingError("unexpected end of file", pos)

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

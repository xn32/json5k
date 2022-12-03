package io.github.xn32.json5k.parsing

import io.github.xn32.json5k.format.Specification
import io.github.xn32.json5k.throwTokenError

internal interface InputSource {
    fun peek(): Char
    fun consume(): Char
    val done: Boolean
}

internal class StringInputSource(private val str: String) : InputSource {
    private var pos = 0

    override fun peek(): Char = str[pos]
    override fun consume(): Char = str[pos++]

    override val done: Boolean get() = pos >= str.length
}

internal fun SourceReader.peekOrNull(): Char? = if (!done) peek() else null
internal fun SourceReader.consumeOrNull(): Char? = if (!done) consume() else null

internal fun SourceReader.consumeAll(): String = consumeWhile { true }
internal fun SourceReader.consumeWhile(predicate: (Char) -> Boolean): String = buildString {
    while (!done && predicate(peek())) {
        append(consume())
    }
}

internal data class LinePosition(val line: UInt, val column: UInt) {
    override fun toString(): String = "$line:$column"
}

internal interface SourceReader {
    fun peek(): Char
    fun consume(): Char
    val done: Boolean
    val pos: LinePosition
}

private class SourceReaderImpl(
    private val reader: InputSource,
    private val lineTerminators: Set<Char> = Specification.LINE_TERMINATORS,
    private val honorCrLf: Boolean = true,
) : SourceReader {
    private var line: UInt = 1u
    private var column: UInt = 1u

    override val pos: LinePosition get() = LinePosition(line, column)

    override val done get() = reader.done

    override fun peek() = reader.peek()
    override fun consume() = reader.consume().also(::handleChar)

    private fun handleChar(char: Char) {
        val crLfPending = honorCrLf && char == '\r' && peekOrNull() == '\n'
        val advanceLine = crLfPending || char in lineTerminators

        if (crLfPending) {
            reader.consume()
        }

        if (advanceLine) {
            ++line
            column = 1u
        } else {
            ++column
        }
    }
}

internal class FormatReader(reader: InputSource) : SourceReader by SourceReaderImpl(reader) {
    fun advance() {
        moveToNextChar()
        while (peekOrNull() == '/') {
            parseComment()
            moveToNextChar()
        }
    }

    private fun moveToNextChar() {
        while (!done) {
            val char = peek()
            if (char in Specification.WHITESPACE_CHARS || char in Specification.LINE_TERMINATORS) {
                consume()
            } else {
                return
            }
        }
    }

    private fun parseComment() {
        consume()
        val opener = consumeOrNull()
        if (opener == '/' || opener == '*') {
            while (!done) {
                val char = consumeOrNull()
                val multiLineDone = (opener == '*' && char == '*' && peekOrNull() == '/')
                val singleLineDone = (opener == '/' && char in Specification.LINE_TERMINATORS)

                if (multiLineDone) {
                    consume()
                }

                if (singleLineDone || multiLineDone) {
                    return
                }
            }
        }

        if (opener != '/') {
            throwTokenError()
        }
    }
}

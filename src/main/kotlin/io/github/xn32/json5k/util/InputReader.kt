package io.github.xn32.json5k.util

import java.io.InputStream

internal interface InputReader {
    fun peek(): Char
    fun consume(): Char
    val pos: ReaderPosition
    val done: Boolean
}

internal data class ReaderPosition(val line: UInt, val column: UInt) {
    override fun toString(): String = "$line:$column"
}

internal fun InputReader.peekOrNull(): Char? = if (!done) peek() else null
internal fun InputReader.consumeOrNull(): Char? = if (!done) consume() else null

internal fun InputReader.consumeWhile(predicate: (Char) -> Boolean): String {
    val builder = StringBuilder()
    while (!done && predicate(peek()))
        builder.append(consume())

    return builder.toString()
}


internal class StreamReader(
    inputStream: InputStream,
    private val lineTerminators: Set<Char>,
    private val honorCrLf: Boolean
) : InputReader {
    private val reader = inputStream.bufferedReader()
    private var next: Int = reader.read()

    private var line: UInt = 1u
    private var column: UInt = 1u

    override val pos: ReaderPosition get() = ReaderPosition(line, column)
    override val done: Boolean get() = next < 0

    override fun consume(): Char = peek().also {
        next = reader.read()
        if (it in lineTerminators) {
            handleLineTerminator(it)
        } else {
            ++column
        }
    }

    override fun peek(): Char {
        check(next >= 0)
        return next.toChar()
    }

    private fun handleLineTerminator(char: Char) {
        if (honorCrLf && char == '\r' && peekOrNull() == '\n')
            return
        column = 1u
        ++line
    }
}

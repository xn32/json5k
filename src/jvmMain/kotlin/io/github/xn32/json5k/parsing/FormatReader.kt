package io.github.xn32.json5k.parsing

import io.github.xn32.json5k.format.Specification.LINE_TERMINATORS
import io.github.xn32.json5k.format.Specification.WHITESPACE_CHARS
import io.github.xn32.json5k.throwTokenError
import java.io.InputStream

internal class FormatReader(stream: InputStream) : InputReader by StreamReader(stream, LINE_TERMINATORS, true) {
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
            if (char in WHITESPACE_CHARS || char in LINE_TERMINATORS) {
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
                val singleLineDone = (opener == '/' && char in LINE_TERMINATORS)

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

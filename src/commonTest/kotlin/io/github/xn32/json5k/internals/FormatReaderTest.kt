package io.github.xn32.json5k.internals

import io.github.xn32.json5k.EndOfFileError
import io.github.xn32.json5k.checkPosition
import io.github.xn32.json5k.parsing.FormatReader
import io.github.xn32.json5k.parsing.StringInputSource
import io.github.xn32.json5k.parsing.consumeAll
import io.github.xn32.json5k.parsing.consumeOrNull
import io.github.xn32.json5k.parsing.consumeWhile
import io.github.xn32.json5k.parsing.peekOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val SAMPLE_INPUT = "abc/def"

private fun readerFor(str: String): FormatReader = FormatReader(StringInputSource(str))

class FormatReaderTest {
    @Test
    fun inputSequenceConsumed() {
        val reader = readerFor(SAMPLE_INPUT)
        SAMPLE_INPUT.forEach { char ->
            assertEquals(char, reader.consume())
        }
    }

    @Test
    fun completedStreamIsDone() {
        val reader = readerFor(SAMPLE_INPUT)
        assertFalse(reader.done)
        repeat(SAMPLE_INPUT.length) {
            reader.consume()
        }

        assertNull(reader.peekOrNull())
        assertNull(reader.consumeOrNull())
        assertTrue(reader.done)
    }

    @Test
    fun consumeWhile() {
        val reader = readerFor(SAMPLE_INPUT)
        assertEquals("abc", reader.consumeWhile(Char::isLetter))
    }

    @Test
    fun peekChar() {
        val reader = readerFor(SAMPLE_INPUT)
        assertEquals(SAMPLE_INPUT.first(), reader.peek())
        assertEquals(SAMPLE_INPUT.first(), reader.consume())
    }

    @Test
    fun linePositionForSingleLine() {
        val reader = readerFor(SAMPLE_INPUT)

        var col = 1
        while (!reader.done) {
            reader.checkPosition(1, col)
            reader.consume()
            col++
        }
    }

    @Test
    fun linePositionForMultipleLines() {
        val reader = readerFor("first\nsecond\r\nthird")
        reader.consumeAll()
        reader.checkPosition(3, 6)
    }

    @Test
    fun specialLineTerminators() {
        val reader = readerFor("\nfirst\u2028\u2029second")
        reader.consumeAll()
        reader.checkPosition(4, 7)
    }

    @Test
    fun unclosedMultiLineComment() {
        val reader = readerFor("/*")
        val e = assertFailsWith<EndOfFileError> { reader.advance() }
        e.checkPosition(1, 3)
    }

    @Test
    fun multiLineComment() {
        val reader = readerFor("/* multi-line\r\n comment */\r\nx")
        reader.advance()
        reader.checkPosition(3, 1)
        assertEquals('x', reader.peek())
    }

    @Test
    fun singleLineComment() {
        val reader = readerFor("// single-line comment\ny")
        reader.advance()
        reader.checkPosition(2, 1)
        assertEquals('y', reader.peek())
    }

    @Test
    fun singleLineCommentAtEndOfFile() {
        val reader = readerFor("// comment\t")
        reader.advance()
        reader.checkPosition(1, 12)
        assertTrue(reader.done)
    }

    @Test
    fun positionAfterFinalLineTerminator() {
        val reader = readerFor("x\n").apply {
            advance()
            consume()
            advance()
        }

        assertTrue(reader.done)
        reader.checkPosition(2, 1)
    }
}

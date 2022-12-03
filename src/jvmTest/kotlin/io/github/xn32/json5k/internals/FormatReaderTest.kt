package io.github.xn32.json5k.internals

import io.github.xn32.json5k.EndOfFileError
import io.github.xn32.json5k.checkPosition
import io.github.xn32.json5k.parsing.FormatReader
import io.github.xn32.json5k.parsing.InputStreamSource
import io.github.xn32.json5k.parsing.StringInputSource
import io.github.xn32.json5k.parsing.consumeAll
import io.github.xn32.json5k.parsing.consumeOrNull
import io.github.xn32.json5k.parsing.consumeWhile
import io.github.xn32.json5k.parsing.peekOrNull
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val SAMPLE_INPUT = "abc/def"

private fun readerFor(stream: InputStream): FormatReader = FormatReader(InputStreamSource(stream))
private fun readerFor(str: String): FormatReader = FormatReader(StringInputSource(str))

class FormatReaderTest {
    @Test
    fun `input stream is interpreted as UTF-8`() {
        val chars = ByteArrayInputStream(intArrayOf(0x0, 0xF0, 0x9F, 0x8E, 0xBC).toByteArray())
        val reader = readerFor(chars)
        assertEquals("\u0000\ud83c\udfbc", reader.consumeAll())
    }

    @Test
    fun `input sequence is consumed correctly`() {
        val reader = readerFor(SAMPLE_INPUT)
        SAMPLE_INPUT.forEach { char ->
            assertEquals(char, reader.consume())
        }
    }

    @Test
    fun `completed stream is done`() {
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
    fun `consumeWhile helper works correctly`() {
        val reader = readerFor(SAMPLE_INPUT)
        assertEquals("abc", reader.consumeWhile(Char::isLetter))
    }

    @Test
    fun `first element can be peeked`() {
        val reader = readerFor(SAMPLE_INPUT)
        assertEquals(SAMPLE_INPUT.first(), reader.peek())
        assertEquals(SAMPLE_INPUT.first(), reader.consume())
    }

    @Test
    fun `line-counting reader works for single line`() {
        val reader = readerFor(SAMPLE_INPUT)

        var col = 1
        while (!reader.done) {
            reader.checkPosition(1, col)
            reader.consume()
            col++
        }
    }

    @Test
    fun `line-counting reader works for multiple lines`() {
        val reader = readerFor("first\nsecond\r\nthird")
        reader.consumeAll()
        reader.checkPosition(3, 6)
    }

    @Test
    fun `line-counting reader honors special line terminators`() {
        val reader = readerFor("\nfirst\u2028\u2029second")
        reader.consumeAll()
        reader.checkPosition(4, 7)
    }

    @Test
    fun `unclosed multi-line comment fails`() {
        val reader = readerFor("/*")
        val e = assertFailsWith<EndOfFileError> { reader.advance() }
        e.checkPosition(1, 3)
    }

    @Test
    fun `multi-line comment is ignored`() {
        val reader = readerFor("/* multi-line\r\n comment */\r\nx")
        reader.advance()
        reader.checkPosition(3, 1)
        assertEquals('x', reader.peek())
    }

    @Test
    fun `single-line comment before end of file is ignored`() {
        val reader = readerFor("// single-line comment\ny")
        reader.advance()
        reader.checkPosition(2, 1)
        assertEquals('y', reader.peek())
    }

    @Test
    fun `single-line comment at end of file is ignored`() {
        val reader = readerFor("// comment\t")
        reader.advance()
        reader.checkPosition(1, 12)
        assertTrue(reader.done)
    }

    @Test
    fun `position after final line terminator is correct`() {
        val reader = readerFor("x\n").apply {
            advance()
            consume()
            advance()
        }

        assertTrue(reader.done)
        reader.checkPosition(2, 1)
    }
}

private fun IntArray.toByteArray(): ByteArray = map(Int::toByte).toByteArray()

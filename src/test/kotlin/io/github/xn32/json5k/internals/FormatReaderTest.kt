package io.github.xn32.json5k.internals

import io.github.xn32.json5k.EndOfFileError
import io.github.xn32.json5k.checkPosition
import io.github.xn32.json5k.parsing.FormatReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private fun readerFor(input: String): FormatReader = FormatReader(input.byteInputStream())

class FormatReaderTest {
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

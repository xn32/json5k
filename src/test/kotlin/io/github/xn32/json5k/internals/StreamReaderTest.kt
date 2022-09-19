package io.github.xn32.json5k.internals

import io.github.xn32.json5k.checkPosition
import io.github.xn32.json5k.parsing.StreamReader
import io.github.xn32.json5k.parsing.consumeOrNull
import io.github.xn32.json5k.parsing.consumeWhile
import io.github.xn32.json5k.parsing.peekOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val SAMPLE_INPUT = "abc/def"
private fun getSampleReader() = getStreamReader(SAMPLE_INPUT)

class StreamReaderTest {
    @Test
    fun `input sequence is consumed correctly`() {
        val reader = getSampleReader()
        SAMPLE_INPUT.forEach { char ->
            assertEquals(char, reader.consume())
        }
    }

    @Test
    fun `completed stream is done`() {
        val reader = getSampleReader()
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
        val reader = getSampleReader()
        assertEquals("abc", reader.consumeWhile(Char::isLetter))
    }

    @Test
    fun `first element can be peeked`() {
        val reader = getSampleReader()
        assertEquals(SAMPLE_INPUT.first(), reader.peek())
        assertEquals(SAMPLE_INPUT.first(), reader.consume())
    }

    @Test
    fun `line-counting reader works for single line`() {
        val reader = getSampleReader()

        var col = 1
        while (!reader.done) {
            reader.checkPosition(1, col)
            reader.consume()
            col++
        }
    }

    @Test
    fun `line-counting reader works for multiple lines`() {
        val reader = getStreamReader("first\nsecond\r\nthird")
        while (!reader.done) {
            reader.consume()
        }

        reader.checkPosition(3, 6)
    }

    @Test
    fun `combined CRLF handling can be disabled`() {
        val reader = getStreamReader("first\r\nsecond", honorCrLf = false)
        while (!reader.done) {
            reader.consume()
        }

        reader.checkPosition(3)
    }

    @Test
    fun `line-counting reader honors explicit terminators`() {
        val reader = getStreamReader("\nfirst\u2028second", lineTerminators = setOf('\u2028'))
        while (!reader.done) {
            reader.consume()
        }

        reader.checkPosition(2, 7)
    }
}

private fun getStreamReader(
    inputString: String,
    lineTerminators: Set<Char> = setOf('\r', '\n'),
    honorCrLf: Boolean = true
) = StreamReader(
    inputString.byteInputStream(),
    lineTerminators,
    honorCrLf
)

package io.github.xn32.json5k.internals

import io.github.xn32.json5k.checkPosition
import io.github.xn32.json5k.parsing.*
import java.io.ByteArrayInputStream
import kotlin.test.*

private const val SAMPLE_INPUT = "abc/def"

class StreamReaderTest {
    @Test
    fun `input stream is interpreted as UTF-8`() {
        val chars = ByteArrayInputStream(intArrayOf(0x0, 0xF0, 0x9F, 0x8E, 0xBC).toByteArray())
        val reader = StreamReader(chars, lineTerminators = setOf(), honorCrLf = false)
        assertEquals("\u0000\ud83c\udfbc", reader.consumeAll())
    }

    @Test
    fun `input sequence is consumed correctly`() {
        val reader = getStreamReader(SAMPLE_INPUT)
        SAMPLE_INPUT.forEach { char ->
            assertEquals(char, reader.consume())
        }
    }

    @Test
    fun `completed stream is done`() {
        val reader = getStreamReader(SAMPLE_INPUT)
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
        val reader = getStreamReader(SAMPLE_INPUT)
        assertEquals("abc", reader.consumeWhile(Char::isLetter))
    }

    @Test
    fun `first element can be peeked`() {
        val reader = getStreamReader(SAMPLE_INPUT)
        assertEquals(SAMPLE_INPUT.first(), reader.peek())
        assertEquals(SAMPLE_INPUT.first(), reader.consume())
    }

    @Test
    fun `line-counting reader works for single line`() {
        val reader = getStreamReader(SAMPLE_INPUT)

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

private fun IntArray.toByteArray(): ByteArray = map(Int::toByte).toByteArray()

private fun getStreamReader(
    inputString: String,
    lineTerminators: Set<Char> = setOf('\r', '\n'),
    honorCrLf: Boolean = true
) = StreamReader(
    inputString.byteInputStream(Charsets.UTF_8),
    lineTerminators,
    honorCrLf
)

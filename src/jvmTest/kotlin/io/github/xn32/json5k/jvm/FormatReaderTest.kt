package io.github.xn32.json5k.jvm

import io.github.xn32.json5k.InputStreamSource
import io.github.xn32.json5k.parsing.FormatReader
import io.github.xn32.json5k.parsing.consumeAll
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.test.assertEquals

private fun readerFor(stream: InputStream): FormatReader = FormatReader(InputStreamSource(stream))

class FormatReaderTest {
    @Test
    fun inputStreamEncoding() {
        val chars = ByteArrayInputStream(intArrayOf(0x0, 0xF0, 0x9F, 0x8E, 0xBC).toByteArray())
        val reader = readerFor(chars)
        assertEquals("\u0000\ud83c\udfbc", reader.consumeAll())
    }
}

private fun IntArray.toByteArray(): ByteArray = map(Int::toByte).toByteArray()

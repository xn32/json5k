package io.github.xn32.json5k.jvm

import io.github.xn32.json5k.CharError
import io.github.xn32.json5k.Json5
import io.github.xn32.json5k.decodeFromStream
import io.github.xn32.json5k.checkPosition
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class DeserializationTest {
    @Test
    fun basicStreamSerialization() {
        val str = "null"
        val num = str.byteInputStream().use {
            Json5.decodeFromStream<Int?>(it)
        }

        assertNull(num)
    }

    @Test
    fun streamReadToEnd() {
        val str = "{}x"
        str.byteInputStream().use {
            val error = assertFailsWith<CharError> {
                Json5.decodeFromStream(it)
            }

            error.checkPosition(1, 3)
            assertEquals('x', error.char)
        }
    }
}

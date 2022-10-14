package io.github.xn32.json5k.binding

import io.github.xn32.json5k.CharError
import io.github.xn32.json5k.DecodingError
import io.github.xn32.json5k.DuplicateKeyError
import io.github.xn32.json5k.Json5
import io.github.xn32.json5k.MissingFieldError
import io.github.xn32.json5k.UnexpectedValueError
import io.github.xn32.json5k.UnknownKeyError
import io.github.xn32.json5k.checkPosition
import io.github.xn32.json5k.decodeFromStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

private inline fun <reified T> decode(input: String): T = Json5.decodeFromString(input)

class DeserializationTest {
    @Test
    fun `top-level value is decoded`() {
        assertEquals(40, decode("40"))
        assertEquals(-33, decode("-33"))
        assertEquals(13.25f, decode("13.25"))
        assertEquals(11.0, decode("11.0"))
        assertEquals(true, decode("true"))
        assertEquals(null, decode<Int?>("null"))
        assertEquals(Double.POSITIVE_INFINITY, decode("Infinity"))
        assertEquals(Double.POSITIVE_INFINITY, decode("+Infinity"))
        assertEquals(Double.NEGATIVE_INFINITY, decode("-Infinity"))
        assertEquals(Double.NaN, decode("NaN"))
        assertEquals("abc", decode("'abc'"))
    }

    @Test
    fun `deserialization of unsigned numbers works`() {
        @Serializable
        data class UnsignedWrapper(val value: UByte)
        assertEquals(UnsignedWrapper(255u), decode("{ value: 255 }"))
        val error = assertFails { decode<UnsignedWrapper>("{ value: -1 }") }
        assertContains(error.message!!, "range [0..255]")
    }

    @Test
    fun `nullable top-level value is decoded`() {
        assertEquals(50, decode<Int?>("50"))
        assertEquals(null, decode<Int?>("null"))
    }

    @Test
    fun `primitive array is decoded`() {
        assertContentEquals(intArrayOf(4, 5, 6), decode("[4,5,6]"))
        assertContentEquals(longArrayOf(-10, 20), decode("[-10, 20]"))
    }

    @Test
    fun `top-level class is decoded`() {
        @Serializable
        data class Class(val x: String)
        assertEquals(Class("abc"), decode("{ x: 'abc' }"))
    }

    @Test
    fun `duplicate key in top-level class are prohibited by default`() {
        @Serializable
        data class Class(val first: Int, val second: Long)
        assertFailsWith<DuplicateKeyError> {
            decode<Class>("{ first: 10, second: 20, first: 30 }")
        }
    }

    @Test
    fun `top-level map is decoded`() {
        assertEquals(mapOf("first" to 10, "second" to 433), decode("{ first: 10, second: 433 }"))
    }

    @Test
    fun `duplicate key in top-level map is detected`() {
        val error = assertFailsWith<DuplicateKeyError> {
            decode<Map<String, Short>>("{ a: 10, a: 10 }")
        }

        assertEquals(10u, error.column)
        assertEquals("a", error.key)
    }

    @Test
    fun `literals can be used as map keys`() {
        assertEquals(mapOf("null" to 42), decode("{ null: 42 }"))
        assertEquals(mapOf("Infinity" to 10), decode("{ Infinity: 10 }"))
    }

    @Test
    fun `unsupported map key types throw an error`() {
        assertFailsWith<UnsupportedOperationException> {
            decode<Map<String?, Int>>("{ abc: 10 }")
        }

        assertFailsWith<UnsupportedOperationException> {
            decode<Map<Boolean, Int>>("{ true: 10 }")
        }

    }

    @Test
    fun `nested maps are supported`() {
        assertEquals(mapOf("a" to mapOf("x" to 1)), decode("{ a: { x: 1 } }"))
    }

    @Test
    fun `implicit conversion to integers is prohibited`() {
        assertFailsWith<DecodingError> {
            decode<Long>("10.0")
        }

        assertFailsWith<DecodingError> {
            decode<Short>("1e2")
        }
    }

    @Test
    fun `top-level object is decoded`() {
        @Serializable
        data class SingletonWrapper(val obj: Singleton)

        assertEquals(SingletonWrapper(Singleton), decode("{ obj: {} }"))

        assertFailsWith<DecodingError> {
            decode<SingletonWrapper>("{ obj: { unknown: 0 } }")
        }

        assertFailsWith<DecodingError> {
            decode<SingletonWrapper>("{}")
        }
    }

    @Test
    fun `top-level polymorphic types are supported`() {
        assertEquals(
            DefaultImpl(30), decode<DefaultInterface>("{ type: 'impl', integer: 30 }")
        )
    }

    @Test
    fun `inner polymorphic types are supported`() {
        assertEquals(
            Wrapper<DefaultInterface>(DefaultImpl(10)),
            decode("{ obj: { type: 'impl', integer: 10 } }")
        )
    }

    @Test
    fun `unknown class discriminator value is detected`() {
        assertFailsWith<DecodingError> {
            decode<DefaultInterface>("{ type: 'unknown' }")
        }.checkPosition(1, 1)
    }

    @Test
    fun `unknown object key is reported`() {
        @Serializable
        data class Entity(val x: Int)
        assertFailsWith<DecodingError> {
            decode<Entity>("{ abc: 10 }")
        }.checkPosition(1, 3)
    }

    @Test
    fun `repeated polymorphic discriminator is recognized as error`() {
        assertFailsWith<DecodingError> {
            decode<DefaultInterface>("{ type: 'impl', type: 'unknown' }")
        }.checkPosition(1, 17)
    }

    @Test
    fun `custom class discriminator is supported`() {
        assertEquals(
            CustomImpl(null),
            decode<CustomInterface>("{ category: 'impl', name: null }")
        )
    }

    @Test
    fun `custom class discriminator overwrites default discriminator`() {
        val format = Json5 { classDiscriminator = "xyz" }
        assertEquals(
            CustomImpl(null),
            format.decodeFromString<CustomInterface>("{ category: 'impl', name: null }")
        )
    }

    @Test
    fun `custom class discriminator is considered`() {
        val format = Json5 { classDiscriminator = "kind" }
        assertEquals(
            DefaultImpl(43),
            format.decodeFromString<DefaultInterface>("{ kind: 'impl', integer: 43 }")
        )
    }

    @Test
    fun `stream deserialization works`() {
        val str = "null"
        val num = str.byteInputStream().use {
            Json5.decodeFromStream<Int?>(it)
        }

        assertNull(num)
    }

    @Test
    fun `input stream is read to the end`() {
        val str = "{}x"
        str.byteInputStream().use {
            val err = assertFailsWith<CharError> {
                Json5.decodeFromStream(it)
            }

            assertEquals('x', err.char)
        }
    }

    @Test
    fun `missing field is reported`() {
        @Serializable
        data class Dummy(val a: Int, val b: Int)

        val err = assertFailsWith<MissingFieldError> {
            decode<Dummy>("{ b: 10 }")
        }

        assertEquals("a", err.key)
    }

    @Test
    fun `duplicate class key is reported`() {
        @Serializable
        data class Obj(val x: Int)

        val err = assertFailsWith<DuplicateKeyError> {
            decode<Obj>("{ x: 5, x: 10 }")
        }

        assertEquals("x", err.key)
    }

    @Test
    fun `duplicate map key is reported`() {
        val err = assertFailsWith<DuplicateKeyError> {
            decode<Map<String, Int>>("{ a: 10, b: 20, a: 20 }")
        }

        assertEquals("a", err.key)
    }

    @Test
    fun `unknown class key is reported`() {
        @Serializable
        data class Obj(val x: Int)

        val err = assertFailsWith<UnknownKeyError> {
            decode<Obj>("{ x: 5, y: 10 }")
        }

        assertEquals("y", err.key)
    }

    @Test
    fun `value errors are reported`() {
        val err = assertFailsWith<UnexpectedValueError> {
            decode<Byte>("5000")
        }

        assertContains(err.message, "in range [-128..127]")
    }
}

package io.github.xn32.json5k.binding

import io.github.xn32.json5k.CharError
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
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

private inline fun <reified T> decode(input: String): T = Json5.decodeFromString(input)

class DeserializationTest {
    @Test
    fun `top-level value is decoded`() {
        assertEquals(40, decode("40"))
        assertEquals(-33, decode("-33"))
        assertEquals(-2000000000, decode("-2000000000"))
        assertEquals(3000000000u, decode("3000000000"))
        assertEquals(13.25f, decode("13.25"))
        assertEquals(11.0, decode("11.0"))
        assertEquals(true, decode("true"))
        assertEquals(Double.POSITIVE_INFINITY, decode("Infinity"))
        assertEquals(Double.POSITIVE_INFINITY, decode("+Infinity"))
        assertEquals(Double.NEGATIVE_INFINITY, decode("-Infinity"))
        assertEquals(Double.NaN, decode("NaN"))
        assertEquals("abc", decode("'abc'"))
        assertEquals("\ud834\udd1e", decode("'\ud834\udd1e'"))
    }

    @Test
    fun `nullable top-level value is decoded`() {
        assertEquals(50, decode<Int?>("50"))
        assertEquals(null, decode<Int?>("null"))
        assertEquals(50u, decode<UInt?>("50"))
        assertEquals(null, decode<UInt?>("null"))
    }

    @Test
    fun `list is decoded`() {
        assertContentEquals(listOf(3, 6, 7), decode<List<Int>>("[3,6,7]"))
    }

    @Test
    fun `primitive array is decoded`() {
        assertContentEquals(intArrayOf(-4, 5, 2000000000), decode("[-4,5,2000000000]"))
        assertContentEquals(longArrayOf(-10, 3000000000), decode("[-10,3000000000]"))

        @OptIn(ExperimentalUnsignedTypes::class)
        assertContentEquals(uintArrayOf(10u, 3000000000u), decode("[10, 3000000000]"))
    }

    @Test
    fun `deserialization of unsigned numbers works`() {
        @Serializable
        data class UnsignedWrapper(val value: UByte)

        assertEquals(UnsignedWrapper(255u), decode("{ value: 255 }"))
        val error = assertFailsWith<UnexpectedValueError> {
            decode<UnsignedWrapper>("{ value: -1 }")
        }

        assertContains(error.message, "unsigned integer in range [0..255] expected at position")
        error.checkPosition(1, 10)
    }

    @Test
    fun `missing value is reported`() {
        val error = assertFailsWith<UnexpectedValueError> {
            decode<Wrapper<Int>>("{ obj: { a: 10 }}")
        }

        assertContains(error.message, "integer expected at position")
        error.checkPosition(1, 8)
    }

    @Test
    fun `missing object is reported`() {
        val error = assertFailsWith<UnexpectedValueError> {
            decode<Map<String, Int>>("40")
        }

        assertContains(error.message, "object expected at position")
        error.checkPosition(1, 1)
    }

    @Test
    fun `missing array is reported`() {
        val error = assertFailsWith<UnexpectedValueError> {
            decode<List<Int>>("true")
        }

        assertContains(error.message, "array expected at position")
        error.checkPosition(1, 1)
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

        val error = assertFailsWith<DuplicateKeyError> {
            decode<Class>("{ first: 10, second: 20, first: 30 }")
        }

        assertContains(error.message, "duplicate key 'first' at position")
        assertEquals("first", error.key)
        error.checkPosition(1, 26)
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

        assertContains(error.message, "duplicate key 'a' at position")
        assertEquals("a", error.key)
        error.checkPosition(1, 10)
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
        val floatError = assertFailsWith<UnexpectedValueError> {
            decode<Long>("10.0")
        }

        val expError = assertFailsWith<UnexpectedValueError> {
            decode<Short>("1e2")
        }

        listOf(floatError, expError).forEach { error ->
            assertContains(error.message, "integer expected at position")
            error.checkPosition(1, 1)
        }
    }

    @Test
    fun `singleton object is decoded`() {
        @Serializable
        data class SingletonWrapper(val obj: Singleton)

        assertEquals(SingletonWrapper(Singleton), decode("{ obj: {} }"))

        val error = assertFailsWith<UnknownKeyError> {
            decode<SingletonWrapper>("{ obj: { unknown: 0 } }")
        }

        assertContains(error.message, "unknown key 'unknown' at position")
        assertEquals("unknown", error.key)
        error.checkPosition(1, 10)
    }

    @Test
    fun `top-level polymorphic types are supported`() {
        assertEquals(
            DefaultImpl(30), decode<DefaultInterface>("{ type: 'valid', integer: 30 }")
        )
    }

    @Test
    fun `inner polymorphic types are supported`() {
        assertEquals(
            Wrapper<DefaultInterface>(DefaultImpl(10)),
            decode("{ obj: { type: 'valid', integer: 10 } }")
        )
    }

    @Test
    fun `unknown class discriminator value is detected`() {
        val error = assertFailsWith<UnexpectedValueError> {
            decode<DefaultInterface>("{ type: 'unknown' }")
        }

        assertContains(error.message, "unknown class name 'unknown' at position")
        error.checkPosition(1, 9)
    }

    @Test
    fun `unknown object key is reported`() {
        @Serializable
        data class Entity(val x: Int)

        val error = assertFailsWith<UnknownKeyError> {
            decode<Entity>("{ abc: 10 }")
        }

        assertContains(error.message, "unknown key 'abc' at position")
        assertEquals("abc", error.key)
        error.checkPosition(1, 3)
    }

    @Test
    fun `repeated polymorphic discriminator is recognized as error`() {
        val error = assertFailsWith<DuplicateKeyError> {
            decode<DefaultInterface>("{ type: 'valid', type: 'valid' }")
        }

        assertContains(error.message, "duplicate key 'type' at position")
        assertEquals("type", error.key)
        error.checkPosition(1, 18)
    }

    @Test
    fun `custom class discriminator is supported`() {
        assertEquals(
            CustomImpl(null),
            decode<CustomInterface>("{ category: 'main', name: null }")
        )
    }

    @Test
    fun `custom class discriminator overwrites default discriminator`() {
        val json5 = Json5 {
            classDiscriminator = "xyz"
        }

        assertEquals(
            CustomImpl(null),
            json5.decodeFromString<CustomInterface>("{ category: 'main', name: null }")
        )
    }

    @Test
    fun `custom class discriminator is considered`() {
        val json5 = Json5 {
            classDiscriminator = "kind"
        }

        assertEquals(
            DefaultImpl(43),
            json5.decodeFromString<DefaultInterface>("{ kind: 'valid', integer: 43 }")
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
            val error = assertFailsWith<CharError> {
                Json5.decodeFromStream(it)
            }

            error.checkPosition(1, 3)
            assertEquals('x', error.char)
        }
    }

    @Test
    fun `missing field is reported`() {
        @Serializable
        data class Dummy(val a: Int, val b: Int)

        val error = assertFailsWith<MissingFieldError> {
            decode<Dummy>("{ b: 10 }")
        }

        assertContains(error.message, "missing field 'a' in object at position")
        assertEquals("a", error.key)
        error.checkPosition(1, 1)
    }

    @Test
    fun `missing field is associated with the correct hierarchy level`() {
        @Serializable
        data class Inner(val x: Int)

        @Serializable
        data class Outer(val inner: Inner, val y: Int)

        val outerError = assertFailsWith<MissingFieldError> {
            decode<Outer>("{ inner: { x: 10 } }")
        }

        assertContains(outerError.message, "missing field 'y' in object at position")
        assertEquals("y", outerError.key)
        outerError.checkPosition(1, 1)

        val innerError = assertFailsWith<MissingFieldError> {
            decode<Outer>("{ inner: {}, y: 4 }")
        }

        assertContains(innerError.message, "missing field 'x' in object at position")
        assertEquals("x", innerError.key)
        innerError.checkPosition(1, 10)
    }

    @Test
    fun `missing field in polymorphic class is detected`() {
        val error = assertFailsWith<MissingFieldError> {
            decode<DefaultInterface>("{ type: 'valid' }")
        }

        assertContains(error.message, "missing field 'integer' in object at position")
        assertEquals("integer", error.key)
        error.checkPosition(1, 1)
    }

    @Test
    fun `duplicate class key is reported`() {
        @Serializable
        data class Obj(val x: Int)

        val error = assertFailsWith<DuplicateKeyError> {
            decode<Obj>("{ x: 5, x: 10 }")
        }

        assertContains(error.message, "duplicate key 'x' at position")
        assertEquals("x", error.key)
        error.checkPosition(1, 9)
    }

    @Test
    fun `duplicate map key is reported`() {
        val error = assertFailsWith<DuplicateKeyError> {
            decode<Map<String, Int>>("{ a: 10, b: 20, a: 20 }")
        }

        assertContains(error.message, "duplicate key 'a' at position")
        assertEquals("a", error.key)
        error.checkPosition(1, 17)
    }

    @Test
    fun `unknown class key is reported`() {
        @Serializable
        data class Obj(val x: Int)

        val error = assertFailsWith<UnknownKeyError> {
            decode<Obj>("{ x: 5, y: 10 }")
        }

        assertContains(error.message, "unknown key 'y' at position")
        assertEquals("y", error.key)
        error.checkPosition(1, 9)
    }

    @Test
    fun `value errors are reported`() {
        val error = assertFailsWith<UnexpectedValueError> {
            decode<Byte>("5000")
        }

        assertContains(error.message, "signed integer in range [-128..127] expected at position")
        error.checkPosition(1, 1)
    }
}

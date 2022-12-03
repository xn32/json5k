package io.github.xn32.json5k.binding

import io.github.xn32.json5k.DuplicateKeyError
import io.github.xn32.json5k.Json5
import io.github.xn32.json5k.MissingFieldError
import io.github.xn32.json5k.UnexpectedValueError
import io.github.xn32.json5k.UnknownKeyError
import io.github.xn32.json5k.checkPosition
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private inline fun <reified T> decode(input: String): T = Json5.decodeFromString(input)

class DeserializationTest {
    @Test
    fun `signed value is decoded`() {
        assertEquals(Byte.MAX_VALUE, decode("127"))
        assertEquals(Byte.MIN_VALUE, decode("-128"))
        assertEquals(Short.MAX_VALUE, decode("32767"))
        assertEquals(Short.MIN_VALUE, decode("-32768"))
        assertEquals(Int.MAX_VALUE, decode("2147483647"))
        assertEquals(Int.MIN_VALUE, decode("-2147483648"))
        assertEquals(Long.MAX_VALUE, decode("9223372036854775807"))
        assertEquals(Long.MIN_VALUE, decode("-9223372036854775808"))
    }

    @Test
    fun `unsigned value is decoded`() {
        assertEquals(UByte.MAX_VALUE, decode("255"))
        assertEquals(UShort.MAX_VALUE, decode("65535"))
        assertEquals(UInt.MAX_VALUE, decode("4294967295"))
        assertEquals(ULong.MAX_VALUE, decode("18446744073709551615"))
    }

    @Test
    fun `floating-point value is decoded`() {
        assertEquals(13.25f, decode("13.25"))
        assertEquals(11.0, decode("11.0"))
        assertEquals(Double.POSITIVE_INFINITY, decode("Infinity"))
        assertEquals(Float.POSITIVE_INFINITY, decode("Infinity"))
        assertEquals(Double.POSITIVE_INFINITY, decode("+Infinity"))
        assertEquals(Float.POSITIVE_INFINITY, decode("+Infinity"))
        assertEquals(Double.NEGATIVE_INFINITY, decode("-Infinity"))
        assertEquals(Float.NEGATIVE_INFINITY, decode("-Infinity"))
        assertEquals(Double.NaN, decode("NaN"))
        assertEquals(Float.NaN, decode("NaN"))
    }

    @Test
    fun `boolean value is decoded`() {
        assertEquals(true, decode("true"))
        assertEquals(false, decode("false"))
    }

    @Test
    fun `enum value is decoded`() {
        assertEquals(DummyEnum.ITEM, decode("'ITEM'"))
    }

    @Test
    fun `value class is decoded`() {
        assertEquals(StringWrapper("wrapped"), decode("'wrapped'"))
        assertEquals(Wrapper(StringWrapper("str")), decode("{obj:\"str\"}"))
    }

    @Test
    fun `string is decoded`() {
        assertEquals('x', decode("'x'"))
        assertEquals("abc", decode("'abc'"))
        assertEquals("\ud834\udd1e", decode("'\ud834\udd1e'"))
    }

    @Test
    fun `nullable value is decoded`() {
        assertEquals(50, decode<Int?>("50"))
        assertEquals(null, decode<Int?>("null"))
        assertEquals(50u, decode<UInt?>("50"))
        assertEquals(null, decode<UInt?>("null"))
    }

    @Test
    fun `list of integers is decoded`() {
        assertEquals(listOf(3, 6, 7), decode("[3,6,7]"))
    }

    @Test
    fun `primitive array is decoded`() {
        assertContentEquals(
            intArrayOf(Int.MIN_VALUE, Int.MAX_VALUE),
            decode("[-2147483648,2147483647]")
        )

        assertContentEquals(
            longArrayOf(Long.MIN_VALUE, Long.MAX_VALUE),
            decode("[-9223372036854775808,9223372036854775807]")
        )

        @OptIn(ExperimentalUnsignedTypes::class)
        assertContentEquals(
            uintArrayOf(0u, UInt.MAX_VALUE),
            decode("[0,4294967295]")
        )
    }

    @Test
    fun `value container is decoded`() {
        assertEquals(
            SignedContainer(Byte.MAX_VALUE, Short.MAX_VALUE, Int.MAX_VALUE, Long.MAX_VALUE),
            decode("{byte:127,short:32767,int:2147483647,long:9223372036854775807}"),
        )

        assertEquals(
            UnsignedContainer(UByte.MAX_VALUE, UShort.MAX_VALUE, UInt.MAX_VALUE, ULong.MAX_VALUE),
            decode("{byte:255,short:65535,int:4294967295,long:18446744073709551615}")
        )

        assertEquals(
            FloatingPointContainer(.0f, Double.POSITIVE_INFINITY),
            decode("{float:0.0,double:Infinity}")
        )

        assertEquals(
            MiscContainer('x', "xyz", true, DummyEnum.ITEM),
            decode("{char:'x',str:'xyz',bool:true,enum:'ITEM'}")
        )
    }

    @Test
    fun `unexpected enum value is reported`() {
        val error = assertFailsWith<UnexpectedValueError> {
            decode<DummyEnum>("'UNKNOWN'")
        }

        assertContains(error.message, "unexpected enum value 'UNKNOWN' at position")
        error.checkPosition(1, 1)
    }

    @Test
    fun `length of character variables is checked`() {
        val stringError = assertFailsWith<UnexpectedValueError> {
            decode<Char>("'ab'")
        }

        val emptyError = assertFailsWith<UnexpectedValueError> {
            decode<Char>("''")
        }

        for (error in listOf(stringError, emptyError)) {
            assertContains(error.message, "single-character string expected at position")
            error.checkPosition(1, 1)
        }
    }

    @Test
    fun `range check for unsigned integers works`() {
        val error = assertFailsWith<UnexpectedValueError> {
            decode<UByte>("256")
        }

        assertContains(error.message, "unsigned integer in range [0..255] expected at position")
        error.checkPosition(1, 1)
    }

    @Test
    fun `range check for signed integers works`() {
        val minError = assertFailsWith<UnexpectedValueError> {
            decode<Byte>("-129")
        }

        val maxError = assertFailsWith<UnexpectedValueError> {
            decode<Byte>("128")
        }

        assertContains(minError.message, "signed integer in range [-128..127] expected at position")
        assertContains(maxError.message, "signed integer in range [-128..127] expected at position")

        for (error in listOf(minError, maxError)) {
            maxError.checkPosition(1, 1)
        }
    }

    @Test
    fun `unexpected parser value is reported`() {
        val intError = assertFailsWith<UnexpectedValueError> {
            decode<Wrapper<Int>>("{ obj: { a: 10 }}")
        }

        val unsignedIntError = assertFailsWith<UnexpectedValueError> {
            decode<Wrapper<UInt>>("{ obj: -10 }")
        }

        val floatError = assertFailsWith<UnexpectedValueError> {
            decode<Wrapper<Double>>("{ obj: true }")
        }

        val boolError = assertFailsWith<UnexpectedValueError> {
            decode<Wrapper<Boolean>>("{ obj: null }")
        }

        val stringError = assertFailsWith<UnexpectedValueError> {
            decode<Wrapper<String>>("{ obj: [1, 2] }")
        }

        assertContains(intError.message, "integer expected at position")
        assertContains(unsignedIntError.message, "unsigned integer expected at position")
        assertContains(floatError.message, "floating-point number expected at position")
        assertContains(boolError.message, "boolean value expected at position")
        assertContains(stringError.message, "string literal expected at position")

        for (error in listOf(intError, floatError, boolError, stringError)) {
            error.checkPosition(1, 8)
        }
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
    fun `duplicate key in top-level map is reported`() {
        val error = assertFailsWith<DuplicateKeyError> {
            decode<Map<String, Short>>("{ a: 10, a: 10 }")
        }

        assertContains(error.message, "duplicate key 'a' at position")
        assertEquals("a", error.key)
        error.checkPosition(1, 10)
    }

    @Test
    fun `unsupported map key type causes error`() {
        fun assertUnsupported(block: () -> Unit) {
            assertFailsWith<UnsupportedOperationException>(block = block)
        }

        assertUnsupported { decode<Map<DummyEnum, Int>>("{a:0}") }
        assertUnsupported { decode<Map<Wrapper<Int>, Int>>("{a:0}") }
        assertUnsupported { decode<Map<Int?, Int>>("{a:0}") }
        assertUnsupported { decode<Map<Char, Int>>("{a:0}") }
        assertUnsupported { decode<Map<Byte, Int>>("{a:0}") }
        assertUnsupported { decode<Map<Short, Int>>("{a:0}") }
        assertUnsupported { decode<Map<Int, Int>>("{a:0}") }
        assertUnsupported { decode<Map<Long, Int>>("{a:0}") }
        assertUnsupported { decode<Map<Float, Int>>("{a:0}") }
        assertUnsupported { decode<Map<Double, Int>>("{a:0}") }
        assertUnsupported { decode<Map<Boolean, Int>>("{a:0}") }
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
    fun `flat polymorphic class is decoded`() {
        assertEquals(
            FlatDefaultImpl(10, 20), decode<DefaultInterface>("{ a: 10, type: 'flat', b: 20 }")
        )
    }

    @Test
    fun `nested polymorphic class is decoded`() {
        assertEquals(
            NestedDefaultImpl(0, Wrapper(5)), decode<DefaultInterface>("{ x: { obj: 5 }, type: 'nested', a: 0 }")
        )
    }

    @Test
    fun `inner polymorphic class is decoded`() {
        assertEquals(
            Wrapper<DefaultInterface>(FlatDefaultImpl(10)),
            decode("{ obj: { type: 'flat', a: 10 } }")
        )
    }

    @Test
    fun `unknown class discriminator value is reported`() {
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
            decode<DefaultInterface>("{ type: 'flat', type: 'flat' }")
        }

        assertContains(error.message, "duplicate key 'type' at position")
        assertEquals("type", error.key)
        error.checkPosition(1, 17)
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
            FlatDefaultImpl(15),
            json5.decodeFromString<DefaultInterface>("{ kind: 'flat', a: 15 }")
        )
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
    fun `missing field in polymorphic class is reported`() {
        val error = assertFailsWith<MissingFieldError> {
            decode<DefaultInterface>("{ type: 'flat' }")
        }

        assertContains(error.message, "missing field 'a' in object at position")
        assertEquals("a", error.key)
        error.checkPosition(1, 1)
    }

    @Test
    fun `missing class discriminator is reported`() {
        val error = assertFailsWith<MissingFieldError> {
            decode<DefaultInterface>("{}")
        }

        assertContains(error.message, "missing field 'type' in object")
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
    fun `class with contextual serializer is decoded`() {
        val json5 = Json5 {
            serializersModule = SerializersModule {
                contextual(ColorAsStringSerializer)
            }
        }

        assertEquals(Color(0xff0000), json5.decodeFromString("'0xff0000'"))
    }
}

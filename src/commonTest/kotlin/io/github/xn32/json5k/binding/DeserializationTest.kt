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
    fun signedValue() {
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
    fun unsignedValue() {
        assertEquals(UByte.MAX_VALUE, decode("255"))
        assertEquals(UShort.MAX_VALUE, decode("65535"))
        assertEquals(UInt.MAX_VALUE, decode("4294967295"))
        assertEquals(ULong.MAX_VALUE, decode("18446744073709551615"))
    }

    @Test
    fun floatingPointValue() {
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
    fun booleanValue() {
        assertEquals(true, decode("true"))
        assertEquals(false, decode("false"))
    }

    @Test
    fun enumValue() {
        assertEquals(DummyEnum.ITEM, decode("'ITEM'"))
    }

    @Test
    fun valueClass() {
        assertEquals(InlineWrapper("wrapped"), decode("'wrapped'"))
        assertEquals(Wrapper(InlineWrapper("str")), decode("{obj:\"str\"}"))
    }

    @Test
    fun stringValue() {
        assertEquals('x', decode("'x'"))
        assertEquals("abc", decode("'abc'"))
        assertEquals("\ud834\udd1e", decode("'\ud834\udd1e'"))
    }

    @Test
    fun nullableValue() {
        assertEquals(50, decode<Int?>("50"))
        assertEquals(null, decode<Int?>("null"))
        assertEquals(50u, decode<UInt?>("50"))
        assertEquals(null, decode<UInt?>("null"))
    }

    @Test
    fun listOfIntegers() {
        assertEquals(listOf(3, 6, 7), decode("[3,6,7]"))
    }

    @Test
    fun primitiveArray() {
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
    fun valueContainers() {
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
    fun unexpectedEnumValue() {
        val error = assertFailsWith<UnexpectedValueError> {
            decode<DummyEnum>("'UNKNOWN'")
        }

        assertContains(error.violation, "unexpected enum value 'UNKNOWN'")
        error.checkPosition(1, 1)
    }

    @Test
    fun lengthOfCharValue() {
        val stringError = assertFailsWith<UnexpectedValueError> {
            decode<Char>("'ab'")
        }

        val emptyError = assertFailsWith<UnexpectedValueError> {
            decode<Char>("''")
        }

        for (error in listOf(stringError, emptyError)) {
            assertContains(error.violation, "single-character string expected")
            error.checkPosition(1, 1)
        }
    }

    @Test
    fun rangeOfUnsignedIntegers() {
        val error = assertFailsWith<UnexpectedValueError> {
            decode<UByte>("256")
        }

        assertContains(error.violation, "unsigned integer in range [0..255] expected")
        error.checkPosition(1, 1)
    }

    @Test
    fun rangeOfSignedIntegers() {
        val minError = assertFailsWith<UnexpectedValueError> {
            decode<Byte>("-129")
        }

        val maxError = assertFailsWith<UnexpectedValueError> {
            decode<Byte>("128")
        }

        assertContains(minError.violation, "signed integer in range [-128..127] expected")
        assertContains(maxError.violation, "signed integer in range [-128..127] expected")

        for (error in listOf(minError, maxError)) {
            maxError.checkPosition(1, 1)
        }
    }

    @Test
    fun unexpectedParserValue() {
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

        assertContains(intError.violation, "integer expected")
        assertContains(unsignedIntError.violation, "unsigned integer expected")
        assertContains(floatError.violation, "floating-point number expected")
        assertContains(boolError.violation, "boolean value expected")
        assertContains(stringError.violation, "string literal expected")

        for (error in listOf(intError, floatError, boolError, stringError)) {
            error.checkPosition(1, 8)
        }
    }

    @Test
    fun missingObject() {
        val error = assertFailsWith<UnexpectedValueError> {
            decode<Map<String, Int>>("40")
        }

        assertContains(error.violation, "object expected")
        error.checkPosition(1, 1)
    }

    @Test
    fun missingArray() {
        val error = assertFailsWith<UnexpectedValueError> {
            decode<List<Int>>("true")
        }

        assertContains(error.violation, "array expected")
        error.checkPosition(1, 1)
    }

    @Test
    fun classValue() {
        @Serializable
        data class Class(val x: String)
        assertEquals(Class("abc"), decode("{ x: 'abc' }"))
    }

    @Test
    fun duplicateClassKey() {
        @Serializable
        data class Class(val first: Int, val second: Long)

        val error = assertFailsWith<DuplicateKeyError> {
            decode<Class>("{ first: 10, second: 20, first: 30 }")
        }

        assertContains(error.violation, "duplicate key 'first'")
        assertEquals("first", error.key)
        error.checkPosition(1, 26)
    }

    @Test
    fun mapWithStringKeys() {
        assertEquals(mapOf("first" to 10, "second" to 433), decode("{ first: 10, second: 433 }"))
        assertEquals(mapOf<String?, Int>("null" to 0), decode("{null:0}"))
    }

    @Test
    fun mapWithWrappedStringKeys() {
        assertEquals(mapOf(InlineWrapper("a") to true, InlineWrapper("b") to false), decode("{a:true,b:false}"))
        assertEquals(mapOf<InlineWrapper<String>?, Int>(InlineWrapper("null") to 0), decode("{null:0}"))
    }

    @Test
    fun duplicateMapKey() {
        val error = assertFailsWith<DuplicateKeyError> {
            decode<Map<String, Short>>("{ a: 10, a: 10 }")
        }

        assertContains(error.violation, "duplicate key 'a'")
        assertEquals("a", error.key)
        error.checkPosition(1, 10)
    }

    @Test
    fun unsupportedMapKeyType() {
        fun assertUnsupported(block: () -> Unit) {
            assertFailsWith<UnsupportedOperationException>(block = block)
        }

        assertUnsupported { decode<Map<DummyEnum, Int>>("{a:0}") }
        assertUnsupported { decode<Map<DummyEnum?, Int>>("{a:0}") }
        assertUnsupported { decode<Map<InlineWrapper<Int>, Int>>("{a:0}") }
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
    fun literalsAsMapKeys() {
        assertEquals(mapOf("null" to 42), decode("{ null: 42 }"))
        assertEquals(mapOf("Infinity" to 10), decode("{ Infinity: 10 }"))
    }

    @Test
    fun nestedMapValue() {
        assertEquals(mapOf("a" to mapOf("x" to 1)), decode("{ a: { x: 1 } }"))
    }

    @Test
    fun noImplicitConversion() {
        val floatError = assertFailsWith<UnexpectedValueError> {
            decode<Long>("10.0")
        }

        val expError = assertFailsWith<UnexpectedValueError> {
            decode<Short>("1e2")
        }

        listOf(floatError, expError).forEach { error ->
            assertContains(error.violation, "integer expected")
            error.checkPosition(1, 1)
        }
    }

    @Test
    fun singletonValue() {
        @Serializable
        data class SingletonWrapper(val obj: Singleton)

        assertEquals(SingletonWrapper(Singleton), decode("{ obj: {} }"))

        val error = assertFailsWith<UnknownKeyError> {
            decode<SingletonWrapper>("{ obj: { unknown: 0 } }")
        }

        assertContains(error.violation, "unknown key 'unknown'")
        assertEquals("unknown", error.key)
        error.checkPosition(1, 10)
    }

    @Test
    fun polymorphicClass() {
        assertEquals(
            FlatDefaultImpl(10, 20), decode<DefaultInterface>("{ a: 10, type: 'flat', b: 20 }")
        )
    }

    @Test
    fun nestedPolymorphicClass() {
        assertEquals(
            NestedDefaultImpl(0, Wrapper(5)), decode<DefaultInterface>("{ x: { obj: 5 }, type: 'nested', a: 0 }")
        )
    }

    @Test
    fun innerPolymorphicClass() {
        assertEquals(
            Wrapper<DefaultInterface>(FlatDefaultImpl(10)),
            decode("{ obj: { type: 'flat', a: 10 } }")
        )
    }

    @Test
    fun unknownClassDiscriminator() {
        val error = assertFailsWith<UnexpectedValueError> {
            decode<DefaultInterface>("{ type: 'unknown' }")
        }

        assertContains(error.violation, "unknown class name 'unknown'")
        error.checkPosition(1, 9)
    }

    @Test
    fun repeatedPolymorphicDiscriminator() {
        val error = assertFailsWith<DuplicateKeyError> {
            decode<DefaultInterface>("{ type: 'flat', type: 'flat' }")
        }

        assertContains(error.violation, "duplicate key 'type'")
        assertEquals("type", error.key)
        error.checkPosition(1, 17)
    }

    @Test
    fun classSpecificDiscriminator() {
        assertEquals(
            CustomImpl(null),
            decode<CustomInterface>("{ category: 'main', name: null }")
        )

        val json5 = Json5 {
            classDiscriminator = "xyz"
        }

        assertEquals(
            CustomImpl(null),
            json5.decodeFromString<CustomInterface>("{ category: 'main', name: null }")
        )
    }

    @Test
    fun customGlobalClassDiscriminator() {
        val json5 = Json5 {
            classDiscriminator = "kind"
        }

        assertEquals(
            FlatDefaultImpl(15),
            json5.decodeFromString<DefaultInterface>("{ kind: 'flat', a: 15 }")
        )
    }

    @Test
    fun unknownClassKey() {
        @Serializable
        data class Entity(val x: Int)

        val error = assertFailsWith<UnknownKeyError> {
            decode<Entity>("{ abc: 10 }")
        }

        assertContains(error.violation, "unknown key 'abc'")
        assertEquals("abc", error.key)
        error.checkPosition(1, 3)
    }

    @Test
    fun missingFieldInFlatObject() {
        @Serializable
        data class Dummy(val a: Int, val b: Int)

        val error = assertFailsWith<MissingFieldError> {
            decode<Dummy>("{ b: 10 }")
        }

        assertContains(error.violation, "missing field 'a' in object")
        assertEquals("a", error.key)
        error.checkPosition(1, 1)
    }

    @Test
    fun missingFieldInNestedStructure() {
        @Serializable
        data class Inner(val x: Int)

        @Serializable
        data class Outer(val inner: Inner, val y: Int)

        val outerError = assertFailsWith<MissingFieldError> {
            decode<Outer>("{ inner: { x: 10 } }")
        }

        assertContains(outerError.violation, "missing field 'y' in object")
        assertEquals("y", outerError.key)
        outerError.checkPosition(1, 1)

        val innerError = assertFailsWith<MissingFieldError> {
            decode<Outer>("{ inner: {}, y: 4 }")
        }

        assertContains(innerError.violation, "missing field 'x' in object")
        assertEquals("x", innerError.key)
        innerError.checkPosition(1, 10)
    }

    @Test
    fun missingFieldInPolymorphicClass() {
        val error = assertFailsWith<MissingFieldError> {
            decode<DefaultInterface>("{ type: 'flat' }")
        }

        assertContains(error.violation, "missing field 'a' in object")
        assertEquals("a", error.key)
        error.checkPosition(1, 1)
    }

    @Test
    fun missingClassDiscriminator() {
        val error = assertFailsWith<MissingFieldError> {
            decode<DefaultInterface>("{}")
        }

        assertContains(error.violation, "missing field 'type' in object")
        error.checkPosition(1, 1)
    }

    @Test
    fun contextualSerializer() {
        val json5 = Json5 {
            serializersModule = SerializersModule {
                contextual(ColorAsStringSerializer)
            }
        }

        assertEquals(Color(0xff0000), json5.decodeFromString("'0xff0000'"))
    }
}

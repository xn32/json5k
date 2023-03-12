package io.github.xn32.json5k.binding

import io.github.xn32.json5k.Json5
import io.github.xn32.json5k.SerialComment
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private inline fun <reified T> encode(input: T): String = Json5.encodeToString(input)

class SerializationTest {
    @Test
    fun signedValue() {
        assertEquals("127", encode(Byte.MAX_VALUE))
        assertEquals("-128", encode(Byte.MIN_VALUE))
        assertEquals("32767", encode(Short.MAX_VALUE))
        assertEquals("-32768", encode(Short.MIN_VALUE))
        assertEquals("2147483647", encode(Int.MAX_VALUE))
        assertEquals("-2147483648", encode(Int.MIN_VALUE))
        assertEquals("9223372036854775807", encode(Long.MAX_VALUE))
        assertEquals("-9223372036854775808", encode(Long.MIN_VALUE))
    }

    @Test
    fun unsignedValue() {
        assertEquals("255", encode(UByte.MAX_VALUE))
        assertEquals("65535", encode(UShort.MAX_VALUE))
        assertEquals("4294967295", encode(UInt.MAX_VALUE))
        assertEquals("18446744073709551615", encode(ULong.MAX_VALUE))
    }

    @Test
    fun floatingPointValue() {
        assertEquals("13.25", encode(13.25f))
        assertEquals("11.0", encode(11.0))
        assertEquals("Infinity", encode(Double.POSITIVE_INFINITY))
        assertEquals("Infinity", encode(Float.POSITIVE_INFINITY))
        assertEquals("-Infinity", encode(Double.NEGATIVE_INFINITY))
        assertEquals("-Infinity", encode(Float.NEGATIVE_INFINITY))
        assertEquals("NaN", encode(Double.NaN))
        assertEquals("NaN", encode(Float.NaN))
    }

    @Test
    fun booleanValue() {
        assertEquals("true", encode(true))
        assertEquals("false", encode(false))
    }

    @Test
    fun enumValue() {
        assertEquals("\"ITEM\"", encode(DummyEnum.ITEM))
        assertEquals("\"NONE\"", encode(DummyEnum.LAST))
    }

    @Test
    fun valueClass() {
        assertEquals("\"wrapped\"", encode(InlineWrapper("wrapped")))
        assertEquals("{obj:\"str\"}", encode(Wrapper(InlineWrapper("str"))))
    }

    @Test
    fun stringValue() {
        assertEquals("\"x\"", encode('x'))
        assertEquals("\"abc\"", encode("abc"))
        assertEquals("\"\ud834\udd1e\"", encode("\ud834\udd1e"))
    }

    @Test
    fun nullableValue() {
        assertEquals("50", encode<Int?>(50))
        assertEquals("null", encode<Int?>(null))
        assertEquals("50", encode<UInt?>(50u))
        assertEquals("null", encode<UInt?>(null))
    }

    @Test
    fun listOfIntegers() {
        assertEquals("[3,6,7]", encode(listOf(3, 6, 7)))
    }

    @Test
    fun primitiveArray() {
        assertEquals(
            "[-2147483648,2147483647]",
            encode(intArrayOf(Int.MIN_VALUE, Int.MAX_VALUE))
        )

        assertEquals(
            "[-9223372036854775808,9223372036854775807]",
            encode(longArrayOf(Long.MIN_VALUE, Long.MAX_VALUE))
        )

        @OptIn(ExperimentalUnsignedTypes::class)
        assertEquals(
            "[0,4294967295]",
            encode(uintArrayOf(0u, UInt.MAX_VALUE))
        )
    }

    @Test
    fun valueContainers() {
        assertEquals(
            "{byte:127,short:32767,int:2147483647,long:9223372036854775807}",
            encode(SignedContainer(Byte.MAX_VALUE, Short.MAX_VALUE, Int.MAX_VALUE, Long.MAX_VALUE))
        )

        assertEquals(
            "{byte:255,short:65535,int:4294967295,long:18446744073709551615}",
            encode(UnsignedContainer(UByte.MAX_VALUE, UShort.MAX_VALUE, UInt.MAX_VALUE, ULong.MAX_VALUE))
        )

        assertEquals(
            "{float:0.0,double:Infinity}",
            encode(FloatingPointContainer(.0f, Double.POSITIVE_INFINITY))
        )

        assertEquals(
            "{char:\"x\",str:\"xyz\",bool:true,enum:\"ITEM\"}",
            encode(MiscContainer('x', "xyz", true, DummyEnum.ITEM))
        )
    }

    @Test
    fun noDefaultValues() {
        @Serializable
        data class Dummy(val a: Short, val b: Short = 40)

        assertEquals("{a:11}", encode(Dummy(11, 40)))
    }

    @Test
    fun enforceDefaultValues() {
        @Serializable
        data class Dummy(val a: Short, val b: Short = 50)

        val json5 = Json5 {
            encodeDefaults = true
        }

        assertEquals("{a:40,b:50}", json5.encodeToString(Dummy(40)))
    }

    @Test
    fun nestedStructure() {
        @Serializable
        data class Container(val id: Int, val parts: List<Container>?)

        assertEquals(
            "[{id:1,parts:[{id:3,parts:null}]}]",
            encode(listOf(Container(1, listOf(Container(3, null)))))
        )
    }

    @Test
    fun mapWithStringKeys() {
        assertEquals("{a:10,b:20,null:0}", encode(mapOf("a" to 10, "b" to 20, "null" to 0)))
        assertEquals("{abc:0}", encode(mapOf<String?, Int>("abc" to 0)))
    }

    @Test
    fun mapWithWrappedStringKeys() {
        assertEquals("{a:10}", encode(mapOf(InlineWrapper("a") to 10)))
        assertEquals("{abc:0}", encode(mapOf<InlineWrapper<String>?, Int>(InlineWrapper("abc") to 0)))
    }

    @Test
    fun unsupportedMapKeyType() {
        fun assertUnsupported(block: () -> Unit) {
            assertFailsWith<UnsupportedOperationException>(block = block)
        }

        assertUnsupported { encode(mapOf(DummyEnum.ITEM to 0)) }
        assertUnsupported { encode(mapOf<DummyEnum?, Int>(DummyEnum.ITEM to 0)) }
        assertUnsupported { encode(mapOf<String?, Int>(null to 0)) }
        assertUnsupported { encode(mapOf<InlineWrapper<String>?, Int>(null to 0)) }
        assertUnsupported { encode(mapOf(InlineWrapper(10) to 0)) }
        assertUnsupported { encode(mapOf<Int?, Int>(null to 0)) }
        assertUnsupported { encode(mapOf(0.toChar() to 0)) }
        assertUnsupported { encode(mapOf(0.toByte() to 0)) }
        assertUnsupported { encode(mapOf(0.toShort() to 0)) }
        assertUnsupported { encode(mapOf(0 to 0)) }
        assertUnsupported { encode(mapOf(0L to 0)) }
        assertUnsupported { encode(mapOf(.0f to 0)) }
        assertUnsupported { encode(mapOf(.0 to 0)) }
        assertUnsupported { encode(mapOf(true to 0)) }
    }

    @Test
    fun classDiscriminatorCollision() {
        assertFailsWith<UnsupportedOperationException> {
            encode(Wrapper<DefaultInterface>(InvalidDefaultImpl("abc")))
        }
    }

    @Test
    fun polymorphicClass() {
        assertEquals(
            "{obj:{type:\"flat\",a:42}}",
            encode(Wrapper<DefaultInterface>(FlatDefaultImpl(42)))
        )
    }

    @Test
    fun classSpecificDiscriminator() {
        assertEquals(
            "{obj:{category:\"main\",name:null}}",
            encode(Wrapper<CustomInterface>(CustomImpl(null)))
        )

        val json5 = Json5 {
            classDiscriminator = "kind"
        }

        assertEquals(
            "{category:\"main\",name:\"abc\"}",
            json5.encodeToString<CustomInterface>(CustomImpl("abc"))
        )

    }

    @Test
    fun customGlobalClassDiscriminator() {
        val json5 = Json5 {
            classDiscriminator = "kind"
        }

        assertEquals(
            "{kind:\"flat\",a:50}",
            json5.encodeToString<DefaultInterface>(FlatDefaultImpl(50))
        )
    }

    @Test
    fun multilinePrettyPrintOutput() {
        val json5 = Json5 {
            prettyPrint = true
        }

        assertEquals(
            """
                {
                    a: 10,
                    "~b": 20
                }
            """.trimIndent(),
            json5.encodeToString(mapOf("a" to 10, "~b" to 20))
        )
    }

    @Test
    fun useSingleQuotes() {
        val json5 = Json5 {
            prettyPrint = true
            useSingleQuotes = true
        }

        assertEquals("'apples'", json5.encodeToString("apples"))
    }

    @Test
    fun quoteMemberNames() {
        val json5 = Json5 {
            prettyPrint = true
            quoteMemberNames = true
        }

        assertEquals(
            """
                {
                    "a": 10,
                    "~b": 30
                }
            """.trimIndent(),
            json5.encodeToString(mapOf("a" to 10, "~b" to 30))
        )
    }

    @Test
    fun serialComments() {
        val json5 = Json5 {
            prettyPrint = true
        }

        @Serializable
        data class Point(
            @SerialComment("First comment")
            val x: Int,

            @SerialComment("\tSecond comment")
            val y: Int,

            @SerialComment("\r\n\tThird comment (spanning\r\n\tmultiple lines)")
            val z: Int,
        )

        assertEquals(
            """
                {
                    // First comment
                    x: 400,
                    // Second comment
                    y: 500,
                    // Third comment (spanning
                    // multiple lines)
                    z: 600
                }
            """.trimIndent(),
            json5.encodeToString(Point(400, 500, 600))
        )
    }

    @Test
    fun contextualSerializer() {
        val json5 = Json5 {
            serializersModule = SerializersModule {
                contextual(ColorAsStringSerializer)
            }
        }

        assertEquals("\"0xff0000\"", json5.encodeToString(Color(0xff0000)))
    }
}

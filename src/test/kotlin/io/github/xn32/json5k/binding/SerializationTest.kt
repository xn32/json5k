package io.github.xn32.json5k.binding

import io.github.xn32.json5k.Json5
import io.github.xn32.json5k.SerialComment
import io.github.xn32.json5k.encodeToStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private inline fun <reified T> encode(input: T): String = Json5.encodeToString(input)

class SerializationTest {
    @Test
    fun `top-level value is encoded`() {
        assertEquals("40", encode(40))
        assertEquals("-33", encode(-33))
        assertEquals("13.25", encode(13.25f))
        assertEquals("11.0", encode(11.0))
        assertEquals("true", encode(true))
        assertEquals("null", encode<Int?>(null))
        assertEquals("Infinity", encode(Double.POSITIVE_INFINITY))
        assertEquals("-Infinity", encode(Double.NEGATIVE_INFINITY))
        assertEquals("NaN", encode(Double.NaN))
        assertEquals("\"abc\"", encode("abc"))
        assertEquals("\"\ud834\udd1e\"", encode("\ud834\udd1e"))
    }

    @Test
    fun `nullable top-level value is encoded`() {
        assertEquals("null", encode<Int?>(null))
        assertEquals("55", encode<Int?>(55))
    }

    @Test
    fun `list is encoded correctly`() {
        assertEquals("[3,6,7]", encode(listOf(3, 6, 7)))
    }

    @Test
    fun `class without default values is encoded correctly`() {
        @Serializable
        data class Dummy(val a: Int, val b: Int?)

        assertEquals("{a:10,b:null}", encode(Dummy(10, null)))
    }

    @Test
    fun `default values are not encoded`() {
        @Serializable
        data class Dummy(val a: Short, val b: Short = 40)

        assertEquals("{a:11}", encode(Dummy(11)))
    }

    @Test
    fun `encoding of default values can be activated`() {
        @Serializable
        data class Dummy(val a: Short, val b: Short = 50)

        val json5 = Json5 {
            encodeDefaults = true
        }

        assertEquals("{a:40,b:50}", json5.encodeToString(Dummy(40)))
    }

    @Test
    fun `serialization of unsigned integers is supported`() {
        @Serializable
        data class UnsignedWrapper(val value: UByte)

        assertEquals("{value:255}", encode(UnsignedWrapper(255u)))
    }

    @Test
    fun `nested class-list structures are encoded`() {
        @Serializable
        data class Container(val id: Int, val parts: List<Container>?)

        assertEquals(
            "[{id:1,parts:[{id:3,parts:null}]}]", encode(listOf(Container(1, listOf(Container(3, null)))))
        )
    }

    @Test
    fun `map is encoded correctly`() {
        assertEquals(
            "{a:10,b:20,c:null,null:0}", encode(mapOf("a" to 10, "b" to 20, "c" to null, "null" to 0))
        )
    }

    @Test
    fun `unsupported key type causes error`() {
        assertFailsWith<UnsupportedOperationException> {
            encode(mapOf('a' to 2))
        }

        assertFailsWith<UnsupportedOperationException> {
            encode(mapOf(10 to 0))
        }
    }

    @Test
    fun `collisions with polymorphic discriminator values are detected`() {
        assertFailsWith<UnsupportedOperationException> {
            encode(Wrapper<DefaultInterface>(InvalidDefaultImpl("abc")))
        }
    }

    @Test
    fun `polymorphic type is serialized correctly`() {
        assertEquals(
            "{obj:{type:\"valid\",integer:42}}", encode(Wrapper<DefaultInterface>(DefaultImpl(42)))
        )
    }

    @Test
    fun `customized polymorphic type is serialized correctly`() {
        assertEquals(
            "{obj:{category:\"main\",name:null}}", encode(Wrapper<CustomInterface>(CustomImpl(null)))
        )
    }

    @Test
    fun `modified default discriminator name is considered`() {
        val json5 = Json5 { classDiscriminator = "kind" }

        assertEquals(
            "{kind:\"valid\",integer:50}", json5.encodeToString<DefaultInterface>(DefaultImpl(50))
        )
    }

    @Test
    fun `local discriminator name overwrites default name`() {
        val json5 = Json5 {
            classDiscriminator = "kind"
        }

        assertEquals(
            "{category:\"main\",name:\"abc\"}", json5.encodeToString<CustomInterface>(CustomImpl("abc"))
        )
    }

    @Test
    fun `pretty-print mode leads to multi-line output`() {
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
    fun `single-quote setting works in pretty-print mode`() {
        val json5 = Json5 {
            prettyPrint = true
            useSingleQuotes = true
        }

        assertEquals("'apples'", json5.encodeToString("apples"))
    }

    @Test
    fun `member name quotation in pretty-print mode can be enforced`() {
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
    fun `stream serialization works`() {
        val stream = ByteArrayOutputStream()
        val str = stream.use {
            Json5.encodeToStream(20, it)
            it.toString("UTF-8")
        }

        assertEquals("20", str)
    }

    @Test
    fun `serial comments are trimmed and added to pretty-print output`() {
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

}

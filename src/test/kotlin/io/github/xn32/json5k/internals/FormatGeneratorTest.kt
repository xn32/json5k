package io.github.xn32.json5k.internals

import io.github.xn32.json5k.config.OutputStrategy
import io.github.xn32.json5k.format.Token
import io.github.xn32.json5k.generation.FormatGenerator
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals

private fun outputFor(
    strategy: OutputStrategy = OutputStrategy.Compressed,
    block: FormatGenerator.() -> Unit
): String = ByteArrayOutputStream().use {
    val writer = FormatGenerator(it, strategy)
    writer.apply(block)
    writer.put(Token.EndOfFile)
    it.toString()
}

private val HUMAN_READABLE = OutputStrategy.HumanReadable(4, '"', false)

class FormatGeneratorTest {
    @Test
    fun `top-level null is written`() {
        assertEquals("null", outputFor {
            put(Token.Null)
        })
    }

    @Test
    fun `top-level true and false are written`() {
        assertEquals("true", outputFor {
            put(Token.Bool(true))
        })

        assertEquals("false", outputFor {
            put(Token.Bool(false))
        })
    }

    @Test
    fun `signed negative integer is written`() {
        assertEquals("-4443", outputFor {
            put(Token.SignedInteger(-4443))
        })
    }

    @Test
    fun `signed positive integer is written`() {
        assertEquals("33332", outputFor {
            put(Token.SignedInteger(33332))
        })
    }

    @Test
    fun `unsigned integer is written`() {
        assertEquals("7371823712", outputFor {
            put(Token.UnsignedInteger(7371823712u))
        })
    }

    @Test
    fun `large floating point number is written`() {
        assertEquals("4.0E30", outputFor {
            put(Token.FloatingPoint(4e30))
        })
    }

    @Test
    fun `small floating point number is written`() {
        assertEquals("-2.0E-10", outputFor {
            put(Token.FloatingPoint(-2e-10))
        })
    }

    @Test
    fun `floating point number with exponent close to zero is written`() {
        assertEquals("55.0", outputFor {
            put(Token.FloatingPoint(55.0))
        })
    }

    @Test
    fun `numeric literals are written`() {
        assertEquals("Infinity", outputFor {
            put(Token.FloatingPoint(Double.POSITIVE_INFINITY))
        })

        assertEquals("-Infinity", outputFor {
            put(Token.FloatingPoint(Double.NEGATIVE_INFINITY))
        })

        assertEquals("NaN", outputFor {
            put(Token.FloatingPoint(Double.NaN))
        })
    }

    @Test
    fun `empty top-level object is written`() {
        assertEquals("{}", outputFor {
            put(Token.BeginObject)
            put(Token.EndObject)
        })
    }

    @Test
    fun `top-level object with single member is written`() {
        assertEquals("{abc:10}", outputFor {
            put(Token.BeginObject)
            put(Token.MemberName("abc"))
            put(Token.SignedInteger(10))
            put(Token.EndObject)
        })
    }

    @Test
    fun `top-level object with multiple members is written`() {
        assertEquals("{first:10,second:null,third:1.1}", outputFor {
            put(Token.BeginObject)
            put(Token.MemberName("first"))
            put(Token.SignedInteger(10))
            put(Token.MemberName("second"))
            put(Token.Null)
            put(Token.MemberName("third"))
            put(Token.FloatingPoint(1.1))
            put(Token.EndObject)
        })
    }

    @Test
    fun `nested structures are written`() {
        assertEquals("[{a:10},{b:20}]", outputFor {
            put(Token.BeginArray)
            put(Token.BeginObject)
            put(Token.MemberName("a"))
            put(Token.SignedInteger(10))
            put(Token.EndObject)
            put(Token.BeginObject)
            put(Token.MemberName("b"))
            put(Token.SignedInteger(20))
            put(Token.EndObject)
            put(Token.EndArray)
        })
    }

    @Test
    fun `string is quoted`() {
        assertEquals("\"string\"", outputFor {
            put(Token.Str("string"))
        })
    }

    @Test
    fun `CR and LF in strings are escaped`() {
        assertEquals("\"first\\r\\nsecond\"", outputFor {
            put(Token.Str("first\r\nsecond"))
        })
    }

    @Test
    fun `quote char and backslash in strings are escaped`() {
        assertEquals(""""string = \"\\underline\", char = '\\'"""", outputFor {
            put(Token.Str("string = \"\\underline\", char = '\u005c'"))
        })
    }

    @Test
    fun `LS and PS in strings are escaped`() {
        assertEquals("\"x\\u2028y\\u2029z\"", outputFor {
            put(Token.Str("x\u2028y\u2029z"))
        })
    }

    @Test
    fun `other chars in strings are not escaped`() {
        assertEquals("\"\u0000\u2000\ud83c\udfbc\"", outputFor {
            put(Token.Str("\u0000\u2000\ud83c\udfbc"))
        })
    }

    @Test
    fun `member names are quoted only when necessary`() {
        assertEquals("{key:10,\"~a\":20}", outputFor {
            put(Token.BeginObject)
            put(Token.MemberName("key"))
            put(Token.SignedInteger(10))
            put(Token.MemberName("~a"))
            put(Token.SignedInteger(20))
            put(Token.EndObject)
        })
    }

    @Test
    fun `basic indentation in pretty-print mode works`() {
        val expected = """
            {
                key: -11
            }
        """.trimIndent()

        assertEquals(expected, outputFor(HUMAN_READABLE) {
            put(Token.BeginObject)
            put(Token.MemberName("key"))
            put(Token.SignedInteger(-11))
            put(Token.EndObject)
        })
    }

    @Test
    fun `indentation width is configurable in pretty-print mode`() {
        val expected = """
            {
               "~a": "test string"
            }
        """.trimIndent()

        val strategy = OutputStrategy.HumanReadable(3, '"', false)
        assertEquals(expected, outputFor(strategy) {
            put(Token.BeginObject)
            put(Token.MemberName("~a"))
            put(Token.Str("test string"))
            put(Token.EndObject)
        })
    }

    @Test
    fun `quotation of member names can be enforced in pretty-print mode`() {
        val expected = """
            {
               "key": 100
            }
        """.trimIndent()

        val strategy = OutputStrategy.HumanReadable(3, '"', true)
        assertEquals(expected, outputFor(strategy) {
            put(Token.BeginObject)
            put(Token.MemberName("key"))
            put(Token.UnsignedInteger(100u))
            put(Token.EndObject)
        })
    }

    @Test
    fun `empty structs are compressed in pretty-print mode`() {
        assertEquals("{}", outputFor(HUMAN_READABLE) {
            put(Token.BeginObject)
            put(Token.EndObject)
        })

        assertEquals("[]", outputFor(HUMAN_READABLE) {
            put(Token.BeginArray)
            put(Token.EndArray)
        })
    }

    @Test
    fun `no line break between consecutive structs in pretty-print mode`() {
        val expected = """
            [
                {}, [
                    "first element",
                    "second element"
                ]
            ]
        """.trimIndent()

        assertEquals(expected, outputFor(HUMAN_READABLE) {
            put(Token.BeginArray)
            put(Token.BeginObject)
            put(Token.EndObject)
            put(Token.BeginArray)
            put(Token.Str("first element"))
            put(Token.Str("second element"))
            put(Token.EndArray)
            put(Token.EndArray)
        })
    }

    @Test
    fun `pretty-print mode default to double-quoted strings`() {
        assertEquals("\"12'34\\\"56\"", outputFor(HUMAN_READABLE) {
            put(Token.Str("12'34\"56"))
        })
    }

    @Test
    fun `quote char can be configured in pretty-print mode`() {
        val strategy = OutputStrategy.HumanReadable(0, '\'', false)
        assertEquals("'12\\\'34\"56'", outputFor(strategy) {
            put(Token.Str("12'34\"56"))
        })
    }
}

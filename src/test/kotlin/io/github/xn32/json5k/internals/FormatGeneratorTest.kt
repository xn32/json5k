package io.github.xn32.json5k.internals

import io.github.xn32.json5k.config.OutputStrategy
import io.github.xn32.json5k.format.Token
import io.github.xn32.json5k.generation.FormatGenerator
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals

private val COMPRESSED = OutputStrategy.Compressed
private val HUMAN_READABLE = OutputStrategy.HumanReadable(4, '"', quoteMemberNames = false)

class FormatGeneratorTest {
    @Test
    fun `top-level null is written`() {
        assertEquals("null", generate(COMPRESSED) {
            put(Token.Null)
        })
    }

    @Test
    fun `top-level true and false are written`() {
        assertEquals("true", generate(COMPRESSED) {
            put(Token.Bool(true))
        })

        assertEquals("false", generate(COMPRESSED) {
            put(Token.Bool(false))
        })
    }

    @Test
    fun `signed negative integer is written`() {
        assertEquals("-4443", generate(COMPRESSED) {
            put(Token.SignedInteger(-4443))
        })
    }

    @Test
    fun `signed positive integer is written`() {
        assertEquals("33332", generate(COMPRESSED) {
            put(Token.SignedInteger(33332))
        })
    }

    @Test
    fun `unsigned integer is written`() {
        assertEquals("7371823712", generate(COMPRESSED) {
            put(Token.UnsignedInteger(7371823712u))
        })
    }

    @Test
    fun `large floating point number is written`() {
        assertEquals("4.0E30", generate(COMPRESSED) {
            put(Token.FloatingPoint(4e30))
        })
    }

    @Test
    fun `small floating point number is written`() {
        assertEquals("-2.0E-10", generate(COMPRESSED) {
            put(Token.FloatingPoint(-2e-10))
        })
    }

    @Test
    fun `floating point number with exponent close to zero is written`() {
        assertEquals("55.0", generate(COMPRESSED) {
            put(Token.FloatingPoint(55.0))
        })
    }

    @Test
    fun `numeric literals are written`() {
        assertEquals("Infinity", generate(COMPRESSED) {
            put(Token.FloatingPoint(Double.POSITIVE_INFINITY))
        })

        assertEquals("-Infinity", generate(COMPRESSED) {
            put(Token.FloatingPoint(Double.NEGATIVE_INFINITY))
        })

        assertEquals("NaN", generate(COMPRESSED) {
            put(Token.FloatingPoint(Double.NaN))
        })
    }

    @Test
    fun `empty top-level object is written`() {
        assertEquals("{}", generate(COMPRESSED) {
            put(Token.BeginObject)
            put(Token.EndObject)
        })
    }

    @Test
    fun `top-level object with single member is written`() {
        assertEquals("{abc:10}", generate(COMPRESSED) {
            put(Token.BeginObject)
            put(Token.MemberName("abc"))
            put(Token.SignedInteger(10))
            put(Token.EndObject)
        })
    }

    @Test
    fun `top-level object with multiple members is written`() {
        assertEquals("{first:10,second:null,third:1.1}", generate(COMPRESSED) {
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
        assertEquals("[{a:10},{b:20}]", generate(COMPRESSED) {
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
    fun `strings are enclosed in double quotation marks`() {
        assertEquals("\"string\"", generate(COMPRESSED) {
            put(Token.Str("string"))
        })
    }

    @Test
    fun `CR and LF in strings are escaped`() {
        assertEquals("\"first\\r\\nsecond\"", generate(COMPRESSED) {
            put(Token.Str("first\r\nsecond"))
        })
    }

    @Test
    fun `quote char and backslash in strings are escaped`() {
        assertEquals(""""string = \"\\underline\", char = '\\'"""", generate(COMPRESSED) {
            put(Token.Str("string = \"\\underline\", char = '\u005c'"))
        })
    }

    @Test
    fun `LS and PS in strings are escaped`() {
        assertEquals("\"x\\u2028y\\u2029z\"", generate(COMPRESSED) {
            put(Token.Str("x\u2028y\u2029z"))
        })
    }

    @Test
    fun `other chars in strings are not escaped`() {
        assertEquals("\"\u0000\u2000\ud83c\udfbc\"", generate(COMPRESSED) {
            put(Token.Str("\u0000\u2000\ud83c\udfbc"))
        })
    }

    @Test
    fun `member names are quoted only when necessary`() {
        assertEquals("{key:10,\"~a\":20}", generate(COMPRESSED) {
            put(Token.BeginObject)
            put(Token.MemberName("key"))
            put(Token.SignedInteger(10))
            put(Token.MemberName("~a"))
            put(Token.SignedInteger(20))
            put(Token.EndObject)
        })
    }

    @Test
    fun `human-readable output has correct indentation`() {
        assertEquals(
            """
                {
                    number: -11,
                    array: [
                        44
                    ]
                }
            """.trimIndent(),
            generate(HUMAN_READABLE) {
                put(Token.BeginObject)
                put(Token.MemberName("number"))
                put(Token.SignedInteger(-11))
                put(Token.MemberName("array"))
                put(Token.BeginArray)
                put(Token.SignedInteger(44))
                put(Token.EndArray)
                put(Token.EndObject)
            }
        )
    }

    @Test
    fun `indentation width of human-readable output is configurable`() {
        assertEquals(
            """
                {
                  key: -12
                }
            """.trimIndent(),
            generate(OutputStrategy.HumanReadable(2, '"', false)) {
                put(Token.BeginObject)
                put(Token.MemberName("key"))
                put(Token.SignedInteger(-12))
                put(Token.EndObject)
            }
        )
    }

    @Test
    fun `quotation of member names can be enforced in human-readable output`() {
        assertEquals(
            """
                {
                    "key": 1000
                }
            """.trimIndent(),
            generate(OutputStrategy.HumanReadable(4, '"', true)) {
                put(Token.BeginObject)
                put(Token.MemberName("key"))
                put(Token.UnsignedInteger(1000u))
                put(Token.EndObject)
            }
        )
    }

    @Test
    fun `empty structs are compressed in human-readable output`() {
        assertEquals("{}", generate(HUMAN_READABLE) {
            put(Token.BeginObject)
            put(Token.EndObject)
        })

        assertEquals("[]", generate(HUMAN_READABLE) {
            put(Token.BeginArray)
            put(Token.EndArray)
        })
    }

    @Test
    fun `consecutive structs in human-readable output are not separated by a line break`() {
        assertEquals(
            """
                [
                    {}, [
                        true,
                        null
                    ]
                ]
            """.trimIndent(),
            generate(HUMAN_READABLE) {
                put(Token.BeginArray)
                put(Token.BeginObject)
                put(Token.EndObject)
                put(Token.BeginArray)
                put(Token.Bool(true))
                put(Token.Null)
                put(Token.EndArray)
                put(Token.EndArray)
            }
        )
    }

    @Test
    fun `strings in human-readable output are enclosed in double quotation marks`() {
        assertEquals("\"12'34\\\"56\"", generate(HUMAN_READABLE) {
            put(Token.Str("12'34\"56"))
        })
    }

    @Test
    fun `quote char for strings in human-readable output is configurable`() {
        assertEquals("'12\\\'34\"56'", generate(OutputStrategy.HumanReadable(7, '\'', false)) {
            put(Token.Str("12'34\"56"))
        })
    }
}

private fun generate(strategy: OutputStrategy, block: FormatGenerator.() -> Unit) = ByteArrayOutputStream().use {
    val writer = FormatGenerator(it, strategy)
    writer.apply(block)
    writer.put(Token.EndOfFile)
    it.toString(Charsets.UTF_8)
}

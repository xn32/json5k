package io.github.xn32.json5k.internals

import io.github.xn32.json5k.OutputStrategy
import io.github.xn32.json5k.format.Token
import io.github.xn32.json5k.generation.FormatGenerator
import io.github.xn32.json5k.generation.StringOutputSink
import kotlin.test.Test
import kotlin.test.assertEquals

private fun newHumanReadableStrategy(
    indentationWidth: Int = 4,
    quoteCharacter: Char = '"',
    quoteMemberNames: Boolean = false,
) = OutputStrategy.HumanReadable(
    indentationWidth,
    quoteCharacter,
    quoteMemberNames
)

class FormatGeneratorTest {
    @Test
    fun `top-level null is written`() {
        assertEquals("null", generate(OutputStrategy.Compressed) {
            put(Token.Null)
        })
    }

    @Test
    fun `top-level true and false are written`() {
        assertEquals("true", generate(OutputStrategy.Compressed) {
            put(Token.Bool(true))
        })

        assertEquals("false", generate(OutputStrategy.Compressed) {
            put(Token.Bool(false))
        })
    }

    @Test
    fun `signed negative integer is written`() {
        assertEquals("-4443", generate(OutputStrategy.Compressed) {
            put(Token.SignedInteger(-4443))
        })
    }

    @Test
    fun `signed positive integer is written`() {
        assertEquals("33332", generate(OutputStrategy.Compressed) {
            put(Token.SignedInteger(33332))
        })
    }

    @Test
    fun `unsigned integer is written`() {
        assertEquals("7371823712", generate(OutputStrategy.Compressed) {
            put(Token.UnsignedInteger(7371823712u))
        })
    }

    @Test
    fun `large floating point number is written`() {
        assertEquals("4.0E30", generate(OutputStrategy.Compressed) {
            put(Token.FloatingPoint(4e30))
        })
    }

    @Test
    fun `small floating point number is written`() {
        assertEquals("-2.0E-10", generate(OutputStrategy.Compressed) {
            put(Token.FloatingPoint(-2e-10))
        })
    }

    @Test
    fun `floating point number with exponent close to zero is written`() {
        assertEquals("55.0", generate(OutputStrategy.Compressed) {
            put(Token.FloatingPoint(55.0))
        })
    }

    @Test
    fun `numeric literals are written`() {
        assertEquals("Infinity", generate(OutputStrategy.Compressed) {
            put(Token.FloatingPoint(Double.POSITIVE_INFINITY))
        })

        assertEquals("-Infinity", generate(OutputStrategy.Compressed) {
            put(Token.FloatingPoint(Double.NEGATIVE_INFINITY))
        })

        assertEquals("NaN", generate(OutputStrategy.Compressed) {
            put(Token.FloatingPoint(Double.NaN))
        })
    }

    @Test
    fun `empty top-level object is written`() {
        assertEquals("{}", generate(OutputStrategy.Compressed) {
            put(Token.BeginObject)
            put(Token.EndObject)
        })
    }

    @Test
    fun `top-level object with single member is written`() {
        assertEquals("{abc:10}", generate(OutputStrategy.Compressed) {
            put(Token.BeginObject)
            put(Token.MemberName("abc"))
            put(Token.SignedInteger(10))
            put(Token.EndObject)
        })
    }

    @Test
    fun `top-level object with multiple members is written`() {
        assertEquals("{first:10,second:null,third:1.1}", generate(OutputStrategy.Compressed) {
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
        assertEquals("[{a:10},{b:20}]", generate(OutputStrategy.Compressed) {
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
        assertEquals("\"string\"", generate(OutputStrategy.Compressed) {
            put(Token.Str("string"))
        })
    }

    @Test
    fun `CR and LF in strings are escaped`() {
        assertEquals("\"first\\r\\nsecond\"", generate(OutputStrategy.Compressed) {
            put(Token.Str("first\r\nsecond"))
        })
    }

    @Test
    fun `quote char and backslash in strings are escaped`() {
        assertEquals(""""string = \"\\underline\", char = '\\'"""", generate(OutputStrategy.Compressed) {
            put(Token.Str("string = \"\\underline\", char = '\u005c'"))
        })
    }

    @Test
    fun `LS and PS in strings are escaped`() {
        assertEquals("\"x\\u2028y\\u2029z\"", generate(OutputStrategy.Compressed) {
            put(Token.Str("x\u2028y\u2029z"))
        })
    }

    @Test
    fun `other chars in strings are not escaped`() {
        assertEquals("\"\u0000\u2000\ud83c\udfbc\"", generate(OutputStrategy.Compressed) {
            put(Token.Str("\u0000\u2000\ud83c\udfbc"))
        })
    }

    @Test
    fun `member names are quoted only when necessary`() {
        assertEquals("{key:10,\"~a\":20}", generate(OutputStrategy.Compressed) {
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
            generate(newHumanReadableStrategy()) {
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
            generate(newHumanReadableStrategy(indentationWidth = 2)) {
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
            generate(newHumanReadableStrategy(quoteMemberNames = true)) {
                put(Token.BeginObject)
                put(Token.MemberName("key"))
                put(Token.UnsignedInteger(1000u))
                put(Token.EndObject)
            }
        )
    }

    @Test
    fun `empty structs are compressed in human-readable output`() {
        assertEquals("{}", generate(newHumanReadableStrategy()) {
            put(Token.BeginObject)
            put(Token.EndObject)
        })

        assertEquals("[]", generate(newHumanReadableStrategy()) {
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
            generate(newHumanReadableStrategy()) {
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
        assertEquals("\"12'34\\\"56\"", generate(newHumanReadableStrategy()) {
            put(Token.Str("12'34\"56"))
        })
    }

    @Test
    fun `quote char for strings in human-readable output is configurable`() {
        assertEquals("'12\\\'34\"56'", generate(newHumanReadableStrategy(quoteCharacter = '\'')) {
            put(Token.Str("12'34\"56"))
        })
    }

    @Test
    fun `serial comments are not present in compressed output`() {
        assertEquals("{a:10}", generate(OutputStrategy.Compressed) {
            put(Token.BeginObject)
            writeComment("comment")
            put(Token.MemberName("a"))
            put(Token.SignedInteger(10))
            put(Token.EndObject)
        })
    }

    @Test
    fun `serial comments are added to human-readable output`() {
        assertEquals(
            """
                {
                    a: 11,
                    // First comment
                    //  Second comment
                    //  spanning two lines
                    // Third comment
                    b: 12
                }
            """.trimIndent(),
            generate(newHumanReadableStrategy()) {
                put(Token.BeginObject)
                put(Token.MemberName("a"))
                put(Token.SignedInteger(11))
                writeComment("First comment")
                writeComment(" Second comment\n spanning two lines")
                writeComment("Third comment")
                put(Token.MemberName("b"))
                put(Token.SignedInteger(12))
                put(Token.EndObject)
            })
    }

    @Test
    fun `line terminators in serial comments are translated`() {
        assertEquals(
            """
                {
                    // a
                    // b
                    // c
                    // d
                    // e
                    // f
                    x: null
                }
            """.trimIndent(),
            generate(newHumanReadableStrategy()) {
                put(Token.BeginObject)
                writeComment("a\r\nb\nc\rd\u2028e\u2029f")
                put(Token.MemberName("x"))
                put(Token.Null)
                put(Token.EndObject)
            }
        )
    }
}

private fun generate(strategy: OutputStrategy, block: FormatGenerator.() -> Unit): String {
    val sink = StringOutputSink()
    val writer = FormatGenerator(sink, strategy)
    writer.apply(block)
    writer.put(Token.EndOfFile)
    return sink.toString()
}

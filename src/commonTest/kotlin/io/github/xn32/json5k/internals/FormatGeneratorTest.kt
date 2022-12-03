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
    fun nullValue() {
        assertEquals("null", generate(OutputStrategy.Compressed) {
            put(Token.Null)
        })
    }

    @Test
    fun booleanValues() {
        assertEquals("true", generate(OutputStrategy.Compressed) {
            put(Token.Bool(true))
        })

        assertEquals("false", generate(OutputStrategy.Compressed) {
            put(Token.Bool(false))
        })
    }

    @Test
    fun signedNegativeInteger() {
        assertEquals("-4443", generate(OutputStrategy.Compressed) {
            put(Token.SignedInteger(-4443))
        })
    }

    @Test
    fun signedPositiveInteger() {
        assertEquals("33332", generate(OutputStrategy.Compressed) {
            put(Token.SignedInteger(33332))
        })
    }

    @Test
    fun unsignedInteger() {
        assertEquals("7371823712", generate(OutputStrategy.Compressed) {
            put(Token.UnsignedInteger(7371823712u))
        })
    }

    @Test
    fun largeFloatingPointNumber() {
        assertEquals("1e+9", generate(OutputStrategy.Compressed) {
            put(Token.FloatingPoint(1e9))
        })

        assertEquals("4.511e+31", generate(OutputStrategy.Compressed) {
            put(Token.FloatingPoint(45.11e30))
        })
    }

    @Test
    fun smallFloatingPointNumber() {
        assertEquals("-2e-10", generate(OutputStrategy.Compressed) {
            put(Token.FloatingPoint(-2e-10))
        })

        assertEquals("9.9e-7", generate(OutputStrategy.Compressed) {
            put(Token.FloatingPoint(0.99e-6))
        })
    }

    @Test
    fun miscFloatingPointNumber() {
        assertEquals("0.0", generate(OutputStrategy.Compressed) {
            put(Token.FloatingPoint(0.0))
        })

        assertEquals("999999999.0", generate(OutputStrategy.Compressed) {
            put(Token.FloatingPoint(0.999999999e9))
        })

        assertEquals("-0.1123", generate(OutputStrategy.Compressed) {
            put(Token.FloatingPoint(-0.1123))
        })

        assertEquals("0.000001", generate(OutputStrategy.Compressed) {
            put(Token.FloatingPoint(1e-6))
        })
    }

    @Test
    fun numericLiterals() {
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
    fun emptyObject() {
        assertEquals("{}", generate(OutputStrategy.Compressed) {
            put(Token.BeginObject)
            put(Token.EndObject)
        })
    }

    @Test
    fun singleMemberObject() {
        assertEquals("{abc:10}", generate(OutputStrategy.Compressed) {
            put(Token.BeginObject)
            put(Token.MemberName("abc"))
            put(Token.SignedInteger(10))
            put(Token.EndObject)
        })
    }

    @Test
    fun multipleMemberObject() {
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
    fun nestedStructure() {
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
    fun stringQuotation() {
        assertEquals("\"string\"", generate(OutputStrategy.Compressed) {
            put(Token.Str("string"))
        })
    }

    @Test
    fun humanReadableStringQuotation() {
        assertEquals("\"12'34\\\"56\"", generate(newHumanReadableStrategy()) {
            put(Token.Str("12'34\"56"))
        })
    }

    @Test
    fun configureStringQuotation() {
        assertEquals("'12\\\'34\"56'", generate(newHumanReadableStrategy(quoteCharacter = '\'')) {
            put(Token.Str("12'34\"56"))
        })
    }

    @Test
    fun escapeCrAndLf() {
        assertEquals("\"first\\r\\nsecond\"", generate(OutputStrategy.Compressed) {
            put(Token.Str("first\r\nsecond"))
        })
    }

    @Test
    fun escapeLsAndPs() {
        assertEquals("\"x\\u2028y\\u2029z\"", generate(OutputStrategy.Compressed) {
            put(Token.Str("x\u2028y\u2029z"))
        })
    }

    @Test
    fun escapeQuotesAndBackslash() {
        assertEquals(""""string = \"\\underline\", char = '\\'"""", generate(OutputStrategy.Compressed) {
            put(Token.Str("string = \"\\underline\", char = '\u005c'"))
        })
    }

    @Test
    fun escapeOtherChars() {
        assertEquals("\"\u0000\u2000\ud83c\udfbc\"", generate(OutputStrategy.Compressed) {
            put(Token.Str("\u0000\u2000\ud83c\udfbc"))
        })
    }

    @Test
    fun memberNameQuotation() {
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
    fun enforceMemberNameQuotation() {
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
    fun outputIndentation() {
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
    fun configureIndentation() {
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
    fun compressEmptyStructs() {
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
    fun consecutiveStructs() {
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
    fun serialCommentsInCompressedOutput() {
        assertEquals("{a:10}", generate(OutputStrategy.Compressed) {
            put(Token.BeginObject)
            writeComment("comment")
            put(Token.MemberName("a"))
            put(Token.SignedInteger(10))
            put(Token.EndObject)
        })
    }

    @Test
    fun serialCommentsInHumanReadableOutput() {
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
    fun lineTerminatorsInSerialComments() {
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

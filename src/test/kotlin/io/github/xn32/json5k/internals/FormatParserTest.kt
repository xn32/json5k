package io.github.xn32.json5k.internals

import io.github.xn32.json5k.CharError
import io.github.xn32.json5k.EndOfFileError
import io.github.xn32.json5k.LiteralError
import io.github.xn32.json5k.OverflowError
import io.github.xn32.json5k.ParsingError
import io.github.xn32.json5k.check
import io.github.xn32.json5k.checkPosition
import io.github.xn32.json5k.format.Token
import io.github.xn32.json5k.parsing.FormatParser
import io.github.xn32.json5k.parsing.Parser
import org.junit.jupiter.api.Nested
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

private fun parserFor(str: String) = FormatParser(str.byteInputStream())

class FormatParserTest {
    @Nested
    inner class BaseParsingTest {
        @Test
        fun `empty input causes error`() {
            parserFor("").checkError<EndOfFileError>(1, 1)
        }

        @Test
        fun `top-level whitespace is ignored`() {
            parserFor("\uFEFF \t\r\n\t\u000B\u000C\u00A0\u2001\u3000").checkError<EndOfFileError>(2, 7)
        }

        @Test
        fun `unexpected top-level elements are reported`() {
            parserFor("%").checkError<CharError>(1, 1) {
                assertContains(it.message, "%")
            }
        }

        @Test
        fun `reverse lookup for unexpected characters is performed`() {
            parserFor("\b").checkError<CharError>(1, 1) {
                assertContains(it.message, "\\b")
            }
        }

        @Test
        fun `unexpected tokens are transcribed if necessary`() {
            parserFor("\u000f").checkError<CharError>(1, 1) {
                assertContains(it.message, "U+000F")
            }
        }

        @Test
        fun `end of file event is repeated`() {
            parserFor("null").apply {
                checkNext<Token.Null>()
                repeat(10) {
                    checkNext<Token.EndOfFile>()
                }
            }
        }
    }

    @Nested
    inner class StructureParsingTest {
        @Test
        fun `array of literals is recognized`() {
            parserFor("\r\n[null, \nfalse\t]/**/").apply {
                checkNext<Token.BeginArray>(2, 1)
                checkNext<Token.Null>(2, 2)
                checkNext<Token.Bool>(3, 1) {
                    assertEquals(false, it.bool)
                }

                checkNext<Token.EndArray>(3, 7)
                checkEnd(3, 12)
            }
        }

        @Test
        fun `trailing comma in array is supported`() {
            parserFor("[null,]").apply {
                checkNext<Token.BeginArray>(1, 1)
                checkNext<Token.Null>(1, 2)
                checkNext<Token.EndArray>(1, 7)
                checkEnd(1, 8)
            }
        }

        @Test
        fun `unclosed array is detected`() {
            parserFor("[null,").apply {
                checkNext<Token.BeginArray>(1, 1)
                checkNext<Token.Null>(1, 2)
                checkError<EndOfFileError>(1, 7)
            }
        }

        @Test
        fun `unclosed object is detected`() {
            parserFor("{key: 10").apply {
                checkNext<Token.BeginObject>(1, 1)
                checkNext<Token.MemberName>(1, 2)
                checkNext<Token.Value>(1, 7)
                checkError<EndOfFileError>(1, 9)
            }
        }

        @Test
        fun `non-matching struct closer causes error`() {
            parserFor("[null, true}").apply {
                checkNext<Token.BeginArray>(1, 1)
                checkNext<Token.Null>(1, 2)
                checkNext<Token.Bool>(1, 8)
                checkError<CharError>(1, 12)
            }
        }

        @Test
        fun `repeated comma in array causes error`() {
            parserFor("[true,,false]").apply {
                checkNext<Token.BeginArray>(1, 1)
                checkNext<Token.Bool>(1, 2)
                checkError<CharError>(1, 7) {
                    assertEquals(',', it.char)
                }
            }
        }

        @Test
        fun `array elements must be separated`() {
            parserFor("[null false]").apply {
                checkNext<Token.BeginArray>(1, 1)
                checkNext<Token.Null>(1, 2)
                checkError<CharError>(1, 7) {
                    assertEquals('f', it.char)
                }
            }
        }

        @Test
        fun `missing member value causes error`() {
            parserFor("{key:}").apply {
                checkNext<Token.BeginObject>(1, 1)
                checkNext<Token.MemberName>(1, 2)
                checkError<CharError>(1, 6)
            }
        }

        @Test
        fun `nested arrays work`() {
            parserFor("[[null, true], null]").apply {
                checkNext<Token.BeginArray>(1, 1)
                checkNext<Token.BeginArray>(1, 2)
                checkNext<Token.Null>(1, 3)
                checkNext<Token.Bool>(1, 9)
                checkNext<Token.EndArray>(1, 13)
                checkNext<Token.Null>(1, 16)
                checkNext<Token.EndArray>(1, 20)
                checkEnd(1, 21)
            }
        }

        @Test
        fun `single-member object is recognized`() {
            parserFor("{abc: null}").apply {
                checkNext<Token.BeginObject>(1, 1)
                checkNext<Token.MemberName>(1, 2) {
                    assertEquals("abc", it.name)
                }

                checkNext<Token.Null>(1, 7)
                checkNext<Token.EndObject>(1, 11)
                checkEnd(1, 12)
            }
        }

        @Test
        fun `nested objects work`() {
            parserFor("{member: {key: null}}").apply {
                checkNext<Token.BeginObject>(1, 1)
                checkNext<Token.MemberName>(1, 2) {
                    assertEquals("member", it.name)
                }

                checkNext<Token.BeginObject>(1, 10)
                checkNext<Token.MemberName>(1, 11) {
                    assertEquals("key", it.name)
                }

                checkNext<Token.Null>(1, 16)
                checkNext<Token.EndObject>(1, 20)
                checkNext<Token.EndObject>(1, 21)
                checkEnd(1, 22)
            }
        }

        @Test
        fun `literal member name is accepted`() {
            parserFor("{null: null}").apply {
                checkNext<Token.BeginObject>(1, 1)
                checkNext<Token.MemberName>(1, 2) {
                    assertEquals("null", it.name)
                }

                checkNext<Token.Null>(1, 8)
                checkNext<Token.EndObject>(1, 12)
                checkEnd(1, 13)
            }
        }

        @Test
        fun `repeated member name causes error`() {
            parserFor("{first: second:").apply {
                checkNext<Token.BeginObject>(1, 1)
                checkNext<Token.MemberName>(1, 2)
                checkError<LiteralError>(1, 9) {
                    assertEquals("second", it.literal)
                }
            }
        }

        @Test
        fun `trailing comma in object works`() {
            parserFor("{member: null,}").apply {
                checkNext<Token.BeginObject>(1, 1)
                checkNext<Token.MemberName>(1, 2)
                checkNext<Token.Null>(1, 10)
                checkNext<Token.EndObject>(1, 15)
                checkEnd(1, 16)
            }
        }

        @Test
        fun `top-level object with trailing comma causes error`() {
            parserFor("{},").apply {
                checkNext<Token.BeginObject>(1, 1)
                checkNext<Token.EndObject>(1, 2)
                checkError<CharError>(1, 3) {
                    assertEquals(',', it.char)
                }
            }
        }
    }

    @Nested
    inner class LiteralParsingTest {
        @Test
        fun `top-level null is recognized`() {
            parserFor("null").checkNext<Token.Null>(1, 1).checkEnd(1, 5)
        }

        @Test
        fun `top-level true is recognized`() {
            parserFor("true").checkNext<Token.Bool>(1, 1).checkEnd(1, 5)
        }

        @Test
        fun `top-level false is recognized`() {
            parserFor("false").checkNext<Token.Bool>(1, 1).checkEnd(1, 6)
        }

        @Test
        fun `whitespace around literals is ignored`() {
            parserFor("\r\r null\r\n").checkNext<Token.Null>(3, 2).checkEnd(4, 1)
            parserFor(" false ").checkNext<Token.Bool>(1, 2).checkEnd(1, 8)
            parserFor(" +Infinity\n\n").checkNext<Token.Num>(1, 2).checkEnd(3, 1)
            parserFor("null\n\nx").apply {
                checkNext<Token.Null>(1, 1)
                checkError<CharError>(3, 1)
            }
        }

        @Test
        fun `comments around literals are ignored`() {
            parserFor("/*x*/null/*y*/").checkNext<Token.Null>(1, 6).checkEnd(1, 15)
            parserFor("//x\r\nnull//y").checkNext<Token.Null>(2, 1).checkEnd(2, 8)
        }

        @Test
        fun `top-level numeric literal without sign is recognized`() {
            parserFor("Infinity").checkFloatingPoint(1, 1, Double.POSITIVE_INFINITY).checkEnd(1, 9)
            parserFor("NaN").checkFloatingPoint(1, 1, Double.NaN).checkEnd(1, 4)
        }

        @Test
        fun `top-level numeric literal with sign is recognized`() {
            parserFor("+Infinity").checkFloatingPoint(1, 1, Double.POSITIVE_INFINITY).checkEnd(1, 10)
            parserFor("-Infinity").checkFloatingPoint(1, 1, Double.NEGATIVE_INFINITY).checkEnd(1, 10)
            parserFor("+NaN").checkFloatingPoint(1, 1, Double.NaN).checkEnd(1, 5)
            parserFor("-NaN").checkFloatingPoint(1, 1, Double.NaN).checkEnd(1, 5)
        }

        @Test
        fun `unknown literal is reported`() {
            parserFor("oranges").checkError<LiteralError>(1, 1)
        }
    }

    @Nested
    inner class StringParsingTest {
        @Test
        fun `plain double string is recognized`() {
            val str = "ab 'cd' ef"
            parserFor("\t\"$str\"").checkNext<Token.Str>(1, 2) {
                assertEquals(str, it.string)
            }.checkEnd(1, 14)
        }

        @Test
        fun `plain single string is recognized`() {
            val str = "ab \"cd\" ef"
            parserFor("\t'$str'").checkNext<Token.Str>(1, 2) {
                assertEquals(str, it.string)
            }.checkEnd(1, 14)
        }

        @Test
        fun `line continuation in string works`() {
            parserFor("\t'x \\\ny'\t").checkNext<Token.Str>(1, 2) {
                assertEquals("x y", it.string)
            }.checkEnd(2, 4)
        }

        @Test
        fun `line terminator in string causes error`() {
            parserFor("\t'abc de\nabc de'").checkError<CharError>(1, 9) {
                assertEquals('\n', it.char)
            }
        }

        @Test
        fun `unsupported escape sequence causes error`() {
            parserFor("'xyz \\4 xyz'").checkError<CharError>(1, 7)
        }

        @Test
        fun `incomplete hex token in string causes error`() {
            parserFor("'\\xaZ'").checkError<CharError>(1, 5)
        }

        @Test
        fun `incomplete unicode token in string causes error`() {
            parserFor("'x\\u123$'").checkError<CharError>(1, 8)
        }

        @Test
        fun `non-terminated string causes error`() {
            parserFor("\t'example").checkError<EndOfFileError>(1, 10)
        }

        @Test
        fun `incomplete escape sequence causes error`() {
            parserFor("'\\").checkError<EndOfFileError>(1, 3)
        }

        @Test
        fun `lowercase hex chars are decoded`() {
            parserFor("'\\u22c6'").checkNext<Token.Str>(1, 1) {
                assertEquals("\u22c6", it.string)
            }.checkEnd(1, 9)
        }

        @Test
        fun `uppercase hex chars are decoded`() {
            parserFor("'\\u215E'").checkNext<Token.Str>(1, 1) {
                assertEquals("\u215E", it.string)
            }.checkEnd(1, 9)
        }

        @Test
        fun `unicode escape sequence works`() {
            parserFor("'a\\u1234z'").checkNext<Token.Str>(1, 1) {
                assertEquals("a\u1234z", it.string)
            }.checkEnd(1, 11)
        }

        @Test
        fun `valid UTF-16 surrogate pair is recognized`() {
            parserFor("'\\uD83C\\uDFBC'").checkNext<Token.Str>(1, 1) {
                assertEquals("\ud83c\udfbc", it.string)
            }.checkEnd(1, 15)
        }

        @Test
        fun `hex byte escape sequence works`() {
            parserFor("'a\\x12z'").checkNext<Token.Str>(1, 1) {
                assertEquals("a\u0012z", it.string)
            }.checkEnd(1, 9)
        }

        @Test
        fun `non-escape characters are translated`() {
            parserFor("'\\a\\c\\$'").checkNext<Token.Str>(1, 1) {
                assertEquals("ac$", it.string)
            }.checkEnd(1, 9)
        }

        @Test
        fun `null byte escape sequence works`() {
            parserFor("'\\0x\\0y'").checkNext<Token.Str>(1, 1) {
                assertEquals("\u0000x\u0000y", it.string)
            }.checkEnd(1, 9)
        }

        @Test
        fun `LS and PS may appear unescaped`() {
            val str = "\u2028+\u2029"
            parserFor("'$str'").checkNext<Token.Str>(1, 1) {
                assertEquals(str, it.string)
            }.checkEnd(3, 2)
        }

        @Test
        fun `event position correct after line continuation`() {
            parserFor("'Some\\\nstring'~").apply {
                checkNext<Token.Str>(1, 1)
                checkError<CharError>(2, 8)
            }
        }
    }

    @Nested
    inner class NumberParsingTest {
        @Test
        fun `zero is recognized`() {
            parserFor("0").checkNext<Token.SignedInteger>(1, 1) {
                assertEquals(0, it.number)
            }.checkEnd(1, 2)
        }

        @Test
        fun `decimal integer literal must not start with a zero`() {
            parserFor("01").apply {
                checkNext<Token.SignedInteger>(1, 1)
                checkError<CharError>(1, 2)
            }
        }

        @Test
        fun `fractional number can start with a zero`() {
            parserFor("0.111").apply {
                checkFloatingPoint(1, 1, 0.111)
                checkEnd(1, 6)
            }
        }

        @Test
        fun `leading decimal point is accepted`() {
            parserFor(".45").checkFloatingPoint(1, 1, 0.45).checkEnd(1, 4)
        }

        @Test
        fun `hexadecimal number is recognized`() {
            parserFor("0XaA01").checkNext<Token.SignedInteger>(1, 1) {
                assertEquals(0xAA01, it.number)
            }.checkEnd(1, 7)

            parserFor(" 0xaAbB ").checkNext<Token.SignedInteger>(1, 2) {
                assertEquals(0xAABB, it.number)
            }.checkEnd(1, 9)
        }

        @Test
        fun `decimal integer is recognized`() {
            parserFor("400").checkNext<Token.SignedInteger>(1, 1) {
                assertEquals(400, it.number)
            }.checkEnd(1, 4)
        }

        @Test
        fun `large decimal integer is returned as unsigned value`() {
            parserFor("0x7FFFFFFFFFFFFFFF").checkNext<Token.SignedInteger>(1, 1) {
                assertEquals(Long.MAX_VALUE, it.number)
            }.checkEnd(1, 19)

            parserFor("0x8000000000000000").checkNext<Token.UnsignedInteger>(1, 1) {
                assertEquals(0x8000000000000000u, it.number)
            }.checkEnd(1, 19)
        }

        @Test
        fun `overflow on negative integers is recognized`() {
            parserFor("-0x8000000000000000").checkNext<Token.SignedInteger>(1, 1)
            parserFor("-0x8000000000000001").checkError<OverflowError>(1, 1)
        }

        @Test
        fun `overflow on positive integers is recognized`() {
            parserFor("0xFFFFFFFFFFFFFFFF").checkNext<Token.UnsignedInteger>(1, 1) {
                assertEquals(ULong.MAX_VALUE, it.number)
            }.checkEnd(1, 19)

            parserFor("0x10000000000000000").checkError<OverflowError>(1, 1)
        }

        @Test
        fun `decimal integer can have trailing decimal point`() {
            parserFor("55.").checkFloatingPoint(1, 1, 55.0).checkEnd(1, 4)
        }

        @Test
        fun `numbers with exponent are decimal numbers`() {
            parserFor("11e0").checkFloatingPoint(1, 1, 11.0).checkEnd(1, 5)
        }

        @Test
        fun `negative decimal integer is recognized`() {
            parserFor("-11").checkNext<Token.SignedInteger>(1, 1) {
                assertEquals(-11, it.number)
            }.checkEnd(1, 4)
        }

        @Test
        fun `negative hexadecimal integer is recognized`() {
            parserFor("-0xFFE").checkNext<Token.SignedInteger>(1, 1) {
                assertEquals(-0xFFE, it.number)
            }.checkEnd(1, 7)
        }

        @Test
        fun `negative fractional number is recognized`() {
            parserFor("-0.25").checkFloatingPoint(1, 1, -0.25).checkEnd(1, 6)
        }

        @Test
        fun `single decimal point causes error`() {
            parserFor(".").checkError<EndOfFileError>(1, 2)
            parserFor(".z").checkError<CharError>(1, 2)
            parserFor("\n.\t").checkError<CharError>(2, 2)
        }

        @Test
        fun `exponents with lowercase indicator are recognized`() {
            parserFor("11.2e-3").checkFloatingPoint(1, 1, .0112).checkEnd(1, 8)
            parserFor("55e10").checkFloatingPoint(1, 1, 55e10).checkEnd(1, 6)
            parserFor("10e+3").checkFloatingPoint(1, 1, 10e3).checkEnd(1, 6)
        }

        @Test
        fun `uppercase exponent indicator is recognized`() {
            parserFor(".4E10").checkFloatingPoint(1, 1, .4e10).checkEnd(1, 6)
        }

        @Test
        fun `erroneous exponent indicator usage is reported`() {
            parserFor(".4f10").apply {
                checkNext<Token.FloatingPoint>(1, 1)
                checkError<CharError>(1, 3)
            }

            parserFor("4 e10").apply {
                checkNext<Token.SignedInteger>(1, 1)
                checkError<CharError>(1, 3)
            }
        }

        @Test
        fun `repeated decimal points are detected`() {
            parserFor("10.25.5").apply {
                checkNext<Token.FloatingPoint>(1, 1)
                checkError<CharError>(1, 6)
            }
        }
    }
}

internal typealias OptionalCheck<T> = ((T) -> Unit)?

private inline fun <reified T : Token> Parser<Token>.checkNext(
    line: Int? = null, column: Int? = null, noinline check: OptionalCheck<T> = null
): Parser<Token> {
    val (pos, token) = next()
    if (line != null) {
        pos.check(line, column)
    }

    assertIs<T>(token)
    check?.invoke(token)
    return this
}

private inline fun <reified E : ParsingError> Parser<Token>.checkError(
    line: Int, column: Int, noinline check: OptionalCheck<E> = null
): Parser<Token> {
    val e = assertFailsWith<E> { next() }
    e.checkPosition(line, column)
    check?.invoke(e)

    return this
}

private fun Parser<Token>.checkFloatingPoint(line: Int? = null, column: Int? = null, value: Double): Parser<Token> =
    checkNext<Token.FloatingPoint>(line, column) {
        assertEquals(value, it.number, 1e-10)
    }

private fun Parser<Token>.checkEnd(line: Int? = null, column: Int? = null) {
    checkNext<Token.EndOfFile>(line, column)
}

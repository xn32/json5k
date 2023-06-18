package io.github.xn32.json5k.internals

import io.github.xn32.json5k.CharError
import io.github.xn32.json5k.EndOfFileError
import io.github.xn32.json5k.LiteralError
import io.github.xn32.json5k.ParsingError
import io.github.xn32.json5k.check
import io.github.xn32.json5k.checkPosition
import io.github.xn32.json5k.format.Token
import io.github.xn32.json5k.parsing.FormatParser
import io.github.xn32.json5k.parsing.Parser
import io.github.xn32.json5k.parsing.StringInputSource
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

private fun parserFor(str: String): FormatParser = FormatParser(StringInputSource(str))

class FormatParserTest {
    @Test
    fun emptyInput() {
        parserFor("").checkError<EndOfFileError>(1, 1)
    }

    @Test
    fun topLevelWhitespace() {
        parserFor("\uFEFF \t\r\n\t\u000B\u000C\u00A0\u2001\u3000").checkError<EndOfFileError>(2, 7)
    }

    @Test
    fun unexpectedTopLevelChar() {
        parserFor("%").checkError<CharError>(1, 1) {
            assertContains(it.violation, "%")
        }
    }

    @Test
    fun reverseLookupForErrors() {
        parserFor("\b").checkError<CharError>(1, 1) {
            assertContains(it.violation, "\\b")
        }
    }

    @Test
    fun transcriptionForErrorMessages() {
        parserFor("\u000f").checkError<CharError>(1, 1) {
            assertContains(it.violation, "U+000F")
        }
    }

    @Test
    fun endOfFileEvents() {
        parserFor("null").apply {
            checkNext<Token.Null>()
            repeat(10) {
                checkNext<Token.EndOfFile>()
            }
        }
    }

    @Test
    fun arrayParsing() {
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
    fun trailingCommaInArray() {
        parserFor("[null,]").apply {
            checkNext<Token.BeginArray>(1, 1)
            checkNext<Token.Null>(1, 2)
            checkNext<Token.EndArray>(1, 7)
            checkEnd(1, 8)
        }
    }

    @Test
    fun unclosedArray() {
        parserFor("[null,").apply {
            checkNext<Token.BeginArray>(1, 1)
            checkNext<Token.Null>(1, 2)
            checkError<EndOfFileError>(1, 7)
        }
    }

    @Test
    fun unclosedObject() {
        parserFor("{key: 10").apply {
            checkNext<Token.BeginObject>(1, 1)
            checkNext<Token.MemberName>(1, 2)
            checkNext<Token.Value>(1, 7)
            checkError<EndOfFileError>(1, 9)
        }
    }

    @Test
    fun unexpectedStructCloser() {
        parserFor("[null, true}").apply {
            checkNext<Token.BeginArray>(1, 1)
            checkNext<Token.Null>(1, 2)
            checkNext<Token.Bool>(1, 8)
            checkError<CharError>(1, 12)
        }

        parserFor("{a: 10]").apply {
            checkNext<Token.BeginObject>(1, 1)
            checkNext<Token.MemberName>(1, 2)
            checkNext<Token.Num>(1, 5)
            checkError<CharError>(1, 7)
        }
    }

    @Test
    fun repeatedComma() {
        parserFor("[true,,false]").apply {
            checkNext<Token.BeginArray>(1, 1)
            checkNext<Token.Bool>(1, 2)
            checkError<CharError>(1, 7) {
                assertEquals(',', it.char)
            }
        }
    }

    @Test
    fun missingComma() {
        parserFor("[null false]").apply {
            checkNext<Token.BeginArray>(1, 1)
            checkNext<Token.Null>(1, 2)
            checkError<CharError>(1, 7) {
                assertEquals('f', it.char)
            }
        }
    }

    @Test
    fun missingMemberValue() {
        parserFor("{key:}").apply {
            checkNext<Token.BeginObject>(1, 1)
            checkNext<Token.MemberName>(1, 2)
            checkError<CharError>(1, 6)
        }

        parserFor("{key=true}").apply {
            checkNext<Token.BeginObject>(1, 1)
            checkError<CharError>(1, 5)
        }

        parserFor("{key").apply {
            checkNext<Token.BeginObject>(1, 1)
            checkError<EndOfFileError>(1, 5)
        }
    }

    @Test
    fun invalidMemberName() {
        parserFor("{#x:10}").apply {
            checkNext<Token.BeginObject>(1, 1)
            checkError<CharError>(1, 2)
        }

        parserFor("{_\\n:10}").apply {
            checkNext<Token.BeginObject>(1, 1)
            checkError<CharError>(1, 4)
        }

        parserFor("{_\\").apply {
            checkNext<Token.BeginObject>(1, 1)
            checkError<EndOfFileError>(1, 4)
        }
    }

    @Test
    fun memberName() {
        parserFor("{'a':10,\"b\":20}").apply {
            checkNext<Token.BeginObject>(1, 1)
            checkNext<Token.MemberName>(1, 2)
            checkNext<Token.Num>(1, 6)
            checkNext<Token.MemberName>(1, 9)
            checkNext<Token.Num>(1, 13)
            checkNext<Token.EndObject>(1, 15)
        }

        parserFor("{\\u0069d: 0}").apply {
            checkNext<Token.BeginObject>(1, 1)
            checkNext<Token.MemberName>(1, 2) {
                assertEquals("id", it.name)
            }

            checkNext<Token.Num>(1, 11)
            checkNext<Token.EndObject>(1, 12)
        }
    }

    @Test
    fun nestedArrays() {
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
    fun singleMemberObject() {
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
    fun nestedObjects() {
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
    fun literalMemberName() {
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
    fun repeatedMemberName() {
        parserFor("{first: second:").apply {
            checkNext<Token.BeginObject>(1, 1)
            checkNext<Token.MemberName>(1, 2)
            checkError<LiteralError>(1, 9) {
                assertEquals("second", it.literal)
            }
        }
    }

    @Test
    fun trailingCommaInObject() {
        parserFor("{member: null,}").apply {
            checkNext<Token.BeginObject>(1, 1)
            checkNext<Token.MemberName>(1, 2)
            checkNext<Token.Null>(1, 10)
            checkNext<Token.EndObject>(1, 15)
            checkEnd(1, 16)
        }
    }

    @Test
    fun commaAfterTopLevelValue() {
        parserFor("{},").apply {
            checkNext<Token.BeginObject>(1, 1)
            checkNext<Token.EndObject>(1, 2)
            checkError<CharError>(1, 3) {
                assertEquals(',', it.char)
            }
        }
    }

    @Test
    fun topLevelNull() {
        parserFor("null").checkNext<Token.Null>(1, 1).checkEnd(1, 5)
    }

    @Test
    fun topLevelTrue() {
        parserFor("true").checkNext<Token.Bool>(1, 1).checkEnd(1, 5)
    }

    @Test
    fun topLevelFalse() {
        parserFor("false").checkNext<Token.Bool>(1, 1).checkEnd(1, 6)
    }

    @Test
    fun whitespaceAroundLiterals() {
        parserFor("\r\r null\r\n").checkNext<Token.Null>(3, 2).checkEnd(4, 1)
        parserFor(" false ").checkNext<Token.Bool>(1, 2).checkEnd(1, 8)
        parserFor(" +Infinity\n\n").checkNext<Token.Num>(1, 2).checkEnd(3, 1)
        parserFor("null\n\nx").apply {
            checkNext<Token.Null>(1, 1)
            checkError<CharError>(3, 1)
        }
    }

    @Test
    fun commentsAroundLiterals() {
        parserFor("/*x*/null/*y*/").checkNext<Token.Null>(1, 6).checkEnd(1, 15)
        parserFor("//x\r\nnull//y").checkNext<Token.Null>(2, 1).checkEnd(2, 8)
    }

    @Test
    fun topLevelNumericLiteral() {
        parserFor("Infinity").checkNext<Token.Num>(1, 1).checkEnd(1, 9)
        parserFor("NaN").checkNext<Token.Num>(1, 1).checkEnd(1, 4)
    }

    @Test
    fun topLevelNumericLiteralWithSign() {
        parserFor("+Infinity").checkNext<Token.Num>(1, 1).checkEnd(1, 10)
        parserFor("-Infinity").checkNext<Token.Num>(1, 1).checkEnd(1, 10)
        parserFor("+NaN").checkNext<Token.Num>(1, 1).checkEnd(1, 5)
        parserFor("-NaN").checkNext<Token.Num>(1, 1).checkEnd(1, 5)
    }

    @Test
    fun unknownLiteral() {
        parserFor("oranges").checkError<LiteralError>(1, 1)
    }

    @Test
    fun plainDoubleString() {
        val str = "ab 'cd' ef"
        parserFor("\t\"$str\"").checkNext<Token.Str>(1, 2) {
            assertEquals(str, it.string)
        }.checkEnd(1, 14)
    }

    @Test
    fun plainSingleString() {
        val str = "ab \"cd\" ef"
        parserFor("\t'$str'").checkNext<Token.Str>(1, 2) {
            assertEquals(str, it.string)
        }.checkEnd(1, 14)
    }

    @Test
    fun lineContinuationInString() {
        parserFor("\t'x \\\ny'\t").checkNext<Token.Str>(1, 2) {
            assertEquals("x y", it.string)
        }.checkEnd(2, 4)
    }

    @Test
    fun lineTerminatorInString() {
        parserFor("\t'abc de\nabc de'").checkError<CharError>(1, 9) {
            assertEquals('\n', it.char)
        }
    }

    @Test
    fun singleEscapeSequence() {
        parserFor("'x\\by'").checkNext<Token.Str>(1, 1) {
            assertEquals("x\by", it.string)
        }
    }

    @Test
    fun unknownEscapeSequence() {
        parserFor("'xyz \\4 xyz'").checkError<CharError>(1, 7)
    }

    @Test
    fun incompleteHexToken() {
        parserFor("'\\xaZ'").checkError<CharError>(1, 5)
    }

    @Test
    fun incompleteUnicodeToken() {
        parserFor("'x\\u123$'").checkError<CharError>(1, 8)
    }

    @Test
    fun unterminatedString() {
        parserFor("\t'example").checkError<EndOfFileError>(1, 10)
    }

    @Test
    fun incompleteEscapeSequence() {
        parserFor("'\\").checkError<EndOfFileError>(1, 3)
    }

    @Test
    fun lowercaseUnicodeEscapeSequence() {
        parserFor("'\\u22c6'").checkNext<Token.Str>(1, 1) {
            assertEquals("\u22c6", it.string)
        }.checkEnd(1, 9)
    }

    @Test
    fun uppercaseUnicodeEscapeSequence() {
        parserFor("'\\u215E'").checkNext<Token.Str>(1, 1) {
            assertEquals("\u215E", it.string)
        }.checkEnd(1, 9)
    }

    @Test
    fun unicodeSurrogatePair() {
        parserFor("'\\uD83C\\uDFBC'").checkNext<Token.Str>(1, 1) {
            assertEquals("\ud83c\udfbc", it.string)
        }.checkEnd(1, 15)
    }

    @Test
    fun hexEscapeSequence() {
        parserFor("'a\\x12z'").checkNext<Token.Str>(1, 1) {
            assertEquals("a\u0012z", it.string)
        }.checkEnd(1, 9)
    }

    @Test
    fun nonEscapedChars() {
        parserFor("'\\a\\c\\$'").checkNext<Token.Str>(1, 1) {
            assertEquals("ac$", it.string)
        }.checkEnd(1, 9)
    }

    @Test
    fun nullByteEscapeSequence() {
        parserFor("'\\0x\\0y'").checkNext<Token.Str>(1, 1) {
            assertEquals("\u0000x\u0000y", it.string)
        }.checkEnd(1, 9)
    }

    @Test
    fun unescapedLsAndPs() {
        val str = "\u2028+\u2029"
        parserFor("'$str'").checkNext<Token.Str>(1, 1) {
            assertEquals(str, it.string)
        }.checkEnd(3, 2)
    }

    @Test
    fun positionAfterLineContinuation() {
        parserFor("'Some\\\nstring'~").apply {
            checkNext<Token.Str>(1, 1)
            checkError<CharError>(2, 8)
        }
    }

    @Test
    fun topLevelZero() {
        parserFor("0").checkNext<Token.Num>(1, 1) {
            assertEquals("0", it.rep)
        }.checkEnd(1, 2)
    }

    @Test
    fun leadingZero() {
        parserFor("01").apply {
            checkNext<Token.Num>(1, 1)
            checkError<CharError>(1, 2)
        }
    }

    @Test
    fun fractionalNumber() {
        parserFor("0.111").checkNext<Token.Num>(1, 1).checkEnd(1, 6)
    }

    @Test
    fun leadingDecimalPoint() {
        parserFor(".45").checkNext<Token.Num>(1, 1).checkEnd(1, 4)
    }

    @Test
    fun hexNumber() {
        parserFor("0XaA01").checkNext<Token.Num>(1, 1) {
            assertEquals("0xaA01", it.rep)
        }.checkEnd(1, 7)

        parserFor(" 0xaAbB ").checkNext<Token.Num>(1, 2) {
            assertEquals("0xaAbB", it.rep)
        }.checkEnd(1, 9)
    }

    @Test
    fun decimalInteger() {
        parserFor("+400").checkNext<Token.Num>(1, 1) {
            assertEquals("400", it.rep)
        }.checkEnd(1, 5)
    }

    @Test
    fun noNegativeOverflow() {
        parserFor("-0x8000000000000001").checkNext<Token.Num>(1, 1)
    }

    @Test
    fun noPositiveOverflow() {
        parserFor("0x10000000000000000").checkNext<Token.Num>(1, 1)
    }

    @Test
    fun trailingDecimalPoint() {
        parserFor("55.").checkNext<Token.Num>(1, 1).checkEnd(1, 4)
    }

    @Test
    fun scientificNotation() {
        parserFor("11e0").checkNext<Token.Num>(1, 1).checkEnd(1, 5)
    }

    @Test
    fun negativeDecimalInteger() {
        parserFor("-1").checkNext<Token.Num>(1, 1) {
            assertEquals("-1", it.rep)
        }.checkEnd(1, 3)
    }

    @Test
    fun negativeHexNumber() {
        parserFor("-0xFF1E").checkNext<Token.Num>(1, 1) {
            assertEquals("-0xFF1E", it.rep)
        }.checkEnd(1, 8)
    }

    @Test
    fun negativeFractionalNumber() {
        parserFor("-0.25").checkNext<Token.Num>(1, 1).checkEnd(1, 6)
    }

    @Test
    fun singleDecimalPoint() {
        parserFor(".").checkError<EndOfFileError>(1, 2)
        parserFor(".z").checkError<CharError>(1, 2)
        parserFor("\n.\t").checkError<CharError>(2, 2)
    }

    @Test
    fun lowercaseExponentSymbol() {
        parserFor("11.2e-3").checkNext<Token.Num>(1, 1).checkEnd(1, 8)
        parserFor("55e10").checkNext<Token.Num>(1, 1).checkEnd(1, 6)
        parserFor("10e+3").checkNext<Token.Num>(1, 1).checkEnd(1, 6)
    }

    @Test
    fun uppercaseExponentSymbol() {
        parserFor(".4E10").checkNext<Token.Num>(1, 1).checkEnd(1, 6)
    }

    @Test
    fun erroneousExponentSymbol() {
        parserFor(".4f10").apply {
            checkNext<Token.Num>(1, 1)
            checkError<CharError>(1, 3)
        }

        parserFor("4 e10").apply {
            checkNext<Token.Num>(1, 1)
            checkError<CharError>(1, 3)
        }
    }

    @Test
    fun repeatedDecimalPoint() {
        parserFor("10.25.5").apply {
            checkNext<Token.Num>(1, 1)
            checkError<CharError>(1, 6)
        }
    }

    @Test
    fun incompleteNumber() {
        parserFor("+").checkError<EndOfFileError>(1, 2)
    }

    @Test
    fun incompleteHexNumber() {
        parserFor("0x").checkError<EndOfFileError>(1, 3)
    }

    @Test
    fun incompleteExponent() {
        parserFor("1e").checkError<EndOfFileError>(1, 3)
    }

    @Test
    fun unknownNumericLiteral() {
        parserFor("+None").checkError<LiteralError>(1, 2)
    }
}

internal typealias OptionalCheck<T> = ((T) -> Unit)?

private inline fun <reified T : Token> Parser<Token>.checkNext(
    line: Int? = null,
    column: Int? = null,
    noinline check: OptionalCheck<T> = null
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
    line: Int,
    column: Int,
    noinline check: OptionalCheck<E> = null
): Parser<Token> {
    val e = assertFailsWith<E> { next() }
    e.checkPosition(line, column)
    check?.invoke(e)

    return this
}

private fun Parser<Token>.checkEnd(line: Int? = null, column: Int? = null) {
    checkNext<Token.EndOfFile>(line, column)
}

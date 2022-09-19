package io.github.xn32.json5k.parsing

import io.github.xn32.json5k.LiteralError
import io.github.xn32.json5k.OverflowError
import io.github.xn32.json5k.format.DocumentTracker
import io.github.xn32.json5k.format.DocumentTracker.TokenType
import io.github.xn32.json5k.format.Specification
import io.github.xn32.json5k.format.Token
import io.github.xn32.json5k.isDecimalDigit
import io.github.xn32.json5k.isHexDigit
import io.github.xn32.json5k.throwTokenError
import java.io.InputStream
import kotlin.math.pow

internal class FormatParser(stream: InputStream) : Parser<Token> {
    private val reader: FormatReader = FormatReader(stream)

    private val stringParser = StringParser(reader)
    private val numParser = NumParser(reader)

    private val tracker = DocumentTracker()

    override fun next(): Event<Token> {
        advanceToPrimaryToken()
        val pos = reader.pos
        val token = nextPrimaryToken()
        tracker.supply(token)
        return Event(pos, token)
    }

    private fun advanceToPrimaryToken() {
        reader.advance()
        if (tracker.nextTokenType == TokenType.COMMA && reader.peekOrNull() == ',') {
            reader.consume()
            tracker.supplyComma()
            reader.advance()
        }
    }

    private fun nextPrimaryToken(): Token {
        val next = reader.peekOrNull()
        val structCloserPending = (next == '}' || next == ']')

        return when (tracker.nextTokenType) {
            TokenType.MEMBER_VALUE -> parseValue()

            TokenType.NEXT_ITEM -> {
                if (tracker.inStruct && structCloserPending) {
                    parseStructCloser()
                } else if (tracker.inObjectStruct) {
                    parseMemberName()
                } else {
                    parseValue()
                }
            }

            TokenType.COMMA -> {
                if (structCloserPending) {
                    parseStructCloser()
                } else {
                    reader.throwTokenError()
                }
            }

            TokenType.END_OF_FILE -> {
                if (next == null) {
                    Token.EndOfFile
                } else {
                    reader.throwTokenError()
                }
            }
        }
    }

    private fun parseMemberName(): Token.MemberName {
        val memberName = Token.MemberName(
            when (reader.peek()) {
                '"', '\'' -> stringParser.parseQuotedString()
                else -> stringParser.parseLiteral()
            }
        )

        reader.advance()
        if (reader.done || reader.peek() != ':') {
            reader.throwTokenError()
        }

        reader.consume()
        return memberName
    }

    private fun parseValue(): Token {
        return when (reader.peekOrNull()) {
            '{', '[' -> parseStructOpener()
            '"', '\'' -> Token.Str(stringParser.parseQuotedString())
            in 'a'..'z', in 'A'..'Z' -> parseLiteral()
            in '0'..'9', '+', '-', '.' -> numParser.parseNumber()
            else -> reader.throwTokenError()
        }
    }

    private fun parseLiteral(): Token.Value {
        val pos = reader.pos
        val letters = reader.consumeWhile(Char::isLetter)
        return numLiteralOf(letters) ?: miscLiteralOf(letters) ?: throw LiteralError(letters, pos)
    }

    private fun parseStructOpener(): Token = when (reader.consume()) {
        '{' -> Token.BeginObject
        '[' -> Token.BeginArray
        else -> error("struct opener expected")
    }

    private fun parseStructCloser(): Token {
        assert(!reader.done)
        assert(tracker.inStruct)

        val char = reader.peek()
        val token = if (tracker.inObjectStruct && char == '}') {
            Token.EndObject
        } else if (tracker.inArrayStruct && char == ']') {
            Token.EndArray
        } else {
            reader.throwTokenError()
        }

        reader.consume()
        return token
    }
}

private fun InputReader.readOptionalSign(): Sign {
    return when (peekOrNull()) {
        '+' -> Sign.PLUS
        '-' -> Sign.MINUS
        else -> return Sign.PLUS
    }.also {
        consume()
    }
}

private class NumParser(private val reader: FormatReader) {
    fun parseNumber(): Token.Num {
        val pos = reader.pos
        val negate = reader.readOptionalSign() == Sign.MINUS
        val next = reader.peekOrNull() ?: reader.throwTokenError()

        val value = if (next == '0') {
            parseZeroPrefixedNumber()
        } else if (next == '.' || next.isDecimalDigit()) {
            parseDecimal()
        } else {
            parseNumLiteral()
        }

        return if (negate && value is Token.FloatingPoint) {
            Token.FloatingPoint(-value.number)
        } else if (negate && value is Token.UnsignedInteger) {
            if (value.number > Long.MIN_VALUE.toULong()) {
                throw OverflowError(pos)
            } else {
                Token.SignedInteger(-value.number.toLong())
            }
        } else if (value is Token.UnsignedInteger && value.number <= Long.MAX_VALUE.toULong()) {
            Token.SignedInteger(value.number.toLong())
        } else {
            value
        }
    }

    private fun parseZeroPrefixedNumber(): Token.Num {
        check(reader.peek() == '0')
        val pos = reader.pos
        reader.consume()
        return when (reader.peekOrNull()) {
            'x', 'X' -> parseHexNumber(pos)
            '.' -> parseDecimal()
            else -> Token.UnsignedInteger(0u)
        }
    }

    private fun parseNumLiteral(): Token.Num {
        val pos = reader.pos
        val letters = reader.consumeWhile(Char::isLetter)
        return numLiteralOf(letters) ?: throw LiteralError(letters, pos)
    }

    private fun parseHexNumber(startPos: ReaderPosition): Token.Num {
        reader.consume()
        val hexString = reader.readHexString()
        if (hexString.isEmpty()) {
            reader.throwTokenError()
        }

        val number = try {
            hexString.toULong(HEX_BASE)
        } catch (e: NumberFormatException) {
            throw OverflowError(startPos)
        }

        return Token.UnsignedInteger(number)
    }

    private fun parseExponent(): Int? = if (reader.peekOrNull()?.lowercaseChar() == 'e') {
        reader.consume()

        val factor = when (reader.readOptionalSign()) {
            Sign.PLUS -> +1
            Sign.MINUS -> -1
        }

        val str = reader.consumeWhile(Char::isDecimalDigit)
        if (str.isEmpty()) {
            reader.throwTokenError()
        }

        factor * str.toInt()
    } else {
        null
    }

    private fun parseDecimal(): Token.Num {
        val builder = StringBuilder()

        builder.appendWhile(reader, Char::isDecimalDigit)
        val hasDecimalPoint = reader.peekOrNull() == '.'

        if (hasDecimalPoint) {
            builder.append(reader.consume())
            builder.appendWhile(reader, Char::isDecimalDigit)
        }

        val token = builder.toString()

        if (token.isEmpty() || token == ".") {
            reader.throwTokenError()
        }

        val exp = parseExponent()

        return if (hasDecimalPoint || exp != null) {
            val factor = exp?.let { EXPONENTIAL_BASE.pow(it) } ?: 1.0
            Token.FloatingPoint(factor * token.toDouble())
        } else {
            Token.UnsignedInteger(token.toULong())
        }
    }
}

private class StringParser(private val reader: FormatReader) {
    fun parseQuotedString(): String {
        val quote = reader.consume()
        val builder = StringBuilder()
        while (!reader.done) {
            when (reader.peek()) {
                '\\' -> handleStringBackslash(builder)
                quote -> return builder.toString().also { reader.consume() }
                in Specification.UNESCAPED_STRING_CHARS -> builder.append(reader.consume())
                in Specification.LINE_TERMINATORS -> break
                else -> builder.append(reader.consume())
            }
        }

        reader.throwTokenError()
    }

    fun parseLiteral(): String {
        if (!Specification.startsIdentifier(reader.peek()) && reader.peek() != '\\') {
            reader.throwTokenError()
        }

        val builder = StringBuilder()
        while (true) {
            val char = reader.peekOrNull()
            if (char == '\\') {
                reader.consume()
                if (reader.peekOrNull() != 'u') {
                    reader.throwTokenError()
                }

                reader.consume()
                builder.append(readHexSequence(U_HEX_DIGITS))
            } else if (char != null && Specification.isIdentifierPart(char)) {
                builder.append(reader.consume())
            } else {
                break
            }
        }

        return builder.toString()
    }

    private fun handleStringBackslash(sink: Appendable) {
        assert(!reader.done)
        reader.consume()
        if (reader.done) {
            reader.throwTokenError()
        }

        when (val char = reader.peek()) {
            in Specification.LINE_TERMINATORS ->
                reader.consume()

            in Specification.SINGLE_ESCAPE_CHARS ->
                sink.append(Specification.SINGLE_ESCAPE_CHARS[reader.consume()]!!)

            in '1'..'9' ->
                reader.throwTokenError()

            '0' ->
                sink.append(0.toChar()).also { reader.consume() }

            'x', 'u' -> {
                reader.consume()
                sink.append(readHexSequence(if (char == 'u') U_HEX_DIGITS else X_HEX_DIGITS))
            }

            else ->
                sink.append(reader.consume())
        }
    }

    private fun readHexSequence(width: Int): Char {
        val builder = StringBuilder()
        repeat(width) {
            if (reader.done || !reader.peek().isHexDigit()) {
                reader.throwTokenError()
            }

            builder.append(reader.consume())
        }

        return builder.toString().toInt(HEX_BASE).toChar()
    }
}

private fun StringBuilder.appendWhile(reader: InputReader, predicate: (Char) -> Boolean) {
    while (true) {
        val next = reader.peekOrNull()

        if (next != null && predicate(next)) {
            append(reader.consume())
        } else {
            break
        }
    }
}

private fun numLiteralOf(letters: String): Token.Num? = when (letters) {
    "Infinity" -> Token.FloatingPoint(Double.POSITIVE_INFINITY)
    "NaN" -> Token.FloatingPoint(Double.NaN)
    else -> null
}

private fun miscLiteralOf(letters: String): Token.Value? = when (letters) {
    "true" -> Token.Bool(true)
    "false" -> Token.Bool(false)
    "null" -> Token.Null
    else -> null
}

private const val EXPONENTIAL_BASE = 10.0
private const val HEX_BASE = 16

private const val U_HEX_DIGITS = 4
private const val X_HEX_DIGITS = 2

private enum class Sign { PLUS, MINUS }

private fun InputReader.readHexString(): String = consumeWhile(Char::isHexDigit)

package io.github.xn32.json5k.parsing

import io.github.xn32.json5k.LiteralError
import io.github.xn32.json5k.format.DocumentTracker
import io.github.xn32.json5k.format.Specification
import io.github.xn32.json5k.format.Token
import io.github.xn32.json5k.isDecimalDigit
import io.github.xn32.json5k.isHexDigit
import io.github.xn32.json5k.throwTokenError

internal data class Event<out T>(
    val pos: LinePosition,
    val item: T,
)

internal interface Parser<out T> {
    fun next(): Event<T>
}

internal interface LookaheadParser<out T> : Parser<T> {
    fun peek(): Event<T>
}

internal class InjectableLookaheadParser<T>(private val parser: Parser<T>) : LookaheadParser<T> {
    private val buffer: MutableList<Event<T>> = mutableListOf()

    fun prepend(event: Event<T>) = buffer.add(0, event)
    fun prepend(events: List<Event<T>>) = buffer.addAll(0, events)

    override fun next(): Event<T> = buffer.removeFirstOrNull() ?: parser.next()
    override fun peek(): Event<T> = buffer.firstOrNull() ?: parser.next().also { buffer.add(it) }
}

internal class FormatParser(streamReader: InputSource) : Parser<Token> {
    private val reader: FormatReader = FormatReader(streamReader)

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
        if (tracker.nextTokenType == DocumentTracker.TokenType.COMMA && reader.peekOrNull() == ',') {
            reader.consume()
            tracker.supplyComma()
            reader.advance()
        }
    }

    private fun nextPrimaryToken(): Token {
        val next = reader.peekOrNull()
        val structCloserPending = (next == '}' || next == ']')

        return when (tracker.nextTokenType) {
            DocumentTracker.TokenType.MEMBER_VALUE -> parseValue()

            DocumentTracker.TokenType.NEXT_ITEM -> {
                if (tracker.inStruct && structCloserPending) {
                    parseStructCloser()
                } else if (tracker.inObjectStruct) {
                    parseMemberName()
                } else {
                    parseValue()
                }
            }

            DocumentTracker.TokenType.COMMA -> {
                if (structCloserPending) {
                    parseStructCloser()
                } else {
                    reader.throwTokenError()
                }
            }

            DocumentTracker.TokenType.END_OF_FILE -> {
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
                else -> parseIdentifierName()
            }
        )

        reader.advance()
        if (reader.done || reader.peek() != ':') {
            reader.throwTokenError()
        }

        reader.consume()
        return memberName
    }

    private fun parseIdentifierName(): String {
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
                builder.append(reader.readHexSequence(U_HEX_DIGITS))
            } else if (char != null && Specification.isIdentifierPart(char)) {
                builder.append(reader.consume())
            } else {
                break
            }
        }

        return builder.toString()
    }

    private fun parseValue(): Token {
        return when (reader.peekOrNull()) {
            '"', '\'' -> Token.Str(stringParser.parseQuotedString())
            in 'a'..'z', in 'A'..'Z' -> parseLiteral()
            in '0'..'9', '+', '-', '.' -> numParser.parseNumber()

            '{' -> {
                reader.consume()
                Token.BeginObject
            }

            '[' -> {
                reader.consume()
                Token.BeginArray
            }

            else -> reader.throwTokenError()
        }
    }

    private fun parseLiteral(): Token.Value {
        val pos = reader.pos
        val literal = reader.consumeWhile(Char::isLetter)
        return if (isNumLiteral(literal)) {
            Token.Num(literal)
        } else {
            miscLiteralOf(literal) ?: throw LiteralError(literal, pos)
        }
    }

    private fun parseStructCloser(): Token {
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

private class NumParser(private val reader: FormatReader) {
    fun parseNumber(): Token.Num {
        val builder = StringBuilder()
        parseOptionalSign(builder)

        val next = reader.peekOrNull() ?: reader.throwTokenError()
        if (next == '0') {
            parseZeroPrefixedNumber(builder)
        } else if (next == '.' || next.isDecimalDigit()) {
            parseDecimal(builder)
        } else {
            parseNumLiteral(builder)
        }

        return Token.Num(builder.toString())
    }

    private fun parseOptionalSign(builder: StringBuilder) {
        val peek = reader.peekOrNull()
        if (peek == '+') {
            reader.consume()
        } else if (peek == '-') {
            builder.append(reader.consume())
        }
    }

    private fun parseZeroPrefixedNumber(builder: StringBuilder) {
        builder.append(reader.consume())
        when (reader.peekOrNull()) {
            'x', 'X' -> parseHexNumber(builder)
            '.' -> parseDecimal(builder)
        }
    }

    private fun parseNumLiteral(builder: StringBuilder) {
        val pos = reader.pos
        val literal = reader.consumeWhile(Char::isLetter)
        if (isNumLiteral(literal)) {
            builder.append(literal)
        } else {
            throw LiteralError(literal, pos)
        }
    }

    private fun parseHexNumber(builder: StringBuilder) {
        builder.append(reader.consume().lowercase())
        if (builder.appendWhile(reader, Char::isHexDigit) == 0) {
            reader.throwTokenError()
        }
    }

    private fun parseDecimal(builder: StringBuilder) {
        val preCount = builder.appendWhile(reader, Char::isDecimalDigit)
        val postCount = if (reader.peekOrNull() == '.') {
            builder.append(reader.consume())
            builder.appendWhile(reader, Char::isDecimalDigit)
        } else {
            0u
        }

        if (preCount == 0 && postCount == 0) {
            reader.throwTokenError()
        }

        if (reader.peekOrNull() == 'e' || reader.peekOrNull() == 'E') {
            builder.append(reader.consume())
            parseOptionalSign(builder)
            val count = builder.appendWhile(reader, Char::isDecimalDigit)
            if (count == 0) {
                reader.throwTokenError()
            }
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

    private fun handleStringBackslash(sink: Appendable) {
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
                sink.append(reader.readHexSequence(if (char == 'u') U_HEX_DIGITS else X_HEX_DIGITS))
            }
            else ->
                sink.append(reader.consume())
        }
    }
}

private fun SourceReader.readHexSequence(width: Int): Char {
    val builder = StringBuilder()
    repeat(width) {
        if (done || !peek().isHexDigit()) {
            throwTokenError()
        }

        builder.append(consume())
    }

    return builder.toString().toInt(HEX_BASE).toChar()
}

private fun StringBuilder.appendWhile(reader: SourceReader, predicate: (Char) -> Boolean): Int {
    var counter = 0
    while (true) {
        val next = reader.peekOrNull()
        if (next != null && predicate(next)) {
            append(reader.consume())
            counter++
        } else {
            return counter
        }
    }
}

private fun isNumLiteral(letters: String): Boolean = when (letters) {
    "Infinity", "NaN" -> true
    else -> false
}

private fun miscLiteralOf(letters: String): Token.Value? = when (letters) {
    "true" -> Token.Bool(true)
    "false" -> Token.Bool(false)
    "null" -> Token.Null
    else -> null
}

private const val HEX_BASE = 16
private const val U_HEX_DIGITS = 4
private const val X_HEX_DIGITS = 2

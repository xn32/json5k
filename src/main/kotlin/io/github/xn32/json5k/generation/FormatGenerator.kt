package io.github.xn32.json5k.generation

import io.github.xn32.json5k.config.OutputStrategy
import io.github.xn32.json5k.format.DocumentTracker
import io.github.xn32.json5k.format.DocumentTracker.TokenType
import io.github.xn32.json5k.format.Specification
import io.github.xn32.json5k.format.Token
import java.io.BufferedWriter
import java.io.Flushable
import java.io.OutputStream
import java.io.Writer

private const val INDENT_CHAR = ' '

internal class FormatGenerator(stream: OutputStream, private val outputStrategy: OutputStrategy) : Flushable {
    private val writer: BufferedWriter = stream.bufferedWriter(Charsets.UTF_8)
    private val tracker = DocumentTracker()

    fun put(token: Token) {
        handleToken(token)
        tracker.supply(token)
    }

    private fun writeQuoted(sequence: CharSequence) {
        writer.appendQuoted(outputStrategy.quoteCharacter, sequence)
    }

    private fun writeVisualSep(levelOffset: Int = 0) {
        if (outputStrategy !is OutputStrategy.HumanReadable) {
            return
        }

        writer.newLine()
        repeat(outputStrategy.indentationWith * (tracker.nestingLevel + levelOffset)) {
            writer.write(INDENT_CHAR.code)
        }
    }

    private fun handleToken(token: Token) {
        if (token is Token.EndToken) {
            if (tracker.nextTokenType == TokenType.COMMA) {
                writeVisualSep(-1)
            }

            handleEndToken(token)
            return
        }

        if (tracker.nextTokenType == TokenType.NEXT_ITEM && tracker.nestingLevel > 0) {
            writeVisualSep()
        }

        if (tracker.nextTokenType == TokenType.COMMA) {
            writer.append(',')
            tracker.supplyComma()

            if (token is Token.BeginToken && outputStrategy is OutputStrategy.HumanReadable) {
                writer.append(' ')
            } else if (token !is Token.BeginToken) {
                writeVisualSep()
            }
        }

        when (tracker.nextTokenType) {
            TokenType.END_OF_FILE -> {
                require(token is Token.EndOfFile)
                writer.flush()
            }

            TokenType.NEXT_ITEM, TokenType.COMMA -> {
                if (tracker.inObjectStruct) {
                    require(token is Token.MemberName)
                    handleMemberName(token)
                } else {
                    handleNextItem(token)
                }
            }

            TokenType.MEMBER_VALUE -> {
                handleNextItem(token)
            }
        }
    }

    private fun handleEndToken(token: Token.EndToken) {
        when (token) {
            Token.EndObject -> writer.append('}')
            Token.EndArray -> writer.append(']')
        }
    }

    private fun handleBeginToken(token: Token.BeginToken) {
        when (token) {
            Token.BeginObject -> writer.append('{')
            Token.BeginArray -> writer.append('[')
        }
    }

    private fun handleMemberName(token: Token.MemberName) {
        val name = token.name

        if (!Specification.isIdentifier(name) || outputStrategy.quoteMemberNames) {
            writeQuoted(name)
        } else {
            writer.append(token.name)
        }

        writer.append(':')
        if (outputStrategy is OutputStrategy.HumanReadable) {
            writer.append(' ')
        }
    }

    private fun handleNextItem(token: Token) {
        when (token) {
            is Token.BeginToken -> handleBeginToken(token)
            is Token.Value -> putValue(token)
            else -> throw IllegalArgumentException("unexpected token type")
        }
    }

    private fun putValue(token: Token.Value) {
        when (token) {
            is Token.Bool -> writer.append(token.bool.toString())
            Token.Null -> writer.append("null")
            is Token.FloatingPoint -> writer.append(token.number.toString())
            is Token.SignedInteger -> writer.append(token.number.toString())
            is Token.UnsignedInteger -> writer.append(token.number.toString())
            is Token.Str -> writeQuoted(token.string)
        }
    }

    override fun flush() {
        writer.flush()
    }
}

private fun Writer.appendEscaped(char: Char) {
    append("\\$char")
}

private fun Writer.appendQuoted(quoteChar: Char, sequence: CharSequence) {
    append(quoteChar)
    for (char in sequence) {
        when (char) {
            quoteChar, '\\' -> appendEscaped(char)
            in Specification.LINE_TERMINATORS -> {
                when (val ctrl = Specification.REVERSE_ESCAPE_CHAR_MAP[char]) {
                    is Char -> appendEscaped(ctrl)
                    else -> append("\\u${"%04x".format(char.code)}")
                }
            }

            else -> append(char)
        }
    }

    append(quoteChar)
}

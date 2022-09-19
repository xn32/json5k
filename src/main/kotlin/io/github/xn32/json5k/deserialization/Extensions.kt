package io.github.xn32.json5k.deserialization

import io.github.xn32.json5k.UnexpectedValueError
import io.github.xn32.json5k.format.Token
import io.github.xn32.json5k.parsing.Event
import io.github.xn32.json5k.parsing.ReaderPosition

internal inline fun <reified T : Token.Value> Event<Token>.mapType(): Event<T> = Event(pos, item.getAsType(pos))
internal inline fun <reified T : Token.Value> Event<Token>.extractType(): T = item.getAsType(pos)

private inline fun <reified T : Token.Value> Token.getAsType(errorPosition: ReaderPosition): T {
    if (this !is T) {
        val descriptor = when (T::class) {
            Token.Num::class -> "numeric value"
            Token.Integer::class -> "integer"
            Token.SignedInteger::class -> "signed integer"
            Token.UnsignedInteger::class -> "unsigned integer"
            Token.FloatingPoint::class -> "floating point number"
            Token.Bool::class -> "boolean value"
            Token.Str::class -> "string"
            Token.Null::class -> "null value"
            else -> null
        }

        if (descriptor != null) {
            throw UnexpectedValueError("$descriptor expected", errorPosition)
        } else {
            throw UnexpectedValueError("unexpected value", errorPosition)
        }
    }

    return this
}
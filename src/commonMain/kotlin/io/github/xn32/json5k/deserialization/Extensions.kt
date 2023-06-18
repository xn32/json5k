package io.github.xn32.json5k.deserialization

import io.github.xn32.json5k.UnexpectedValueError
import io.github.xn32.json5k.format.Token
import io.github.xn32.json5k.parsing.Event
import io.github.xn32.json5k.parsing.LinePosition

internal inline fun <reified T : Token.Value> Event<Token>.mapType(): Event<T> = Event(pos, item.getAsType(pos))
internal inline fun <reified T : Token.Value> Event<Token>.extractType(): T = item.getAsType(pos)

private inline fun <reified T : Token.Value> Token.getAsType(errorPos: LinePosition): T {
    if (this !is T) {
        val descriptor = when (T::class) {
            Token.Num::class -> "number"
            Token.Bool::class -> "boolean value"
            Token.Str::class -> "string literal"
            else -> null
        }

        throw if (descriptor != null) {
            UnexpectedValueError("$descriptor expected", errorPos)
        } else {
            UnexpectedValueError("unexpected value", errorPos)
        }
    }

    return this
}

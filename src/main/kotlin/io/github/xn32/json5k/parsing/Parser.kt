package io.github.xn32.json5k.parsing

internal interface Parser<out T> {
    fun next(): Event<T>
}

internal data class Event<out T>(
    val pos: ReaderPosition,
    val item: T,
)

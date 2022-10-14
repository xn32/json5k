package io.github.xn32.json5k.parsing

internal interface Parser<out T> {
    fun next(): Event<T>
}

internal interface LookaheadParser<out T> : Parser<T> {
    fun peek(): Event<T>
}

internal data class Event<out T>(
    val pos: ReaderPosition,
    val item: T,
)

internal class InjectableLookaheadParser<T>(private val parser: Parser<T>) : LookaheadParser<T> {
    private val buffer: MutableList<Event<T>> = mutableListOf()

    fun inject(event: Event<T>) = buffer.add(0, event)
    fun inject(events: List<Event<T>>) = buffer.addAll(0, events)

    override fun next(): Event<T> = buffer.removeFirstOrNull() ?: parser.next()
    override fun peek(): Event<T> = buffer.firstOrNull() ?: parser.next().also { buffer.add(it) }
}

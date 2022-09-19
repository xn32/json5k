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

internal class PlainLookaheadParser<out T>(private val parser: Parser<T>) : LookaheadParser<T> {
    private var next: Event<T>? = null

    private fun clearing(): Event<T>? = next.also { next = null }
    private fun setting(event: Event<T>): Event<T> = event.also { next = it }

    override fun next(): Event<T> = clearing() ?: parser.next()
    override fun peek(): Event<T> = next ?: setting(parser.next())
}

internal class InjectableLookaheadParser<T>(parser: Parser<T>) : LookaheadParser<T> {
    private val eventBuffer: ArrayDeque<Event<T>> = ArrayDeque()
    private val delegate: LookaheadParser<T> = PlainLookaheadParser(parser)

    fun inject(event: Event<T>) {
        eventBuffer.add(event)
    }

    override fun peek(): Event<T> = if (eventBuffer.isNotEmpty()) {
        eventBuffer.first()
    } else {
        delegate.peek()
    }

    override fun next(): Event<T> = if (eventBuffer.isNotEmpty()) {
        eventBuffer.removeFirst()
    } else {
        delegate.next()
    }
}

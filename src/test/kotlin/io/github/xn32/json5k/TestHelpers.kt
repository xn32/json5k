package io.github.xn32.json5k

import io.github.xn32.json5k.parsing.InputReader
import io.github.xn32.json5k.parsing.ReaderPosition
import kotlin.test.assertEquals

internal fun InputReader.checkPosition(line: Int, column: Int? = null) = pos.check(line, column)
internal fun ReaderPosition.check(line: Int, column: Int? = null) {
    assertEquals(line, this.line.toInt())
    if (column != null) {
        assertEquals(column, this.column.toInt())
    }
}

internal fun PositionProvider.checkPosition(line: Int, column: Int) {
    assertEquals(line, this.line.toInt())
    assertEquals(column, this.column.toInt())
}

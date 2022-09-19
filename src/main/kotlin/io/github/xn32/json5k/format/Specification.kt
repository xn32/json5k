package io.github.xn32.json5k.format

private fun <T> charMapOf(vararg pairs: Pair<T, Int>): Map<T, Char> = pairs.toMap().mapValues { it.value.toChar() }
private fun charSetOf(vararg values: Int): Set<Char> = values.map(Int::toChar).toSet()
private fun <K, V> Map<K, V>.swap(): Map<V, K> = entries.associate { it.value to it.key }

@Suppress("MagicNumber")
internal object Specification {
    // Specification: https://262.ecma-international.org/5.1/#sec-7.8.4
    private val SINGLE_ESCAPE_CHARS = charMapOf(
        '\'' to 0x27, '"' to 0x22, '\\' to 0x5c,
        'b' to 0x08, 'f' to 0x0c, 'n' to 0x0a,
        'r' to 0x0d, 't' to 0x09, 'v' to 0x0b,
    )

    // Reverse helper for SINGLE_ESCAPE_CHARS:
    val REVERSE_ESCAPE_CHAR_MAP = SINGLE_ESCAPE_CHARS.filterKeys(Char::isLetter).swap()

    // Specification: https://262.ecma-international.org/5.1/#sec-7.2
    val WHITESPACE_CHARS = charSetOf(
        // TAB, VT, FF, SP, NBSP, and BOM
        0x09, 0x0b, 0x0c, 0x20, 0xa0, 0xfeff,
        // USP characters (remainder)
        0x1680, 0x2001, 0x2002, 0x2003, 0x2004, 0x2005, 0x2006,
        0x2007, 0x2008, 0x2009, 0x200a, 0x202f, 0x205f, 0x3000,
    )

    // Specification: https://262.ecma-international.org/5.1/#sec-7.3
    val LINE_TERMINATORS = charSetOf(0x000a, 0x000d, 0x2028, 0x2029)
}

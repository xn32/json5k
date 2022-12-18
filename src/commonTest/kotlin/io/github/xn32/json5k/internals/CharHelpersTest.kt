package io.github.xn32.json5k.internals

import io.github.xn32.json5k.isDecimalDigit
import io.github.xn32.json5k.isHexDigit
import io.github.xn32.json5k.isUnicodeLetter
import io.github.xn32.json5k.isUnicodeOther
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val UNICODE_LETTERS = listOf('a', 'ä', 'ἀ', 'A', 'Æ', 'ℙ', '\u1ffc', '\u02b9', '\u05d7', '\u2169')
private val UNICODE_OTHERS = listOf('\u0000', '\n', '\u009e', '\u00ad')

private val DECIMAL_DIGITS = '0'..'9'
private val HEX_DIGITS = DECIMAL_DIGITS + ('A'..'F') + ('a'..'f')

private val OTHER_CHARS = listOf('-', ']', '«', '»', '!', ':', '$', '=', '\u00a9', '\u203f', '\u2011')

private fun checkFunc(func: ((Char) -> Boolean), positives: Iterable<Char>, negatives: Iterable<Char>) {
    positives.map(func).forEach(::assertTrue)
    negatives.map(func).forEach(::assertFalse)
}

class CharHelpersTest {
    @Test
    fun isUnicodeLetter() {
        checkFunc(Char::isUnicodeLetter, UNICODE_LETTERS, UNICODE_OTHERS + DECIMAL_DIGITS + OTHER_CHARS)
    }

    @Test
    fun isUnicodeOther() {
        checkFunc(Char::isUnicodeOther, UNICODE_OTHERS, UNICODE_LETTERS + DECIMAL_DIGITS + OTHER_CHARS)
    }

    @Test
    fun isDecimalDigit() {
        checkFunc(Char::isDecimalDigit, DECIMAL_DIGITS, UNICODE_LETTERS + UNICODE_OTHERS + OTHER_CHARS)
    }

    @Test
    fun isHexDigit() {
        checkFunc(Char::isHexDigit, HEX_DIGITS, UNICODE_OTHERS + OTHER_CHARS)
    }
}

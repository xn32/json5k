package io.github.xn32.json5k

internal fun Char.isUnicodeLetter(): Boolean = this.category in UNICODE_LETTER_CATEGORIES
internal fun Char.isUnicodeOther(): Boolean = this.category in UNICODE_OTHER_CATEGORIES
internal fun Char.isHexDigit(): Boolean = this in DECIMAL_DIGITS || this.lowercaseChar() in HEX_LETTERS
internal fun Char.isDecimalDigit(): Boolean = this in DECIMAL_DIGITS

internal fun Char.toHexString(): String = code.toString(16).padStart(4, '0').uppercase()

private val DECIMAL_DIGITS = '0'..'9'
private val HEX_LETTERS = 'a'..'f'

private val UNICODE_LETTER_CATEGORIES = setOf(
    CharCategory.UPPERCASE_LETTER,
    CharCategory.LOWERCASE_LETTER,
    CharCategory.TITLECASE_LETTER,
    CharCategory.MODIFIER_LETTER,
    CharCategory.OTHER_LETTER,
    CharCategory.LETTER_NUMBER,
)

private val UNICODE_OTHER_CATEGORIES = setOf(
    CharCategory.CONTROL,
    CharCategory.FORMAT,
    CharCategory.PRIVATE_USE,
    CharCategory.SURROGATE,
    CharCategory.UNASSIGNED,
)

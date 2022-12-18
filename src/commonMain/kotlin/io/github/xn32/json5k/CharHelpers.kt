package io.github.xn32.json5k

internal fun Char.isUnicodeLetter(): Boolean = this.category in unicodeLetterCategories
internal fun Char.isUnicodeOther(): Boolean = this.category in unicodeOtherCategories
internal fun Char.isHexDigit(): Boolean = this in decimalDigits || this.lowercaseChar() in hexLetters
internal fun Char.isDecimalDigit(): Boolean = this in decimalDigits

internal fun Char.toHexString(): String = code.toString(HEX_BASE).padStart(HEX_STRING_WIDTH, '0').uppercase()

private const val HEX_BASE = 16
private const val HEX_STRING_WIDTH = 4

private val decimalDigits = '0'..'9'
private val hexLetters = 'a'..'f'

private val unicodeLetterCategories = setOf(
    CharCategory.UPPERCASE_LETTER,
    CharCategory.LOWERCASE_LETTER,
    CharCategory.TITLECASE_LETTER,
    CharCategory.MODIFIER_LETTER,
    CharCategory.OTHER_LETTER,
    CharCategory.LETTER_NUMBER,
)

private val unicodeOtherCategories = setOf(
    CharCategory.CONTROL,
    CharCategory.FORMAT,
    CharCategory.PRIVATE_USE,
    CharCategory.SURROGATE,
    CharCategory.UNASSIGNED,
)

package io.github.xn32.json5k.generation

internal actual fun Double.toScientificString(): String {
    return toBigDecimal().stripTrailingZeros().toString().lowercase()
}

internal actual fun Double.toPlainString(): String {
    return toBigDecimal().toPlainString()
}

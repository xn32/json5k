package io.github.xn32.json5k.generation

internal actual fun Double.toScientificString(): String {
    return toBigDecimal().stripTrailingZeros().toString().replace('E', 'e')
}

internal actual fun Double.toPlainString(): String {
    val stripped = toBigDecimal().stripTrailingZeros()

    val scaled = if (stripped.scale() == 0) {
        stripped.setScale(1)
    } else {
        stripped
    }

    return scaled.toPlainString()
}

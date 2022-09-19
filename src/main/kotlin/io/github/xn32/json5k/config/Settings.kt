package io.github.xn32.json5k.config

internal data class Settings(
    val failOnDuplicateKeys: Boolean,
    val classDiscriminator: String,
    val encodeDefaults: Boolean,
    val outputStrategy: OutputStrategy,
)

internal sealed interface OutputStrategy {
    object Compressed : OutputStrategy {
        override val quoteChar: Char = DOUBLE_QUOTE
        override val quoteMemberNames = false
    }

    data class HumanReadable(
        val indentationWith: Int,
        override val quoteChar: Char,
        override val quoteMemberNames: Boolean,
    ) : OutputStrategy

    val quoteChar: Char
    val quoteMemberNames: Boolean
}

internal fun ConfigBuilder.toSettings(): Settings = Settings(
    failOnDuplicateKeys = failOnDuplicateKeys,
    classDiscriminator = classDiscriminator,
    encodeDefaults = encodeDefaults,
    outputStrategy = if (prettyPrint) {
        require(indentationWidth > 0)
        OutputStrategy.HumanReadable(
            indentationWith = indentationWidth,
            quoteChar = if (useSingleQuotes) SINGLE_QUOTE else DOUBLE_QUOTE,
            quoteMemberNames = quoteMemberNames
        )
    } else {
        OutputStrategy.Compressed
    },
)

private const val SINGLE_QUOTE = '\''
private const val DOUBLE_QUOTE = '"'

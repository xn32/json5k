package io.github.xn32.json5k

import kotlinx.serialization.modules.SerializersModule

/**
 * Type-safe builder to configure the parameters of a custom [Json5] instance.
 */
@Suppress("MagicNumber")
class ConfigBuilder {
    /**
     * Supply a custom [SerializersModule] that will be passed to the kotlinx.serialization framework.
     */
    var serializersModule: SerializersModule? = null

    /**
     * Class discriminator to use for the serialization and deserialization of polymorphic types without
     * a [ClassDiscriminator] annotation.
     */
    var classDiscriminator: String = "type"

    /**
     * Encode default values from the data model when the object hierarchy to serialize does not overwrite them.
     */
    var encodeDefaults: Boolean = false

    /**
     * Request the generation of human-readable output (spanning multiple lines). Without this option,
     * compressed single-line output is generated. [SerialComment] annotations are ignored without this setting.
     */
    var prettyPrint: Boolean = false

    /**
     * Number of spaces to use for indenting each hierarchy level (objects/arrays) in generated JSON5 output.
     * This setting does only have an effect if the [prettyPrint] option is activated.
     */
    var indentationWidth: Int = 4

    /**
     * Request names of serialized object members to be quoted even if the quotation marks could be dropped.
     * This setting does only have an effect if the [prettyPrint] option is activated. If compressed single-line
     * output is generated, member names will be quoted only when necessary (regardless of this option).
     */
    var quoteMemberNames: Boolean = false

    /**
     * Use single quotes instead of double quotes when serializing strings and member names in human-readable
     * output. This setting does only have an effect if the [prettyPrint] option is activated. If compressed
     * single-line output is generated, double quotes will be used (regardless of this option).
     */
    var useSingleQuotes: Boolean = false
}

internal data class Settings(
    val classDiscriminator: String,
    val encodeDefaults: Boolean,
    val outputStrategy: OutputStrategy,
)

internal sealed interface OutputStrategy {
    object Compressed : OutputStrategy {
        override val quoteCharacter: Char = DOUBLE_QUOTE
        override val quoteMemberNames = false
    }

    data class HumanReadable(
        val indentationWith: Int,
        override val quoteCharacter: Char,
        override val quoteMemberNames: Boolean,
    ) : OutputStrategy

    val quoteCharacter: Char
    val quoteMemberNames: Boolean
}

internal fun ConfigBuilder.toSettings(): Settings = Settings(
    classDiscriminator = classDiscriminator,
    encodeDefaults = encodeDefaults,
    outputStrategy = if (prettyPrint) {
        require(indentationWidth > 0)
        OutputStrategy.HumanReadable(
            indentationWith = indentationWidth,
            quoteCharacter = if (useSingleQuotes) SINGLE_QUOTE else DOUBLE_QUOTE,
            quoteMemberNames = quoteMemberNames
        )
    } else {
        OutputStrategy.Compressed
    },
)

private const val SINGLE_QUOTE = '\''
private const val DOUBLE_QUOTE = '"'

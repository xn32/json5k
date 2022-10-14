package io.github.xn32.json5k.config

import kotlinx.serialization.modules.SerializersModule

@Suppress("MagicNumber")
class ConfigBuilder {
    var serializersModule: SerializersModule? = null

    var classDiscriminator: String = "type"
    var encodeDefaults: Boolean = false

    var prettyPrint: Boolean = false
    var indentationWidth: Int = 4
    var quoteMemberNames: Boolean = false
    var useSingleQuotes: Boolean = false
}

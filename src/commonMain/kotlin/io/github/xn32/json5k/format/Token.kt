package io.github.xn32.json5k.format

import kotlin.jvm.JvmInline

internal sealed interface Token {
    object EndOfFile : Token

    sealed interface StructToken : Token
    sealed interface BeginToken : StructToken
    sealed interface EndToken : StructToken

    object BeginArray : BeginToken
    object EndArray : EndToken
    object BeginObject : BeginToken
    object EndObject : EndToken

    @JvmInline
    value class MemberName(val name: String) : Token

    sealed interface Value : Token
    sealed interface Num : Value
    sealed interface Integer : Num

    @JvmInline
    value class SignedInteger(val number: Long) : Integer

    @JvmInline
    value class UnsignedInteger(val number: ULong) : Integer

    @JvmInline
    value class FloatingPoint(val number: Double) : Num

    @JvmInline
    value class Str(val string: String) : Value

    @JvmInline
    value class Bool(val bool: Boolean) : Value

    object Null : Value
}

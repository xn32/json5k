package io.github.xn32.json5k.format

internal class DocumentTracker {
    private enum class StructType { OBJECT, ARRAY }
    enum class TokenType { NEXT_ITEM, MEMBER_VALUE, COMMA, END_OF_FILE }

    private val stack: ArrayDeque<StructType> = ArrayDeque()

    var nextTokenType: TokenType = TokenType.NEXT_ITEM
        private set

    val inObjectStruct: Boolean
        get() = stack.lastOrNull() == StructType.OBJECT
    val inArrayStruct: Boolean
        get() = stack.lastOrNull() == StructType.ARRAY
    val inStruct: Boolean
        get() = stack.isNotEmpty()
    val nestingLevel: Int
        get() = stack.size

    fun supply(token: Token) {
        when (token) {
            Token.BeginArray -> stack.add(StructType.ARRAY)
            Token.BeginObject -> stack.add(StructType.OBJECT)
            Token.EndArray, Token.EndObject -> endStruct(token)
            else -> {}
        }

        nextTokenType = if (token == Token.BeginArray || token == Token.BeginObject) {
            TokenType.NEXT_ITEM
        } else if (token is Token.MemberName) {
            TokenType.MEMBER_VALUE
        } else if (!inStruct) {
            TokenType.END_OF_FILE
        } else {
            TokenType.COMMA
        }
    }

    fun supplyComma() {
        check(nextTokenType == TokenType.COMMA)
        nextTokenType = TokenType.NEXT_ITEM
    }

    private fun endStruct(token: Token) {
        if (inObjectStruct) {
            require(token is Token.EndObject)
        } else if (inArrayStruct) {
            require(token is Token.EndArray)
        } else {
            error("no struct to close")
        }

        stack.removeLast()
    }
}

package com.emberr.domain.database

class FormulaException(message: String) : Exception(message)

enum class FormulaOperator(val symbol: String) {
    PLUS("+"),
    MINUS("-"),
    TIMES("*"),
    DIVIDE("/"),
    EQUALS("=="),
    NOT_EQUALS("!="),
    GREATER_OR_EQUAL(">="),
    LESS_OR_EQUAL("<="),
    GREATER(">"),
    LESS("<")
}

enum class FormulaFunction(val formulaName: String, val argumentCounts: IntRange) {
    IF("if", 3..3),
    AND("and", 1..Int.MAX_VALUE),
    OR("or", 1..Int.MAX_VALUE),
    NOT("not", 1..1),
    CONCAT("concat", 1..Int.MAX_VALUE),
    LENGTH("length", 1..1),
    ROUND("round", 1..2),
    ABS("abs", 1..1),
    MIN("min", 1..Int.MAX_VALUE),
    MAX("max", 1..Int.MAX_VALUE),
    EMPTY("empty", 1..1),
    NOW("now", 0..0),
    DATE_ADD("dateAdd", 3..3),
    DATE_BETWEEN("dateBetween", 3..3);

    val argumentCountText: String
        get() = when {
            argumentCounts.last == 0 -> "no values"
            argumentCounts.last == Int.MAX_VALUE -> "at least ${argumentCounts.first} ${valuesWord(argumentCounts.first)}"
            argumentCounts.first == argumentCounts.last -> "${argumentCounts.first} ${valuesWord(argumentCounts.first)}"
            else -> "${argumentCounts.first} or ${argumentCounts.last} values"
        }

    private fun valuesWord(count: Int): String = if (count == 1) "value" else "values"
}

sealed class FormulaExpression {
    data class Literal(val value: FormulaValue) : FormulaExpression()
    data class PropertyReference(val propertyName: String) : FormulaExpression()
    data class Negation(val operand: FormulaExpression) : FormulaExpression()
    data class Operation(val operator: FormulaOperator, val left: FormulaExpression, val right: FormulaExpression) : FormulaExpression()
    data class FunctionCall(val function: FormulaFunction, val arguments: List<FormulaExpression>) : FormulaExpression()
}

fun FormulaExpression.propertyNames(): List<String> = when (this) {
    is FormulaExpression.Literal -> emptyList()
    is FormulaExpression.PropertyReference -> listOf(propertyName)
    is FormulaExpression.Negation -> operand.propertyNames()
    is FormulaExpression.Operation -> left.propertyNames() + right.propertyNames()
    is FormulaExpression.FunctionCall -> arguments.flatMap { it.propertyNames() }
}

fun parseFormula(text: String): FormulaExpression {
    if (text.isBlank()) return FormulaExpression.Literal(FormulaValue.Empty)
    return FormulaParser(tokensOf(text)).parseWholeFormula()
}

fun formulaTextLiteral(text: String): String = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

private sealed class FormulaToken {
    data class NumberToken(val number: Double) : FormulaToken()
    data class TextToken(val text: String) : FormulaToken()
    data class NameToken(val name: String) : FormulaToken()
    data class SymbolToken(val symbol: String) : FormulaToken()
}

private val twoCharacterSymbols = listOf("==", "!=", ">=", "<=")
private const val SINGLE_CHARACTER_SYMBOLS = "+-*/<>(),"

private fun tokensOf(text: String): List<FormulaToken> {
    val tokens = mutableListOf<FormulaToken>()
    var index = 0
    while (index < text.length) {
        val character = text[index]
        val twoCharacterSymbol = twoCharacterSymbols.firstOrNull { text.startsWith(it, index) }
        when {
            character.isWhitespace() -> index++
            character.isDigit() || (character == '.' && text.getOrNull(index + 1)?.isDigit() == true) -> {
                val start = index
                while (index < text.length && (text[index].isDigit() || text[index] == '.')) index++
                val numberText = text.substring(start, index)
                val number = numberText.toDoubleOrNull() ?: throw FormulaException("\"$numberText\" is not a number")
                tokens += FormulaToken.NumberToken(number)
            }
            character == '"' -> {
                val quotedText = StringBuilder()
                index++
                while (true) {
                    if (index >= text.length) throw FormulaException("Text is missing its closing quote")
                    val quotedCharacter = text[index]
                    if (quotedCharacter == '"') {
                        index++
                        break
                    }
                    if (quotedCharacter == '\\' && index + 1 < text.length) {
                        quotedText.append(text[index + 1])
                        index += 2
                    } else {
                        quotedText.append(quotedCharacter)
                        index++
                    }
                }
                tokens += FormulaToken.TextToken(quotedText.toString())
            }
            character.isLetter() || character == '_' -> {
                val start = index
                while (index < text.length && (text[index].isLetterOrDigit() || text[index] == '_')) index++
                tokens += FormulaToken.NameToken(text.substring(start, index))
            }
            twoCharacterSymbol != null -> {
                tokens += FormulaToken.SymbolToken(twoCharacterSymbol)
                index += 2
            }
            character in SINGLE_CHARACTER_SYMBOLS -> {
                tokens += FormulaToken.SymbolToken(character.toString())
                index++
            }
            else -> throw FormulaException("Unexpected \"$character\"")
        }
    }
    return tokens
}

private val comparisonOperators = listOf(
    FormulaOperator.EQUALS,
    FormulaOperator.NOT_EQUALS,
    FormulaOperator.GREATER_OR_EQUAL,
    FormulaOperator.LESS_OR_EQUAL,
    FormulaOperator.GREATER,
    FormulaOperator.LESS
)
private val addingOperators = listOf(FormulaOperator.PLUS, FormulaOperator.MINUS)
private val multiplyingOperators = listOf(FormulaOperator.TIMES, FormulaOperator.DIVIDE)

private class FormulaParser(private val tokens: List<FormulaToken>) {
    private var position = 0

    fun parseWholeFormula(): FormulaExpression {
        val expression = parseComparison()
        val leftoverToken = tokens.getOrNull(position)
        if (leftoverToken != null) throw FormulaException("Unexpected ${describe(leftoverToken)}")
        return expression
    }

    private fun parseComparison(): FormulaExpression = parseOperations(comparisonOperators, ::parseAdding)

    private fun parseAdding(): FormulaExpression = parseOperations(addingOperators, ::parseMultiplying)

    private fun parseMultiplying(): FormulaExpression = parseOperations(multiplyingOperators, ::parseNegation)

    private fun parseOperations(operators: List<FormulaOperator>, parseOperand: () -> FormulaExpression): FormulaExpression {
        var expression = parseOperand()
        while (true) {
            val operator = takeOperator(operators) ?: return expression
            expression = FormulaExpression.Operation(operator, expression, parseOperand())
        }
    }

    private fun parseNegation(): FormulaExpression =
        if (takeSymbol("-")) FormulaExpression.Negation(parseNegation()) else parseSingleValue()

    private fun parseSingleValue(): FormulaExpression {
        val token = tokens.getOrNull(position) ?: throw FormulaException("The formula ends too early")
        position++
        return when (token) {
            is FormulaToken.NumberToken -> FormulaExpression.Literal(FormulaValue.NumberValue(token.number))
            is FormulaToken.TextToken -> FormulaExpression.Literal(FormulaValue.TextValue(token.text))
            is FormulaToken.NameToken -> parseName(token.name)
            is FormulaToken.SymbolToken -> {
                if (token.symbol != "(") throw FormulaException("Unexpected ${describe(token)}")
                val expressionInBrackets = parseComparison()
                expectSymbol(")")
                expressionInBrackets
            }
        }
    }

    private fun parseName(name: String): FormulaExpression {
        if (name.equals("true", ignoreCase = true)) return FormulaExpression.Literal(FormulaValue.BooleanValue(true))
        if (name.equals("false", ignoreCase = true)) return FormulaExpression.Literal(FormulaValue.BooleanValue(false))
        if (!takeSymbol("(")) throw FormulaException("Unknown word \"$name\". To use a property, write prop(\"$name\")")
        val arguments = parseArguments()

        if (name.equals("prop", ignoreCase = true)) {
            val propertyName = ((arguments.singleOrNull() as? FormulaExpression.Literal)?.value as? FormulaValue.TextValue)?.text
                ?: throw FormulaException("prop() needs one property name in quotes, like prop(\"Price\")")
            return FormulaExpression.PropertyReference(propertyName)
        }

        val function = FormulaFunction.entries.firstOrNull { it.formulaName.equals(name, ignoreCase = true) }
            ?: throw FormulaException("Unknown function \"$name\"")
        if (arguments.size !in function.argumentCounts) {
            throw FormulaException("${function.formulaName}() needs ${function.argumentCountText}")
        }
        return FormulaExpression.FunctionCall(function, arguments)
    }

    private fun parseArguments(): List<FormulaExpression> {
        if (takeSymbol(")")) return emptyList()
        val arguments = mutableListOf(parseComparison())
        while (takeSymbol(",")) arguments += parseComparison()
        expectSymbol(")")
        return arguments
    }

    private fun takeOperator(operators: List<FormulaOperator>): FormulaOperator? {
        val symbol = (tokens.getOrNull(position) as? FormulaToken.SymbolToken)?.symbol ?: return null
        val operator = operators.firstOrNull { it.symbol == symbol } ?: return null
        position++
        return operator
    }

    private fun takeSymbol(symbol: String): Boolean {
        val token = tokens.getOrNull(position)
        if (token !is FormulaToken.SymbolToken || token.symbol != symbol) return false
        position++
        return true
    }

    private fun expectSymbol(symbol: String) {
        if (!takeSymbol(symbol)) throw FormulaException("Missing \"$symbol\"")
    }

    private fun describe(token: FormulaToken): String = when (token) {
        is FormulaToken.NumberToken -> "number"
        is FormulaToken.TextToken -> "text ${formulaTextLiteral(token.text)}"
        is FormulaToken.NameToken -> "word \"${token.name}\""
        is FormulaToken.SymbolToken -> "\"${token.symbol}\""
    }
}

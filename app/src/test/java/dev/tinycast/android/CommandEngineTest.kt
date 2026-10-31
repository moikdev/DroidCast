package dev.tinycast.android

import org.junit.Assert.*
import org.junit.Test

class CommandEngineTest {
    private fun result(expression: String) = Calculator.evaluate(expression)?.result
    @Test fun arithmeticPrecedence() { assertEquals("14", result("2 + 3 * 4")); assertEquals("20", result("(2 + 3) * 4")) }
    @Test fun rightAssociativePower() { assertEquals("512", result("2^3^2")) }
    @Test fun unaryMinusPrecedence() { assertEquals("-4", result("-2^2")); assertEquals("4", result("(-2)^2")); assertEquals("0.25", result("2^-2")) }
    @Test fun percentages() { assertEquals("30", result("200 * 15%")); assertEquals("1.5", result("150%")) }
    @Test fun functions() { assertEquals("9", result("sqrt(81)")); assertEquals("3", result("log(1000)")); assertEquals("7", result("abs(-7)")) }
    @Test fun invalidMathDoesNotCrashOrProduceNan() {
        listOf("", "1/0", "sqrt(-1)", "(2+3", "2+", "1..5", "hello", "2+2xyz", "3(2)", "9^999", "unknown(5)").forEach { assertNull(it, result(it)) }
    }
    @Test fun calculatorPrefixAndUnicodeOperators() { assertEquals("42", result("= 6 × 7")); assertEquals("4", result("8 ÷ 2")) }
    @Test fun ordinaryNumbersRemainRootSearches() {
        listOf("123", "12.5", ".5", "123.", " 123 ", "(123)", "((12.5))").forEach {
            assertNotNull("Still valid in calculator mode: $it", Calculator.evaluate(it))
            assertNull("No automatic calculator: $it", Calculator.evaluateSearch(it))
        }
    }
    @Test fun explicitCalculatorModeAcceptsNumbers() {
        mapOf("=123" to "123", "= .5" to "0.5", "= (123)" to "123").forEach { (query, expected) ->
            assertEquals(expected, Calculator.evaluateSearch(query)?.result)
        }
    }
    @Test fun arithmeticOperatorsTriggerRootCalculation() {
        mapOf("12+3" to "15", "12-3" to "9", "12x3" to "36", "12X3" to "36",
            "12*3" to "36", "12×3" to "36", "12/3" to "4", "12÷3" to "4",
            "-12" to "-12", "+12" to "12", "2^3" to "8", "15%" to "0.15").forEach { (query, expected) ->
            assertEquals(query, expected, Calculator.evaluateSearch(query)?.result)
        }
    }
    @Test fun xMultiplicationRetainsPrecedenceAndFunctionNames() {
        mapOf("2+3x4" to "14", "(2+3)x4" to "20", "2x-3" to "-6", "-2x3" to "-6",
            "2x3x4" to "24", "sqrt(9)x4" to "12", "2xabs(-3)" to "6").forEach { (query, expected) ->
            assertEquals(query, expected, Calculator.evaluateSearch(query)?.result)
        }
    }
    @Test fun xInWordsAndIncompleteExpressionsRemainSearches() {
        listOf("x", "x12", "12px", "box12", "12x", "12+", "12xscreen", "12x3cm", "2+2xyz", "linux16", "max(2)").forEach {
            assertNull(it, Calculator.evaluateSearch(it))
        }
    }
    @Test fun multiplicationDisplayUsesTheRealSymbolAndKeepsResultsNumeric() {
        listOf("12x3", "12X3", "12*3", "12 × 3").forEach {
            val calculation = Calculator.evaluateSearch(it)!!
            assertEquals("12 × 3", calculation.expression)
            assertEquals("36", calculation.result)
        }
        assertEquals("= 12 × 3", Calculator.evaluateSearch("= 12 * 3")?.expression)
        assertEquals("2 × abs(-3)", Calculator.evaluateSearch("2xabs(-3)")?.expression)
    }
    @Test fun functionsConstantsAndConversionsKeepTheirIntentionalSearchBehavior() {
        listOf("sqrt(81)", "log(1000)", "abs(-7)", "pi", "e", "10 km to mi", "32 f to c").forEach {
            assertEquals(it, Calculator.evaluate(it)?.result, Calculator.evaluateSearch(it)?.result)
            assertNotNull(it, Calculator.evaluateSearch(it))
        }
    }
    @Test fun convertsLength() { assertEquals("6.2137119224 mi", result("10 km to mi")); assertEquals("30.48 cm", result("1 ft in cm")) }
    @Test fun convertsTemperature() { assertEquals("0 c", result("32 f to c")); assertEquals("212 f", result("100 c to f")); assertEquals("273.15 k", result("0 c to k")) }
    @Test fun convertsTimeAndData() { assertEquals("90 min", result("1.5 h to min")); assertEquals("1024 mib", result("1 gib to mib")) }
    @Test fun rejectsIncompatibleUnits() { assertNull(result("2 kg to km")); assertNull(result("3 foo to bar")) }
    @Test fun refusesOversizedExpression() { assertNull(result("1+".repeat(200) + "1")) }
    @Test fun localeIndependentOutput() {
        val before = java.util.Locale.getDefault()
        try { java.util.Locale.setDefault(java.util.Locale.GERMANY); assertEquals("1.5", result("3/2")) } finally { java.util.Locale.setDefault(before) }
    }
    @Test fun fuzzySubsequenceMatches() { assertTrue(CommandSearch.score("clcltr", "Calculator") >= 0); assertEquals(-1, CommandSearch.score("xyz", "Calculator")) }
    @Test fun exactAndPrefixRankAboveSubsequence() { assertTrue(CommandSearch.score("cal", "Calendar") > CommandSearch.score("cal", "Create a link")) }
    @Test fun exactAliasWins() {
        val commands = listOf(Command("a", "Calculator", "", alias = "math"), Command("b", "Math reference", ""))
        assertEquals("a", CommandSearch.find(commands, "math", emptySet(), emptyMap()).first().id)
    }
    @Test fun pinsLeadEmptyQuery() {
        val commands = listOf(Command("a", "First", ""), Command("b", "Second", ""))
        assertEquals("b", CommandSearch.find(commands, "", setOf("b"), emptyMap()).first().id)
    }
    @Test fun stableOrderingAndUsage() {
        val commands = listOf(Command("a", "First", ""), Command("b", "Second", ""))
        assertEquals(listOf("a", "b"), CommandSearch.find(commands, "", emptySet(), emptyMap()).map { it.id })
        assertEquals("b", CommandSearch.find(commands, "", emptySet(), mapOf("b" to 1)).first().id)
    }
}

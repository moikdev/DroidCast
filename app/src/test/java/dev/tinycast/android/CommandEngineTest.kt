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

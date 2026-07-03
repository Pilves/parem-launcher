package com.parem.launcher.helper

import org.junit.Assert.*
import org.junit.Test

class ExpressionEvaluatorTest {

    @Test
    fun evaluate_simpleAddition() {
        val result = ExpressionEvaluator.evaluate("2+2")
        assertEquals(4.0, result!!, 0.0)
    }

    @Test
    fun evaluate_operatorPrecedence() {
        val result = ExpressionEvaluator.evaluate("2+3*4")
        assertEquals(14.0, result!!, 0.0)
    }

    @Test
    fun evaluate_parentheses() {
        val result = ExpressionEvaluator.evaluate("(12+8)/4")
        assertEquals(5.0, result!!, 0.0)
    }

    @Test
    fun evaluate_powerRightAssociative() {
        val result = ExpressionEvaluator.evaluate("2^3^2")
        assertEquals(512.0, result!!, 0.0)
    }

    @Test
    fun evaluate_unaryMinus() {
        val result = ExpressionEvaluator.evaluate("-3+4")
        assertEquals(1.0, result!!, 0.0)
    }

    @Test
    fun evaluate_commaDecimalSeparator() {
        val result = ExpressionEvaluator.evaluate("1,5+1")
        assertEquals(2.5, result!!, 0.0)
    }

    @Test
    fun evaluate_modulo() {
        val result = ExpressionEvaluator.evaluate("10%3")
        assertEquals(1.0, result!!, 0.0)
    }

    @Test
    fun evaluate_division_withRounding() {
        val result = ExpressionEvaluator.evaluate("100/3")
        assertEquals(33.333, result!!, 0.001)
    }

    @Test
    fun evaluate_malformedExpression_doubleOperator() {
        val result = ExpressionEvaluator.evaluate("2++")
        assertNull(result)
    }

    @Test
    fun evaluate_divisionByZero_returnsNull() {
        val result = ExpressionEvaluator.evaluate("2/0")
        assertNull(result)
    }

    @Test
    fun evaluate_emptyInput_returnsNull() {
        val result = ExpressionEvaluator.evaluate("")
        assertNull(result)
    }

    @Test
    fun evaluate_unbalancedParentheses_returnsNull() {
        val result = ExpressionEvaluator.evaluate("(2+3")
        assertNull(result)
    }

    @Test
    fun looksLikeExpression_validExpressions() {
        assertTrue(ExpressionEvaluator.looksLikeExpression("2+2"))
        assertTrue(ExpressionEvaluator.looksLikeExpression("2024-2001"))
        assertTrue(ExpressionEvaluator.looksLikeExpression("3.5*2"))
        assertTrue(ExpressionEvaluator.looksLikeExpression("(12+8)/4"))
        assertTrue(ExpressionEvaluator.looksLikeExpression("2^10"))
    }

    @Test
    fun looksLikeExpression_invalidInputs() {
        assertFalse(ExpressionEvaluator.looksLikeExpression("-5"))
        assertFalse(ExpressionEvaluator.looksLikeExpression("555 1234"))
        assertFalse(ExpressionEvaluator.looksLikeExpression("7zip"))
        assertFalse(ExpressionEvaluator.looksLikeExpression("call mom"))
        assertFalse(ExpressionEvaluator.looksLikeExpression("42"))
    }

    @Test
    fun format_integer() {
        val result = ExpressionEvaluator.format(4.0)
        assertEquals("4", result)
    }

    @Test
    fun format_decimal_truncatesTrailingZeros() {
        val result = ExpressionEvaluator.format(33.333333333)
        assertEquals("33.333333", result)
    }

    @Test
    fun format_decimal() {
        val result = ExpressionEvaluator.format(2.5)
        assertEquals("2.5", result)
    }

    @Test
    fun format_largeNumber() {
        val result = ExpressionEvaluator.format(1e14)
        assertEquals("100000000000000", result)
    }
}

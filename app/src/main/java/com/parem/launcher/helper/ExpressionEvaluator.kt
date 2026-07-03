package com.parem.launcher.helper

import kotlin.math.abs
import kotlin.math.pow

/**
 * Tiny arithmetic evaluator behind the app drawer omnibox calculator.
 * Supports + - * / % ^, parentheses, unary minus, and both '.' and ',' as
 * decimal separators. Recursive descent; returns null on any parse error
 * instead of throwing.
 */
object ExpressionEvaluator {

    private val allowedChars = Regex("^[0-9+\\-*/%^().,\\s]+$")

    // An operator that isn't just a leading unary minus ("-5" is not an expression)
    private val operatorBeyondStart = Regex("[+*/%^]|(?<=.)-")

    fun looksLikeExpression(input: String): Boolean {
        val s = input.trim()
        if (s.length < 3) return false
        if (!allowedChars.matches(s)) return false
        if (!s.any { it.isDigit() }) return false
        return operatorBeyondStart.containsMatchIn(s)
    }

    fun evaluate(input: String): Double? = try {
        val parser = Parser(input.trim().replace(',', '.'))
        val v = parser.parseExpression()
        if (parser.atEnd() && v.isFinite()) v else null
    } catch (_: Exception) {
        null
    }

    fun format(value: Double): String {
        if (value == value.toLong().toDouble() && abs(value) < 1e15)
            return value.toLong().toString()
        // Locale.ROOT: device locales with comma decimals would break the trimming
        return String.format(java.util.Locale.ROOT, "%.6f", value).trimEnd('0').trimEnd('.')
    }

    private class Parser(private val s: String) {
        private var pos = 0

        private fun skipWs() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }

        private fun peek(): Char? {
            skipWs()
            return if (pos < s.length) s[pos] else null
        }

        fun atEnd(): Boolean {
            skipWs()
            return pos >= s.length
        }

        // expression := term (('+' | '-') term)*
        fun parseExpression(): Double {
            var v = parseTerm()
            while (true) {
                when (peek()) {
                    '+' -> { pos++; v += parseTerm() }
                    '-' -> { pos++; v -= parseTerm() }
                    else -> return v
                }
            }
        }

        // term := power (('*' | '/' | '%') power)*
        private fun parseTerm(): Double {
            var v = parsePower()
            while (true) {
                when (peek()) {
                    '*' -> { pos++; v *= parsePower() }
                    '/' -> { pos++; v /= parsePower() }
                    '%' -> { pos++; v %= parsePower() }
                    else -> return v
                }
            }
        }

        // power := unary ('^' power)?  — right-associative
        private fun parsePower(): Double {
            val base = parseUnary()
            if (peek() == '^') {
                pos++
                return base.pow(parsePower())
            }
            return base
        }

        private fun parseUnary(): Double = when (peek()) {
            '-' -> { pos++; -parseUnary() }
            '+' -> { pos++; parseUnary() }
            else -> parseAtom()
        }

        private fun parseAtom(): Double {
            if (peek() == '(') {
                pos++
                val v = parseExpression()
                require(peek() == ')')
                pos++
                return v
            }
            skipWs()
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
            require(pos > start)
            return s.substring(start, pos).toDouble()
        }
    }
}

package com.yesmin.ai

import java.util.Locale

/** Local calculator: + - * / % and parentheses. No AI involved. */
object Calculator {
    private class Parser(val s: String) {
        var i = 0
        fun expr(): Double {
            var v = term()
            while (i < s.length && (s[i] == '+' || s[i] == '-')) {
                val op = s[i++]
                val r = term()
                v = if (op == '+') v + r else v - r
            }
            return v
        }
        fun term(): Double {
            var v = factor()
            while (i < s.length && (s[i] == '*' || s[i] == '/' || s[i] == '%')) {
                val op = s[i++]
                val r = factor()
                v = when (op) {
                    '*' -> v * r
                    '/' -> { if (r == 0.0) throw ArithmeticException("div0"); v / r }
                    else -> { if (r == 0.0) throw ArithmeticException("mod0"); v % r }
                }
            }
            return v
        }
        fun factor(): Double {
            if (i < s.length && s[i] == '-') { i++; return -factor() }
            if (i < s.length && s[i] == '+') { i++; return factor() }
            if (i < s.length && s[i] == '(') {
                i++
                val v = expr()
                if (i >= s.length || s[i] != ')') throw IllegalArgumentException("paren")
                i++
                return v
            }
            val st = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            if (st == i) throw IllegalArgumentException("number")
            return s.substring(st, i).toDouble()
        }
    }

    fun eval(input: String): Double? = try {
        val clean = input.replace("×", "*").replace("÷", "/").replace('x', '*').replace('X', '*').replace(" ", "")
        val p = Parser(clean)
        val v = p.expr()
        if (p.i != clean.length || v.isNaN() || v.isInfinite()) null else v
    } catch (e: Exception) {
        null
    }

    fun format(d: Double): String =
        if (d == Math.floor(d) && Math.abs(d) < 1e15) d.toLong().toString()
        else String.format(Locale.US, "%.8f", d).trimEnd('0').trimEnd('.')
}

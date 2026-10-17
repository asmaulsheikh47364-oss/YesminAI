package com.yesmin.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalculatorTest {
    @Test fun basics() {
        assertEquals(14.0, Calculator.eval("2+3*4")!!, 1e-9)
        assertEquals(20.0, Calculator.eval("(2+3)*4")!!, 1e-9)
        assertEquals(2.5, Calculator.eval("10/4")!!, 1e-9)
        assertEquals(-3.0, Calculator.eval("-5+2")!!, 1e-9)
        assertEquals(6.0, Calculator.eval("2x3")!!, 1e-9)
    }

    @Test fun invalid() {
        assertNull(Calculator.eval("10/0"))
        assertNull(Calculator.eval("2+"))
        assertNull(Calculator.eval("(2+3"))
    }

    @Test fun formatting() {
        assertEquals("14", Calculator.format(14.0))
        assertEquals("2.5", Calculator.format(2.5))
    }
}

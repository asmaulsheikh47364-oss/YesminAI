package com.yesmin.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandRouterTest {
    @Test fun openApp() { assertEquals(Command.OpenApp("whatsapp"), CommandRouter.parse("whatsapp kholo")) }
    @Test fun torchOn() { assertEquals(Command.Torch(true), CommandRouter.parse("torch on karo")) }
    @Test fun torchOff() { assertEquals(Command.Torch(false), CommandRouter.parse("torch off karo")) }
    @Test fun volumeUp() { assertTrue(CommandRouter.parse("volume badhao") is Command.Volume) }
    @Test fun mediaNext() { assertEquals(Command.Media("next"), CommandRouter.parse("next gaana")) }
    @Test fun mediaPause() { assertEquals(Command.Media("pause"), CommandRouter.parse("gaana pause karo")) }
    @Test fun callNeedsName() { assertEquals(Command.Call("rahul"), CommandRouter.parse("rahul ko call karo")) }
    @Test fun unknownIsNone() { assertEquals(Command.None, CommandRouter.parse("kal mausam kaisa rahega")) }
    @Test fun sendingTapsAreConsequential() { assertTrue(CommandRouter.isConsequential("Send")) }
}

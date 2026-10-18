package com.yesmin.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandRouterTest {
    @Test fun openApps() {
        assertEquals(Command.OpenApp("whatsapp"), CommandRouter.parse("WhatsApp kholo"))
        assertEquals(Command.OpenApp("google maps"), CommandRouter.parse("Yesmin, Google Maps kholo"))
        assertEquals(Command.OpenApp("youtube"), CommandRouter.parse("YouTube kholo"))
        assertEquals(Command.OpenApp("settings"), CommandRouter.parse("Settings kholo"))
    }

    @Test fun youtubeSearch() {
        assertEquals(Command.YouTube("arijit singh songs"), CommandRouter.parse("YouTube par Arijit Singh ke songs kholo"))
        assertEquals(Command.YouTube("relaxing music"), CommandRouter.parse("Relaxing music search karo"))
        assertEquals(Command.YouTube("music"), CommandRouter.parse("YouTube par music kholo"))
        assertEquals(Command.YouTube(""), CommandRouter.parse("Mera favorite song search karo"))
    }

    @Test fun phoneInfo() {
        assertEquals(Command.Battery, CommandRouter.parse("battery kitni hai"))
        assertEquals(Command.Time, CommandRouter.parse("abhi time kya hai"))
        assertEquals(Command.Network, CommandRouter.parse("internet status"))
        assertEquals(Command.DeviceInfo, CommandRouter.parse("device model batao"))
    }

    @Test fun calculator() {
        assertEquals(Command.Calc("12 + 8"), CommandRouter.parse("12 + 8"))
        assertEquals(Command.Calc("5*3"), CommandRouter.parse("calculate 5*3"))
    }

    @Test fun accessibility() {
        assertEquals(Command.A11y(A11yAction.Back), CommandRouter.parse("back jao"))
        assertEquals(Command.A11y(A11yAction.ScrollDown), CommandRouter.parse("scroll down"))
        assertEquals(Command.A11y(A11yAction.Tap("search")), CommandRouter.parse("tap search"))
        assertEquals(Command.A11y(A11yAction.Type("Hello World")), CommandRouter.parse("type Hello World"))
        assertTrue(CommandRouter.isConsequential("Send"))
        assertFalse(CommandRouter.isConsequential("Search"))
    }

    @Test fun profileNamePrefix() {
        assertEquals(Command.OpenApp("youtube"), CommandRouter.parse("Zara youtube kholo"))
    }

    @Test fun hindiScript() {
        assertEquals(Command.OpenApp("youtube"), CommandRouter.parse("यूट्यूब खोलो"))
        assertEquals(Command.OpenApp("whatsapp"), CommandRouter.parse("व्हाट्सएप खोलो"))
        assertEquals(Command.YouTube("अरिजीत सिंह songs"), CommandRouter.parse("यूट्यूब पर अरिजीत सिंह के गाने खोलो"))
        assertEquals(Command.YouTube(""), CommandRouter.parse("मेरे लिए गाना लगा दीजिए"))
        assertEquals(Command.YouTube(""), CommandRouter.parse("mere liye gaana laga dijiye"))
        assertEquals(Command.Battery, CommandRouter.parse("बैटरी कितनी है"))
        assertEquals(Command.Time, CommandRouter.parse("अभी टाइम क्या है"))
    }

    @Test fun normalChatFallsThrough() {
        assertEquals(Command.None, CommandRouter.parse("Mujhe ek kahani sunao"))
        assertEquals(Command.None, CommandRouter.parse("Kotlin mein calculator app ka code likho"))
        assertEquals(Command.None, CommandRouter.parse(""))
    }
}

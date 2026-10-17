package com.yesmin.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveTest {
    @Test fun blocksSecrets() {
        assertTrue(Sensitive.looksSensitive("my password is abc123"))
        assertTrue(Sensitive.looksSensitive("card 1234 5678 9012 3456"))
        assertTrue(Sensitive.looksSensitive("OTP 4821"))
    }

    @Test fun allowsNormalNotes() {
        assertFalse(Sensitive.looksSensitive("favorite music Arijit Singh"))
        assertFalse(Sensitive.looksSensitive("kal 5 baje doodh lana hai"))
    }
}

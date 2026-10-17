package com.yesmin.ai

/** Very simple guard so passwords / card numbers don't end up in ordinary memory. */
object Sensitive {
    private val re = Regex(
        "(?i)\\b(password|passcode|passwd|pin|otp|cvv|cvc|aadhaar|aadhar|card number|account number)\\b|\\d{12,}|\\b\\d{4}[ -]\\d{4}[ -]\\d{4}"
    )

    fun looksSensitive(text: String): Boolean = re.containsMatchIn(text)
}

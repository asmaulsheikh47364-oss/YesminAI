package com.yesmin.ai

import androidx.compose.ui.graphics.Color

/** One selectable AI character: name, tone, avatar colours and default voice settings. */
data class Profile(
    val id: String,
    val name: String,
    val tag: String,
    val tone: String,
    val letter: String,
    val c1: Color,
    val c2: Color,
    val pitch: Float,
    val rate: Float,
    val geminiVoice: String
)

object Profiles {
    val all = listOf(
        Profile(
            "yesmin", "Payal", "Kind & Friendly",
            "warm, caring, gentle and encouraging, like a kind close friend",
            "P", Color(0xFF22D3EE), Color(0xFF8B5CF6), 1.1f, 1.0f, "Kore"
        ),
        Profile(
            "zara", "Zara", "Cool & Smart",
            "confident, witty, sharp and concise; gets straight to the point with a light touch of humour",
            "Z", Color(0xFF8B5CF6), Color(0xFF3B82F6), 1.0f, 1.05f, "Zephyr"
        ),
        Profile(
            "riya", "Riya", "Positive & Energetic",
            "cheerful, enthusiastic, upbeat and motivating, uses lively expressions",
            "R", Color(0xFFF472B6), Color(0xFFF59E0B), 1.2f, 1.1f, "Aoede"
        ),
        Profile(
            "aisha", "Aisha", "Calm & Helpful",
            "calm, soothing, patient and thoughtful; explains things slowly and clearly",
            "A", Color(0xFF14B8A6), Color(0xFF22D3EE), 0.95f, 0.9f, "Gacrux"
        )
    )

    fun byId(id: String): Profile? = all.firstOrNull { it.id == id }
}

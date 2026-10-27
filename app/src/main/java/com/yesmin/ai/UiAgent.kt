package com.yesmin.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

class UiJob(
    private val ctx: Context,
    private val gemini: GeminiClient,
    private val key: String,
    private val model: String,
    private val progress: (String) -> Unit
) {
    private val system = """
You control an Android phone through the Accessibility service. Finish the whole task yourself without asking the user at every small step.

WHATSAPP MESSAGE RULES:
If the goal is to send a WhatsApp message:
1. Use open_app to open WhatsApp.
2. Wait, then find and tap the search icon or search box.
3. Type the person's name exactly as the user said.
4. Choose the best matching chat or contact. Prefer an exact name match.
5. If multiple people have the same name, stop and say which one is needed.
6. Open the correct chat.
7. Tap the message input box.
8. Type the exact message.
9. If the GOAL contains CONFIRMED_SEND_YES, find and tap the Send button, then finish with: Message sent to the person.
10. If the GOAL does NOT contain CONFIRMED_SEND_YES, DO NOT press send. Finish with: Message ready. Send button is not pressed.

GENERAL RULES:
- Use open_app to open any installed app by its name.
- To type, first tap the input field, then use type.
- If you cannot see what you need, use scroll_down, scroll_up, back, or look for a search icon or box.
- Never enter passwords, OTP, card numbers, or make payments.
- Never press pay, buy, delete, uninstall, transfer, checkout, place order, or factory reset.
- If something fails, try another way. If truly stuck, finish with done and explain honestly.
- Never claim success unless the screen proves it.

REPLY with exactly one JSON object and nothing else:
{"thought":"short reason","action":"tap","index":3}
{"thought":"short reason","action":"type","text":"hello"}
{"thought":"short reason","action":"open_app","app":"whatsapp"}
Other actions: scroll_down, scroll_up, back, home, wait.
When finished or impossible:
{"done_reason":"short","action":"done","summary":"1 to 3 short Hinglish sentences with the real result"}
    """.trimIndent()

    private val danger = Regex("(?i)(^|[^a-z])(pay|buy|purchase|delete|uninstall|transfer|checkout|place order|factory reset)([^a-z]|$)")
    private val sendWords = Regex("(?i)(send|bhej)")

    private fun parse(t: String): JSONObject? {
        val s = t.trim()
        val a = s.indexOf('{')
        val b = s.lastIndexOf('}')
        if (a < 0 || b <= a) return null
        return try {
            JSONObject(s.substring(a, b + 1))
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun <T> onMain(f: () -> T): T = withContext(Dispatchers.Main) { f() }

    suspend fun run(goal: String): String {
        val nl = System.lineSeparator()
        val allowSend = goal.contains("CONFIRMED_SEND_YES", ignoreCase = true)
        val hist = ArrayList<Pair<String, String>>()
        var note = "Start. Nothing done yet."
        var fails = 0
        var lastSig = ""
        var same = 0
        for (step in 1..30) {
            val svc = YesminAccessibilityService.instance
                ?: return "Accessibility service chalu nahi hai. Settings mein Yesmin AI ko ek baar OFF karke phir ON karo."
            val screen = onMain { svc.screenForAgent() }
            hist.add("user" to ("GOAL: " + goal + nl + "LAST RESULT: " + note + nl + "SCREEN NOW:" + nl + screen + nl + "Reply with one JSON action."))
            progress("Step " + step)
            val send = if (hist.size > 6) hist.takeLast(6) else hist
            val r = gemini.generate(key, model, system, send, 0.2f)
            if (!r.ok) {
                hist.removeAt(hist.size - 1)
                fails++
                if (fails >= 3) return "Gemini se jawab nahi aaya (" + r.text.take(100) + "). Thodi der baad dobara bolo."
                delay(2500L)
                continue
            }
            val reply = r.text.trim()
            hist.add("model" to reply)
            val j = parse(reply)
            if (j == null) {
                note = "Your last reply was not a valid JSON object. Reply with only one JSON object."
                continue
            }
            val act = j.optString("action", "")
            val sig = act + "|" + j.optInt("index", -1) + "|" + j.optString("text", "") + "|" + j.optString("app", "")
            if (sig == lastSig) same++ else {
                same = 0
                lastSig = sig
            }
            if (same >= 3) return "Ek hi action baar baar ho raha hai aur screen nahi badal rahi, isliye ruk gayi. " + j.optString("thought", "")
            when (act) {
                "done" -> {
                    val s = j.optString("summary", "").trim()
                    return if (s.isEmpty()) "Kaam khatam." else s
                }
                "tap" -> {
                    val i = j.optInt("index", -1)
                    val lab = onMain { svc.labelAt(i) }
                    if (lab == null) {
                        note = "There is no element with that index on this screen."
                    } else if (danger.containsMatchIn(lab)) {
                        return "Risky button nahi daba sakti. Aap khud confirm karke dabaiye."
                    } else if (sendWords.containsMatchIn(lab) && !allowSend) {
                        return "Message ready, lekin aapne abhi confirm nahi kiya. Pehle haan boliye."
                    } else {
                        val ok = onMain { svc.tapIndex(i) }
                        note = if (ok) "Tapped [" + i + "] " + lab else "Tap failed on [" + i + "] " + lab
                    }
                }
                "type" -> {
                    val ok = onMain { svc.perform(A11yAction.Type(j.optString("text", ""))) }
                    note = if (ok) "Typed the text." else "Typing failed. Tap an input field first, then type."
                }
                "scroll_down" -> {
                    val ok = onMain { svc.perform(A11yAction.ScrollDown) }
                    note = if (ok) "Scrolled down." else "Scroll failed."
                }
                "scroll_up" -> {
                    val ok = onMain { svc.perform(A11yAction.ScrollUp) }
                    note = if (ok) "Scrolled up." else "Scroll failed."
                }
                "back" -> {
                    val ok = onMain { svc.perform(A11yAction.Back) }
                    note = if (ok) "Went back." else "Back failed."
                }
                "home" -> {
                    val ok = onMain { svc.perform(A11yAction.Home) }
                    note = if (ok) "Went to the home screen." else "Home failed."
                }
                "open_app" -> {
                    val name = j.optString("app", "")
                    note = when (val r2 = onMain { AppLauncher.open(ctx, name) }) {
                        is AppLauncher.R.Opened -> "Opened " + r2.label + "."
                        is AppLauncher.R.Ambiguous -> "Several apps match: " + r2.labels.joinToString(", ") + ". Use one exact name."
                        is AppLauncher.R.Failed -> "Could not open the app: " + r2.msg
                        else -> "No installed app is named " + name + "."
                    }
                    delay(1800L)
                }
                "wait" -> {
                    delay(1500L)
                    note = "Waited."
                }
                else -> note = "Unknown action. Use tap, type, open_app, scroll_down, scroll_up, back, home, wait or done."
            }
            delay(1000L)
        }
        return "30 step mein poora nahi ho paya. Jitna hua wo ho gaya; dobara bolo to aage badhaungi."
    }
}

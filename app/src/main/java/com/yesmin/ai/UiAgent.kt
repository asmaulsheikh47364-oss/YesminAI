package com.yesmin.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Controls any app on screen step by step: reads the screen, picks one action, checks the result, and repeats until the goal is done. */
class UiJob(
    private val ctx: Context,
    private val gemini: GeminiClient,
    private val key: String,
    private val model: String,
    private val progress: (String) -> Unit
) {
    private val system = """
You control an Android phone for the user through the Accessibility service, like a careful person using the phone. The user is not technical; he states a goal in plain words and you do the whole job.
Each turn you get the screen as a list. Lines starting with [number] are things you can tap (button) or type into (input). Lines starting with text: are just labels.
Work like a human: open the right app first, find the right place on screen, tap, type, scroll, go back, and check after every step what the screen shows now.
Rules:
1. Use open_app to open any installed app by its name. Never say an app is not allowed; just try to open it.
2. To type, first tap the input, then use type. After typing a message, tap the send button yourself.
3. If you cannot see what you need, use scroll_down or scroll_up, or back, or look for a search icon or box.
4. Never enter passwords, OTP or card numbers. Never press anything that pays, buys, transfers money or deletes.
5. If something fails, try a different way. If you are truly stuck after several different tries, finish with done and say honestly what is blocking.
6. Never claim success unless the screen proves it.
REPLY with exactly one JSON object and nothing else:
{"thought":"short reason","action":"tap","index":3}
{"thought":"short reason","action":"type","text":"hello"}
{"thought":"short reason","action":"open_app","app":"whatsapp"}
Other actions: scroll_down, scroll_up, back, home, wait.
When the goal is finished or impossible: {"done_reason":"short","action":"done","summary":"1 to 3 short Hinglish sentences with the real result"}
""".trimIndent()

    private val danger = Regex("(?i)(^|[^a-z])(pay|buy|purchase|delete|uninstall|transfer|checkout|place order|factory reset)([^a-z]|$)")

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
        val hist = ArrayList<Pair<String, String>>()
        var note = "Start. Nothing done yet."
        var fails = 0
        var lastSig = ""
        var same = 0
        for (step in 1..25) {
            val svc = YesminAccessibilityService.instance
                ?: return "Accessibility service chalu nahi hai. Settings mein Payal ko ek baar OFF karke phir ON karo."
            val screen = onMain { svc.screenForAgent() }
            hist.add("user" to ("GOAL: " + goal + nl + "LAST RESULT: " + note + nl + "SCREEN NOW:" + nl + screen + nl + "Reply with one JSON action."))
            progress("Step " + step)
            val send = if (hist.size > 5) hist.takeLast(5) else hist
            val r = gemini.generate(key, model, system, send, 0.2f)
            if (!r.ok) {
                hist.removeAt(hist.size - 1)
                fails++
                if (fails >= 3) return "AI se jawab nahi aaya (" + r.text.take(100) + "). Thodi der baad dobara bolo."
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
                        return "'" + lab + "' dabana risky hai (paisa ya delete), isliye ruk gayi. Aap khud dabaiye."
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
                    delay(1500L)
                }
                "wait" -> {
                    delay(1500L)
                    note = "Waited."
                }
                else -> note = "Unknown action. Use tap, type, open_app, scroll_down, scroll_up, back, home, wait or done."
            }
            delay(1000L)
        }
        return "25 step mein poora nahi ho paya. Jitna hua wo ho gaya; dobara bolo to aage badhaungi."
    }
}

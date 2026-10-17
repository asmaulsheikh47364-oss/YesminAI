package com.yesmin.ai

sealed class A11yAction {
    object Back : A11yAction()
    object Home : A11yAction()
    object ScrollDown : A11yAction()
    object ScrollUp : A11yAction()
    data class Tap(val label: String) : A11yAction()
    data class Type(val text: String) : A11yAction()
}

sealed class Command {
    data class OpenApp(val name: String) : Command()
    /** Blank query means "use the user's saved favorite music, or plain music". */
    data class YouTube(val query: String) : Command()
    data class Calc(val expr: String) : Command()
    data class A11y(val action: A11yAction) : Command()
    object Battery : Command()
    object Time : Command()
    object Network : Command()
    object DeviceInfo : Command()
    object None : Command()
}

/** Pure Kotlin (no Android classes) so it can be unit-tested. */
object CommandRouter {
    private val calcRe = Regex(
        "^\\s*(?:yesmin[, ]*)?(?:calculate|calc|hisab|hisaab|solve)?\\s*([0-9+\\-*/().%x×÷ ]+)$",
        RegexOption.IGNORE_CASE
    )
    private val typeRe = Regex("(?i)^(?:yesmin[, ]*)?type\\s+(.+)$")

    private val stop = setOf(
        "yesmin", "youtube", "yt", "par", "pe", "mein", "me", "kholo", "khol", "do", "open", "search", "karo",
        "kar", "karna", "ke", "ka", "ki", "ko", "mera", "meri", "mere", "favorite", "favourite", "please", "plz",
        "song", "songs", "gaane", "gana", "gaana", "gane", "music", "sunao", "chalao", "play", "dikhao", "dhundo",
        "laga", "lagao", "on", "in", "for", "the", "a", "hai", "kya", "mujhe", "ek", "bhi"
    )
    private val songWords = setOf("song", "songs", "gaane", "gana", "gaana", "gane")
    private val searchWords = setOf("search", "play", "chalao", "sunao", "dhundo", "dikhao", "laga", "lagao")
    private val openWords = setOf("kholo", "khol", "open", "launch", "chalu", "start", "kholna", "खोलो", "खोल")
    private val filler = setOf(
        "yesmin", "please", "plz", "ko", "app", "ek", "mujhe", "mera", "meri", "ka", "ki", "ke", "par", "pe",
        "mein", "me", "karo", "kar", "do", "the", "na", "zara"
    )

    fun tokens(s: String): List<String> =
        s.lowercase().split(Regex("[^\\p{L}\\p{M}\\p{N}]+")).filter { it.isNotEmpty() }

    fun isConsequential(label: String): Boolean {
        val l = label.lowercase()
        return listOf("send", "bhej", "pay", "buy", "order", "submit", "delete", "remove", "confirm", "purchase", "post", "transfer", "book")
            .any { l.contains(it) }
    }

    fun parse(input: String): Command {
        val text = input.trim()
        if (text.isEmpty()) return Command.None
        var t = tokens(text)
        if (t.firstOrNull() == "yesmin") t = t.drop(1)
        if (t.isEmpty()) return Command.None

        a11y(text, t)?.let { return Command.A11y(it) }

        calcRe.matchEntire(text)?.let { m ->
            val e = m.groupValues[1].trim()
            if (e.any { it.isDigit() } && e.any { it in "+-*/x×÷%" }) return Command.Calc(e)
        }

        if (t.size <= 7) {
            if ("battery" in t || "बैटरी" in t) return Command.Battery
            if (t.any { it in setOf("time", "samay", "date", "tarikh", "baje") }) return Command.Time
            if (t.any { it in setOf("internet", "wifi", "network") }) return Command.Network
            if (t.any { it in setOf("device", "model") } || ("android" in t && "version" in t)) return Command.DeviceInfo
        }

        val wantsSongs = t.any { it in songWords }
        val wantsMusic = "music" in t
        val hasYoutube = "youtube" in t || "yt" in t
        if (hasYoutube || ((wantsSongs || wantsMusic) && t.any { it in searchWords })) {
            if ("favorite" in t || "favourite" in t) return Command.YouTube("")
            val core = t.filter { it !in stop }
            val extra = if (wantsSongs) listOf("songs") else if (wantsMusic) listOf("music") else emptyList()
            val q = (core + extra).joinToString(" ")
            if (q.isNotBlank()) return Command.YouTube(q)
            if (hasYoutube) return Command.OpenApp("youtube")
        }

        if (t.size <= 4) {
            val idx = t.indexOfFirst { it in openWords }
            if (idx >= 0) {
                var name = t.take(idx).filter { it !in filler }
                if (name.isEmpty()) name = t.drop(idx + 1).filter { it !in filler }
                if (name.isNotEmpty()) return Command.OpenApp(name.joinToString(" "))
            }
        }
        return Command.None
    }

    private fun a11y(text: String, t: List<String>): A11yAction? {
        if (t.size > 8) return null
        val j = t.joinToString(" ")
        if (j in setOf("back", "go back", "back jao", "peeche jao", "back karo", "back dabao")) return A11yAction.Back
        if (j in setOf("home", "go home", "home jao", "home screen")) return A11yAction.Home
        if ("scroll" in t && t.size <= 4) {
            if (t.any { it == "down" || it == "neeche" }) return A11yAction.ScrollDown
            if (t.any { it == "up" || it == "upar" }) return A11yAction.ScrollUp
        }
        if (t[0] == "type") {
            typeRe.find(text)?.let { return A11yAction.Type(it.groupValues[1].trim()) }
        }
        if (t[0] in setOf("tap", "click", "dabao", "press") && t.size >= 2) {
            return A11yAction.Tap(t.drop(1).joinToString(" "))
        }
        return null
    }
}

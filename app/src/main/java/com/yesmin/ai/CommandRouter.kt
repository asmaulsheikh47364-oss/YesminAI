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
    /** "10 minute baad ..." style quick reminder. */
    data class Reminder(val title: String, val minutes: Int) : Command()
    object ReminderHelp : Command()
    data class Torch(val on: Boolean) : Command()
    data class Volume(val action: String, val percent: Int?) : Command()
    data class Call(val target: String) : Command()
    data class Message(val name: String, val channel: String, val body: String) : Command()
    data class OpenPanel(val page: String) : Command()
    data class Dnd(val on: Boolean) : Command()
    object ReadMessages : Command()
    data class Reply(val text: String) : Command()
    /** Voice session control: pause | resume | stop | stopspeak. */
    data class Voice(val action: String) : Command()
    /** Media key press: play | pause | next | previous. */
    data class Media(val action: String) : Command()
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
    private val msgRe = Regex(
        "(?i)^\\s*(?:(?:yesmin|zara|riya|aisha)[, ]*)?(.+?)\\s+ko\\s+(whatsapp|sms|message|msg)(?:\\s+(?:message|msg))?(?:\\s+(?:par|pe|mein|me))?\\s+(?:bhejo|bhej do|karo|send|likho)\\s*[:,-]?\\s*(.*)$"
    )
    private val replyRe = Regex("(?i)^\\s*(?:(?:yesmin|zara|riya|aisha)[, ]*)?reply\\s*(?:(?:karo|do|bhejo|send)\\s*[:,-]?|[:,-])\\s*(.+)$")
    private val typeRe = Regex("(?i)^(?:yesmin[, ]*)?type\\s+(.+)$")

    private val stop = setOf(
        "yesmin", "jasmine", "jasmin", "yasmin", "youtube", "yt", "par", "pe", "mein", "me", "kholo", "khol", "do", "open", "search", "karo",
        "kar", "karna", "ke", "ka", "ki", "ko", "mera", "meri", "mere", "favorite", "favourite", "please", "plz",
        "song", "songs", "gaane", "gana", "gaana", "gane", "music", "sunao", "chalao", "play", "dikhao", "dhundo",
        "laga", "lagao", "on", "in", "for", "the", "a", "hai", "kya", "mujhe", "ek", "bhi",
        "liye", "dijiye", "dijie", "de", "dena", "bajao", "baja", "chala", "lagaiye", "chalaiye", "sunaiye", "koi", "achha", "accha"
    )
    private val songWords = setOf("song", "songs", "gaane", "gana", "gaana", "gane")
    private val searchWords = setOf(
        "search", "play", "chalao", "sunao", "dhundo", "dikhao", "laga", "lagao",
        "bajao", "baja", "chala", "lagaiye", "chalaiye", "sunaiye"
    )
    private val openWords = setOf("kholo", "khol", "open", "launch", "chalu", "start", "kholna", "खोलो", "खोल")
    private val filler = setOf(
        "yesmin", "jasmine", "jasmin", "yasmin", "please", "plz", "ko", "app", "ek", "mujhe", "mera", "meri", "ka", "ki", "ke", "par", "pe",
        "mein", "me", "karo", "kar", "do", "the", "na", "zara"
    )


    private val dev: Map<String, String> = mapOf(
        "यूट्यूब" to "youtube", "यूटूब" to "youtube", "युट्यूब" to "youtube", "यूट्युब" to "youtube",
        "खोलो" to "kholo", "खोल" to "kholo", "खोलिए" to "kholo", "खोलें" to "kholo", "खोलना" to "kholo",
        "चालू" to "chalu", "शुरू" to "start",
        "गाना" to "gaana", "गाने" to "gaane", "गीत" to "gaana", "संगीत" to "music", "म्यूजिक" to "music", "म्यूज़िक" to "music",
        "सर्च" to "search", "खोजो" to "search", "ढूंढो" to "search", "ढूँढो" to "search",
        "चलाओ" to "chalao", "सुनाओ" to "sunao", "प्ले" to "play", "लगाओ" to "lagao", "दिखाओ" to "dikhao",
        "बैटरी" to "battery", "समय" to "time", "टाइम" to "time", "तारीख" to "date", "डेट" to "date",
        "इंटरनेट" to "internet", "वाईफाई" to "wifi", "वाइफाई" to "wifi", "नेटवर्क" to "network",
        "डिवाइस" to "device", "मॉडल" to "model", "मोडल" to "model",
        "बैक" to "back", "जाओ" to "jao", "पीछे" to "peeche", "होम" to "home", "स्क्रॉल" to "scroll",
        "नीचे" to "neeche", "ऊपर" to "upar", "टैप" to "tap", "क्लिक" to "click", "दबाओ" to "dabao",
        "पर" to "par", "पे" to "pe", "में" to "mein", "मे" to "me", "के" to "ke", "का" to "ka", "की" to "ki", "को" to "ko",
        "मेरा" to "mera", "मेरी" to "meri", "मेरे" to "mere", "करो" to "karo", "कर" to "kar", "दो" to "do",
        "एक" to "ek", "मुझे" to "mujhe", "भी" to "bhi", "है" to "hai", "हैं" to "hai", "क्या" to "kya",
        "पसंदीदा" to "favorite", "फेवरेट" to "favorite", "फेवरिट" to "favorite",
        "व्हाट्सएप" to "whatsapp", "व्हाट्सऐप" to "whatsapp", "वॉट्सऐप" to "whatsapp", "वाट्सऐप" to "whatsapp",
        "व्हॉट्सएप" to "whatsapp", "वॉट्सएप" to "whatsapp", "व्हाट्सअप" to "whatsapp",
        "क्रोम" to "chrome", "कैमरा" to "camera", "कैमेरा" to "camera", "सेटिंग" to "settings", "सेटिंग्स" to "settings",
        "गूगल" to "google", "मैप" to "maps", "मैप्स" to "maps", "जीमेल" to "gmail", "इंस्टाग्राम" to "instagram",
        "फेसबुक" to "facebook", "टेलीग्राम" to "telegram", "फोन" to "phone", "कैलकुलेटर" to "calculator",
        "गैलरी" to "gallery", "स्टोर" to "store",
        "लगा" to "laga", "लगाइए" to "lagaiye", "दीजिए" to "dijiye", "दीजिये" to "dijiye", "लिए" to "liye",
        "बजाओ" to "bajao", "बजा" to "baja", "चला" to "chala", "चलाइए" to "chalaiye", "सुनाइए" to "sunaiye", "कोई" to "koi", "अच्छा" to "achha",
        "मिनट" to "minute", "घंटे" to "ghante", "घंटा" to "ghanta", "रिमाइंडर" to "reminder", "याद" to "yaad",
        "दिलाओ" to "dilao", "दिलाना" to "dilana", "बाद" to "baad", "अलार्म" to "alarm", "सेट" to "set",
        "टॉर्च" to "torch", "फ्लैशलाइट" to "flashlight", "जलाओ" to "jalao", "बंद" to "band", "ऑन" to "on", "ऑफ" to "off",
        "वॉल्यूम" to "volume", "आवाज" to "volume", "बढाओ" to "badhao", "बढ\u095Dाओ" to "badhao", "घटाओ" to "ghatao", "कम" to "kam",
        "म्यूट" to "mute", "कॉल" to "call", "मैसेज" to "message", "मैसेजेस" to "messages", "पढो" to "padho", "पढ\u095Dो" to "padho",
        "सुनो" to "suno", "ब्लूटूथ" to "bluetooth", "वाईफाई" to "wifi",
        "यस्मिन" to "yesmin",
        "थर्मक्स" to "termux", "थर्मॉक्स" to "termux", "थर्मोक्स" to "termux", "टर्मक्स" to "termux", "टर्मुक्स" to "termux", "थरमक्स" to "termux", "टरमक्स" to "termux", "यसमिन" to "yesmin", "येस्मिन" to "yesmin", "यस्मीन" to "yesmin"
    )

    /** Splits text into words; Devanagari (Hindi script) words are mapped to the same Roman keywords. */
    fun tokens(s: String): List<String> =
        s.lowercase().split(Regex("[^\\p{L}\\p{M}\\p{N}]+")).filter { it.isNotEmpty() }
            .map { w -> val n = w.replace("\u093C", ""); dev[n] ?: n }

    fun isConsequential(label: String): Boolean {
        val l = label.lowercase()
        return listOf("send", "bhej", "pay", "buy", "order", "submit", "delete", "remove", "confirm", "purchase", "post", "transfer", "book")
            .any { l.contains(it) }
    }

    fun parse(input: String): Command {
        val text = input.trim()
        if (text.isEmpty()) return Command.None
        var t = tokens(text)
        if (t.firstOrNull() in setOf("yesmin", "zara", "riya", "aisha", "jasmine", "jasmin", "yasmin")) t = t.drop(1)
        if (t.isEmpty()) return Command.None

        a11y(text, t)?.let { return Command.A11y(it) }

        if (t.size <= 5) {
            if (t == listOf("stop") || ("stop" in t && "speaking" in t)) return Command.Voice("stopspeak")
            if (t.any { it in setOf("pause", "ruko", "rukiye") } && t.none { it in setOf("video", "song", "songs", "music", "gaana", "gaane", "gana", "track") }) return Command.Voice("pause")
            if ("resume" in t || ("dobara" in t && "shuru" in t)) return Command.Voice("resume")
            if (("stop" in t || "band" in t) && t.any { it in setOf("listening", "session") }) return Command.Voice("stop")
        }

        calcRe.matchEntire(text)?.let { m ->
            val e = m.groupValues[1].trim()
            if (e.any { it.isDigit() } && e.any { it in "+-*/x×÷%" }) return Command.Calc(e)
        }

        msgRe.matchEntire(text)?.let { m ->
            val ch = if (m.groupValues[2].lowercase() == "whatsapp") "whatsapp" else "sms"
            return Command.Message(m.groupValues[1].trim(), ch, m.groupValues[3].trim())
        }
        replyRe.matchEntire(text)?.let { m -> return Command.Reply(m.groupValues[1].trim()) }

        val hasReminderNoun = t.any { it in setOf("reminder", "remind", "alarm") }
        val hasYaadDila = "yaad" in t && t.any { it in setOf("dilao", "dilana", "dila", "dilaiye", "dilado", "dijiye") }
        if ((hasReminderNoun || hasYaadDila) && t.size <= 14) {
            var minutes = 0
            var idx = -1
            for (i in 0 until t.size - 1) {
                val n = t[i].toIntOrNull()
                if (n != null) {
                    val unit = t[i + 1]
                    if (unit in setOf("minute", "minutes", "min", "mins")) { minutes = n; idx = i; break }
                    if (unit in setOf("ghante", "ghanta", "hour", "hours", "hr", "hrs")) { minutes = n * 60; idx = i; break }
                }
            }
            if (minutes > 0) {
                val skip = setOf(
                    "baad", "mein", "me", "ke", "ka", "ki", "liye", "yaad", "dilao", "dilana", "dila", "dilaiye", "dilado", "dijiye",
                    "reminder", "remind", "set", "karo", "kar", "laga", "lagao", "mujhe", "mere", "meri", "ek", "after", "in", "to",
                    "alarm", "bhi", "hai", "please", "plz", "aur", "me", "do", "dena", "de"
                )
                val title = t.filterIndexed { i, w -> i != idx && i != idx + 1 && w !in skip && w.toIntOrNull() == null }.joinToString(" ")
                return Command.Reminder(title.ifBlank { "Reminder" }, minutes)
            }
            return Command.ReminderHelp
        }

        val onWords = setOf("on", "chalu", "jalao", "start", "kholo", "enable")
        val offWords = setOf("off", "band", "bandh", "bujhao", "stop", "disable")
        val turnOn = t.any { it in onWords }
        val turnOff = t.any { it in offWords }
        if (t.size <= 6 && t.any { it in setOf("torch", "flashlight", "tourch", "torchlight") } && (turnOn || turnOff)) {
            return Command.Torch(!turnOff)
        }
        if (t.size <= 7 && "volume" in t) {
            val pct = t.firstNotNullOfOrNull { it.toIntOrNull() }
            val action = when {
                "mute" in t -> "mute"
                t.any { it in setOf("badhao", "up", "increase", "zyada", "tez", "plus") } -> "up"
                t.any { it in setOf("kam", "down", "ghatao", "decrease", "dheema", "low", "minus") } -> "down"
                "max" in t || "full" in t -> "max"
                pct != null -> "set"
                else -> ""
            }
            if (action.isNotEmpty()) return Command.Volume(action, pct)
        }
        if (t.size <= 6 && "wifi" in t && (turnOn || turnOff)) return Command.OpenPanel("wifi")
        if (t.size <= 6 && "bluetooth" in t && (turnOn || turnOff)) return Command.OpenPanel("bluetooth")
        if (t.size <= 7 && ("dnd" in t || "disturb" in t) && (turnOn || turnOff)) return Command.Dnd(!turnOff)
        val callIdx = t.indexOfFirst { it == "call" || it == "dial" }
        if (callIdx >= 0 && t.size <= 7 && t.any { it in setOf("karo", "kar", "lagao", "laga", "do", "dial", "kardo") }) {
            val fill = setOf("ko", "par", "pe", "yesmin", "mujhe", "please", "plz", "ek")
            val name = t.take(callIdx).filter { it !in fill }.joinToString(" ")
            if (name.isNotBlank()) return Command.Call(name)
        }
        if (t.size <= 6 && t.any { it in setOf("message", "messages", "msg", "msgs", "sandesh") } &&
            t.any { it in setOf("padho", "padh", "suno", "sunao", "read", "batao", "dikhao") }
        ) return Command.ReadMessages

        if (t.size <= 7) {
            if ("battery" in t || "बैटरी" in t) return Command.Battery
            if (t.any { it in setOf("time", "samay", "date", "tarikh", "baje") }) return Command.Time
            if (t.any { it in setOf("internet", "wifi", "network") }) return Command.Network
            if (t.any { it in setOf("device", "model") } || ("android" in t && "version" in t)) return Command.DeviceInfo
        }

        val songish = t.any { it in setOf("song", "songs", "gaana", "gaane", "gana", "music", "track", "video") }
        if (t.size <= 5 && songish) {
            when {
                t.any { it in setOf("next", "agla", "agli", "skip") } -> return Command.Media("next")
                t.any { it in setOf("previous", "prev", "pichla", "pichhla") } -> return Command.Media("previous")
                t.any { it in setOf("pause", "ruko", "roko", "rok", "stop") } -> return Command.Media("pause")
                t.any { it in setOf("resume", "dobara", "phirse") } -> return Command.Media("play")
            }
        }

        val wantsSongs = t.any { it in songWords }
        val wantsMusic = "music" in t
        val hasYoutube = "youtube" in t || "yt" in t
        if (hasYoutube || ((wantsSongs || wantsMusic) && t.any { it in searchWords })) {
            if ("favorite" in t || "favourite" in t) return Command.YouTube("")
            val core = t.filter { it !in stop }
            val extra = if (wantsSongs) listOf("songs") else if (wantsMusic) listOf("music") else emptyList()
            if (core.isEmpty() && !hasYoutube) return Command.YouTube("")
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

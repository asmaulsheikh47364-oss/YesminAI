package com.yesmin.ai

import android.app.Application
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class VoiceState { Idle, Connecting, Listening, Processing, Speaking, Paused, Error }
enum class BrainStatus { NotConnected, Connecting, Connected, InvalidKey, QuotaExceeded, RateLimited, NetworkError, ServiceUnavailable, Error }
data class ChatMsg(val fromUser: Boolean, val text: String, val error: Boolean = false)
data class Pending(val description: String, val action: A11yAction)
data class PendingReply(val msg: IncomingMessage, val text: String)
data class ActionLog(val time: Long, val text: String, val undo: (() -> Unit)? = null)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context = app.applicationContext
    val prefs = Prefs(ctx)
    private val db = MemoryDb(ctx)
    private val gemini = GeminiClient()

    val messages = mutableStateListOf<ChatMsg>()
    var voiceState by mutableStateOf(VoiceState.Idle)
    var brainStatus by mutableStateOf(BrainStatus.NotConnected)
    var brainMessage by mutableStateOf("")
    var models by mutableStateOf(listOf<String>())
    var busy by mutableStateOf(false)
    var toolBusy by mutableStateOf(false)
    var toolOutput by mutableStateOf("")
    var sampleReply by mutableStateOf("")
    var pending by mutableStateOf<Pending?>(null)
    var memories by mutableStateOf(listOf<MemoryItem>())
    var hasKey by mutableStateOf(prefs.apiKey != null)
    var notice by mutableStateOf("")

    var model by mutableStateOf(prefs.model)
    var persona by mutableStateOf(prefs.persona)
    var language by mutableStateOf(prefs.language)
    var theme by mutableStateOf(prefs.theme)
    var memoryEnabled by mutableStateOf(prefs.memoryEnabled)
    var saveHistory by mutableStateOf(prefs.saveHistory)
    var temperature by mutableStateOf(prefs.temperature)
    var voiceName by mutableStateOf(prefs.voiceName)
    var disclosure by mutableStateOf(prefs.disclosure)
    var voicePitch by mutableStateOf(prefs.voicePitch)
    var voiceRate by mutableStateOf(prefs.voiceRate)
    var micLevel by mutableStateOf(0f)
    var onboardingDone by mutableStateOf(prefs.onboardingDone)
    var userName by mutableStateOf(prefs.userName)
    var profileId by mutableStateOf(prefs.profileId)
    var customName by mutableStateOf(prefs.customName)
    var customTone by mutableStateOf(prefs.customTone)
    var voiceEngine by mutableStateOf(prefs.voiceEngine)
    var ttsModelName by mutableStateOf(prefs.ttsModel)
    var reminders by mutableStateOf(listOf<Reminder>())
    var hideReminderText by mutableStateOf(prefs.hideReminderText)
    var pendingReply by mutableStateOf<PendingReply?>(null)
    var lockEnabled by mutableStateOf(prefs.lockEnabled)
    var biometricEnabled by mutableStateOf(prefs.biometricEnabled)
    var locked by mutableStateOf(prefs.lockEnabled)
    var supportNumber by mutableStateOf(prefs.supportNumber)
    var tempMode by mutableStateOf(false)
    var sessionActive by mutableStateOf(false)
    var sessionPaused by mutableStateOf(false)
    var voiceSource by mutableStateOf("")
    var openRoute by mutableStateOf<String?>(null)
    private var silentFails = 0
    private var listenJob: Job? = null
    private var blockedUntil = 0L
    var visionOutput by mutableStateOf("")
    var hasYtKey by mutableStateOf(prefs.youtubeKey != null)
    var ytResults by mutableStateOf(listOf<YouTubeClient.Video>())
    private var wrongAttempts = 0
    private var lockoutUntil = 0L
    val actionLog = mutableStateListOf<ActionLog>()

    private val voice = VoiceManager(ctx, { voiceState = it }, { micLevel = it })
    private var ttsNoticeShown = false
    private var lastUserText = ""
    private val pcm = PcmPlayer()
    private var live: LiveVoice? = null
    private var overlayAsked = false

    init {
        refreshMemories()
        refreshReminders()
        if (prefs.saveHistory) { db.pruneHistory(30); messages.addAll(db.history(100)) }
    }

    override fun onCleared() { voice.shutdown(); pcm.stop() }

    // ---------- settings ----------
    fun setModelName(v: String) { model = v.trim(); prefs.model = model }
    fun changePersona(v: String) { persona = v; prefs.persona = v }
    fun changeLanguage(v: String) { language = v; prefs.language = v; voiceName = null; prefs.voiceName = null }
    fun changeTheme(v: String) { theme = v; prefs.theme = v }
    fun changeTemperature(v: Float) { temperature = v; prefs.temperature = v }
    fun changeVoicePitch(v: Float) { voicePitch = v; prefs.voicePitch = v }
    fun changeVoiceRate(v: Float) { voiceRate = v; prefs.voiceRate = v }
    fun changeUserName(v: String) { userName = v.trim(); prefs.userName = userName }
    fun finishOnboarding(name: String) {
        changeUserName(name)
        onboardingDone = true
        prefs.onboardingDone = true
    }
    fun openApp(name: String) { if (name.isNotBlank()) handleCommand(Command.OpenApp(name.trim()), false) }
    fun openYouTubeSearch(q: String) { if (q.isNotBlank()) { lastUserText = q.trim(); handleCommand(Command.YouTube(q.trim()), false) } }
    fun setVoice(v: String?) { voiceName = v; prefs.voiceName = v }
    fun changeMemoryEnabled(v: Boolean) { memoryEnabled = v; prefs.memoryEnabled = v }
    fun changeSaveHistory(v: Boolean) { saveHistory = v; prefs.saveHistory = v }
    fun acceptDisclosure() { disclosure = true; prefs.disclosure = true }
    fun resetDisclosure() { disclosure = false; prefs.disclosure = false }
    fun voiceNames(): List<String> = voice.voices(language)
    fun statusLabel(): String = when (voiceState) {
        VoiceState.Idle -> "Ready"
        VoiceState.Connecting -> "Jud rahi hoon…"
        VoiceState.Listening -> "Sun rahi hoon…"
        VoiceState.Processing -> "Soch rahi hoon…"
        VoiceState.Speaking -> "Bol rahi hoon…"
        VoiceState.Paused -> "Paused — Resume dabao"
        VoiceState.Error -> "Error — details chat mein"
    }

    // ---------- personalities & speaking ----------
    fun activeProfile(): Profile =
        if (profileId == "custom") {
            val n = customName.ifBlank { "Custom" }
            Profile("custom", n, "Custom", customTone.ifBlank { "friendly and helpful" }, n.take(1).uppercase(), NeonPink, NeonViolet, 1.0f, 1.0f, "Kore")
        } else {
            Profiles.byId(profileId) ?: Profiles.all[0]
        }

    fun changeProfile(id: String) {
        profileId = id
        prefs.profileId = id
        val p = activeProfile()
        changeVoicePitch(p.pitch)
        changeVoiceRate(p.rate)
    }

    fun saveCustom(name: String, tone: String) {
        customName = name.trim().take(20)
        customTone = tone.trim().take(200)
        prefs.customName = customName
        prefs.customTone = customTone
        changeProfile("custom")
    }

    fun changeVoiceEngine(v: String) { voiceEngine = v; prefs.voiceEngine = v }
    fun changeTtsModel(v: String) { ttsModelName = v; prefs.ttsModel = v }

    private fun ttsModel(): String = ttsModelName.ifBlank {
        models.firstOrNull { it.contains("flash") && it.contains("tts") && !it.contains("lite") }
            ?: models.firstOrNull { it.contains("tts") && !it.contains("lite") }
            ?: models.firstOrNull { it.contains("tts") }
            ?: "gemini-2.5-flash-preview-tts"
    }

    fun previewProfile(p: Profile) {
        speakWith("Namaste, main " + p.name + " hoon. " + p.tag + " — aapki madad ke liye taiyaar.", p, true)
    }

    private fun speakWith(text: String, p: Profile = activeProfile(), previewing: Boolean = false) {
        val key = prefs.apiKey
        if (voiceEngine == "gemini" && key != null && disclosure) {
            voiceState = VoiceState.Processing
            viewModelScope.launch {
                val r = gemini.speech(key, ttsModel(), p.geminiVoice, voice.speakable(text).take(1200))
                val audio = r.audio
                if (r.ok && audio != null) {
                    voiceSource = "Gemini voice"
                    voiceState = VoiceState.Speaking
                    pcm.play(audio) { afterTurn() }
                } else {
                    notice = "Gemini awaaz nahi mili (" + r.text.take(80) + ") — is baar awaaz nahi aayegi, jawab text mein hai."
                    afterTurn()
                }
            }
        } else {
            afterTurn()
        }
    }

    private fun phoneSpeak(text: String, pitch: Float, rate: Float) {
        voiceSource = "Phone voice"
        val ok = voice.speak(text, language, voiceName, pitch, rate) { afterTurn() }
        if (!ok) {
            if (!ttsNoticeShown) {
                ttsNoticeShown = true
                messages.add(ChatMsg(false, "Is phone ke speech engine mein is bhasha ki awaaz nahi mili, isliye main bol nahi paa rahi. Settings → Voice se voice data install karo. Tab tak text mein jawab dungi.", true))
            }
        }
    }

    // ---------- chat ----------
    private fun localeTag() = if (language == "english") "en-IN" else "hi-IN"

    private fun reply(text: String, speak: Boolean, error: Boolean = false) {
        messages.add(ChatMsg(false, text, error))
        if (saveHistory && !error && !tempMode) db.addHistory(false, text)
        if (error) {
            if (sessionActive && !sessionPaused) afterTurn() else voiceState = VoiceState.Error
            return
        }
        if (speak) speakWith(text) else afterTurn()
    }

    fun send(text: String, speak: Boolean) {
        val t = text.trim()
        if (t.isEmpty() || busy) return
        messages.add(ChatMsg(true, t))
        lastUserText = t
        if (saveHistory && !tempMode) db.addHistory(true, t)
        val cmd = CommandRouter.parse(t)
        if (cmd !is Command.None) handleCommand(cmd, speak) else askGemini(speak)
    }

    private fun handleCommand(cmd: Command, speak: Boolean) {
        when (cmd) {
            is Command.OpenApp -> {
                if (speak) {
                    viewModelScope.launch {
                        speakThenWait("Theek hai, " + cmd.name + " khol rahi hoon.")
                        doOpenApp(cmd.name, true)
                    }
                } else {
                    doOpenApp(cmd.name, false)
                }
            }
            is Command.YouTube -> {
                var q = cmd.query
                if (q.isBlank()) {
                    q = (if (memoryEnabled) memories.firstOrNull { it.category == "music" || it.category == "favorite" }?.text else null) ?: "music"
                }
                youtubeFlow(q, speak)
            }
            Command.Battery -> reply(PhoneInfo.battery(ctx), speak)
            Command.Time -> reply(PhoneInfo.time(), speak)
            Command.Network -> reply(PhoneInfo.network(ctx), speak)
            Command.DeviceInfo -> reply(PhoneInfo.device(), speak)
            is Command.Reminder -> createQuickReminder(cmd.title, cmd.minutes, speak)
            is Command.Voice -> when {
                cmd.action == "stopspeak" -> stopSpeaking()
                !sessionActive -> reply("Abhi koi voice session chalu nahi hai. Voice tab mein mic dabao.", false)
                cmd.action == "pause" -> { pauseSession(); reply("Pause kar diya. Resume ke liye Resume dabao.", false) }
                cmd.action == "resume" -> resumeSession()
                else -> { stopSession(); reply("Voice session band kar diya.", false) }
            }
            is Command.Torch -> {
                if (PhoneControl.torch(ctx, cmd.on)) {
                    val msg = if (cmd.on) "Torch on kar di." else "Torch band kar di."
                    logAction(msg, { PhoneControl.torch(ctx, !cmd.on) })
                    reply(msg, speak)
                } else {
                    reply("Is phone mein torch nahi mili ya Android ne rok diya.", false, true)
                }
            }
            is Command.Volume -> {
                val (msg, prev) = PhoneControl.volume(ctx, cmd.action, cmd.percent)
                logAction(msg, { PhoneControl.setVolumeRaw(ctx, prev) })
                reply(msg, speak)
            }
            is Command.OpenPanel -> {
                val name = if (cmd.page == "wifi") "Wi-Fi" else "Bluetooth"
                if (PhoneControl.openSettings(ctx, cmd.page)) {
                    logAction("$name settings khuli")
                    reply("Android 10 mein app $name khud on/off nahi kar sakti, isliye uski Settings khol di hai. Wahan switch aap dabao.", speak)
                } else {
                    reply("$name settings nahi khul payi.", false, true)
                }
            }
            is Command.Dnd -> {
                val msg = PhoneControl.dnd(ctx, cmd.on)
                logAction(msg)
                reply(msg, speak)
            }
            is Command.Call -> startCall(cmd.target, speak)
            is Command.Message -> startMessage(cmd.name, cmd.channel, cmd.body, speak)
            Command.ReadMessages -> {
                if (!notificationAccessOn()) {
                    reply("Messages padhne ke liye Notification Access on karna hoga. More → Messages mein button hai.", speak)
                } else {
                    val l = MessageStore.messages.take(5)
                    if (l.isEmpty()) reply("Abhi koi naya message nahi hai.", speak)
                    else reply(l.joinToString("\n") { it.sender + " (" + it.app + "): " + it.text }, speak)
                }
            }
            is Command.Reply -> {
                val m = MessageStore.messages.firstOrNull { it.replyAction != null }
                if (m == null) {
                    reply("Jawab dene layak koi recent message nahi mila.", speak)
                } else {
                    pendingReply = PendingReply(m, cmd.text)
                    reply(m.sender + " ko yeh jawab bhejna hai: \"" + cmd.text + "\". Neeche Bhejo ya Cancel dabao.", false)
                }
            }
            Command.ReminderHelp -> reply("Reminder ke liye bolo: '10 minute baad chai ke liye yaad dilao', ya More mein Reminders kholo.", speak)
            is Command.Calc -> {
                val v = Calculator.eval(cmd.expr)
                reply(if (v == null) "Yeh hisaab main samajh nahi paayi." else "${cmd.expr.trim()} = ${Calculator.format(v)}", speak)
            }
            is Command.A11y -> handleA11y(cmd.action, speak)
            is Command.Media -> {
                val ok = PhoneControl.media(ctx, cmd.action)
                reply(if (ok) "Theek hai, media key bhej di." else "Media control nahi ho paya.", speak)
            }
            Command.None -> {}
        }
    }

    private fun doOpenApp(name: String, speakErrors: Boolean) {
        when (val r = AppLauncher.open(ctx, name)) {
            is AppLauncher.R.Opened -> reply("${r.label} khol diya.", false)
            AppLauncher.R.NotFound -> reply("'$name' naam ka app is phone mein nahi mila.", speakErrors)
            is AppLauncher.R.Ambiguous -> reply("Kaunsa kholna hai: ${r.labels.joinToString(", ")}? Poora naam bolo.", speakErrors)
            is AppLauncher.R.Failed -> reply("App nahi khul paya: ${r.msg}", false, true)
        }
    }

    /** Speaks first and waits for the sentence to finish, so another app opening does not cut Yesmin's voice. */
    private suspend fun speakThenWait(text: String) {
        speakWith(text)
        delay(300)
        var waited = 0
        while ((voiceState == VoiceState.Speaking || voiceState == VoiceState.Processing) && waited < 4000) {
            delay(100)
            waited += 100
        }
    }


    private fun readTermuxCommandResult(): String {
        val prefs = ctx.getSharedPreferences(
            "termux_command_results",
            android.content.Context.MODE_PRIVATE
        )

        if (!prefs.getBoolean("result_received", false)) {
            return "Termux ne abhi command ka result nahi bheja."
        }

        val stdout = prefs.getString("stdout", "").orEmpty().trim()
        val stderr = prefs.getString("stderr", "").orEmpty().trim()
        val exitCode = prefs.getInt("exit_code", -1)
        val errorMessage = prefs.getString("error_message", "").orEmpty().trim()

        prefs.edit().putBoolean("result_received", false).apply()

        return buildString {
            append("Termux result: ")
            append(if (exitCode == 0) "success" else "exit code $exitCode")
            if (stdout.isNotBlank()) append("\nOutput: $stdout")
            if (stderr.isNotBlank()) append("\nError: $stderr")
            if (errorMessage.isNotBlank()) append("\nDetails: $errorMessage")
        }.take(3000)
    }

fun runTermuxCommand(command: String) {
        val cleaned = command.trim()
        if (cleaned.equals("clear", ignoreCase = true) ||
            cleaned.equals("termux clear", ignoreCase = true)) {
            val opened = TermuxAgent.openTerminal(ctx)
            reply(
                if (opened) "Termux mein naya terminal kholkar screen clear karne ki request bheji."
                else "Termux terminal nahi khul saka. RUN_COMMAND permission aur allow-external-apps check karein.",
                true,
                !opened
            )
            return
        }
        if (cleaned.isBlank()) {
            reply("Kaunsa Termux command chalana hai?", false)
            return
        }
        if (cleaned.contains("rm -rf") ||
            cleaned.contains("mkfs") ||
            cleaned.contains("format") ||
            cleaned.contains("shutdown") ||
            cleaned.contains("reboot")
        ) {
            reply("Yeh command risky hai. Ise direct execute nahi kiya.", false, true)
            return
        }

        val prefs = ctx.getSharedPreferences(
            "termux_command_results",
            android.content.Context.MODE_PRIVATE
        )
        prefs.edit().putBoolean("result_received", false).apply()

        val ok = TermuxAgent.execute(
            ctx,
            cleaned,
            "/data/data/com.termux/files/home/YesminAI"
        )
        if (!ok) {
            reply("Termux command nahi bhej payi. Termux RUN_COMMAND permission/configuration check karein.", false, true)
            return
        }

        reply("Termux mein command bhej di. Result aane ka intezar hai.", false)

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            var waited = 0
            while (waited < 15000 && !prefs.getBoolean("result_received", false)) {
                kotlinx.coroutines.delay(250)
                waited += 250
            }
            val result = if (prefs.getBoolean("result_received", false)) {
                readTermuxCommandResult()
            } else {
                "Termux se abhi result nahi mila. Permission aur Termux notification/background execution check karein."
            }
            reply(result, true, !result.contains("success"))
        }
    }

    private fun handleA11y(a: A11yAction, speak: Boolean) {
        if (!YesminAccessibilityService.isRunning()) {
            val a11yStatus = YesminAccessibilityService.status(ctx)
            val message = when (a11yStatus) {
                "ENABLED_NOT_CONNECTED" ->
                    "Accessibility Android Settings mein ON hai, lekin Jasmine ki service abhi connect nahi hui. Service reconnect hone ka intezar karein; main ise OFF nahi bataungi."
                else ->
                    "Accessibility Android Settings mein OFF dikh rahi hai. Aapki setting badalne ki koshish nahi ki gayi."
            }
            reply(message, speak)
            return
        }
        if (a is A11yAction.Tap && CommandRouter.isConsequential(a.label)) {
            pending = Pending("'${a.label}' dabana — yeh message bhejna, submit ya kharidari jaisa kaam ho sakta hai.", a)
            reply("Yeh kaam consequential ho sakta hai. Neeche confirm ya cancel karo.", false)
            return
        }
        runA11y(a, speak)
    }

    fun confirmPending() {
        val p = pending ?: return
        pending = null
        runA11y(p.action, false)
    }

    fun cancelPending() {
        pending = null
        reply("Theek hai, cancel kar diya.", false)
    }

    private fun runA11y(a: A11yAction, speak: Boolean) {
        reply("5 second mein karti hoon — jis app mein karna hai wahan chale jao.", speak)
        viewModelScope.launch {
            delay(5000)
            val ok = YesminAccessibilityService.instance?.perform(a) == true
            reply(if (ok) "Ho gaya." else "Yeh action nahi ho paya (element nahi mila, ya screen protected hai).", false, !ok)
        }
    }

    private fun statusFor(code: Int, text: String): BrainStatus = when {
        code == -1 -> BrainStatus.NetworkError
        code == 429 -> if (text.contains("Aaj ki quota", true)) BrainStatus.QuotaExceeded else BrainStatus.RateLimited
        code >= 500 -> BrainStatus.ServiceUnavailable
        code == 401 || code == 403 -> BrainStatus.InvalidKey
        code == 400 && text.contains("API key", true) -> BrainStatus.InvalidKey
        else -> BrainStatus.Error
    }

    /** Updates the connection status from a real API result and pauses new requests after a rate/quota error. */
    private fun noteResult(r: AiResult) {
        if (r.ok) { brainStatus = BrainStatus.Connected; return }
        brainStatus = statusFor(r.code, r.text)
        if (r.code == 429) {
            val sec = Regex("~(\\d+) second").find(r.text)?.groupValues?.get(1)?.toLongOrNull() ?: 300L
            blockedUntil = System.currentTimeMillis() + sec * 1000
        }
    }

    private fun gateGemini(): String? {
        val wait = (blockedUntil - System.currentTimeMillis()) / 1000
        if (wait > 0) return "Gemini ki limit ki wajah se abhi ruko: " + (wait + 1) + " second."
        if (prefs.apiKey == null) return "Pehle AI Brain screen mein Gemini API key daalo."
        if (!disclosure) return "Pehle Privacy notice accept karo (app dobara kholo ya Settings → Privacy). Local commands tab bhi chalte hain."
        return null
    }

    private fun askGemini(speak: Boolean) {
        gateGemini()?.let { reply(it, false, true); return }
        val key = prefs.apiKey ?: return
        busy = true
        voiceState = VoiceState.Processing
        val hist = messages.filter { !it.error }.takeLast(12)
            .map { (if (it.fromUser) "user" else "model") to it.text }
            .dropWhile { it.first != "user" }
        val mem = if (memoryEnabled && !tempMode) memories.filter { it.category != "snippet" }.take(30).map { it.text } else emptyList()
        viewModelScope.launch {
            val r = gemini.generate(key, model, Personas.chatSystem(persona, language, mem, activeProfile().name, activeProfile().tone), hist, temperature)
            busy = false
            noteResult(r)
            if (r.ok) { reply(r.text, speak); autoRemember() } else reply(r.text, false, true)
        }
    }

    fun speakText(text: String) { speakWith(text) }

    /** Runs a phone command spoken in a Live voice call. Returns a plain result for Gemini to say; nothing is shown on screen. */
    // ---------- Termux agent ----------
    var agentStatus by mutableStateOf("Koi Termux kaam nahi chal raha.")
    private var agentJob: kotlinx.coroutines.Job? = null

    fun startAgent(goalText: String): String {
        val goal = goalText.trim()
        if (goal.isEmpty()) return "Goal batao: kya banana ya karna hai?"
        val key = prefs.apiKey ?: return "Gemini key nahi hai."
        if (agentJob?.isActive == true) return "Ek kaam abhi chal raha hai: " + agentStatus
        if (!TermuxBridge.ready(ctx)) return TermuxBridge.setupHelp(ctx)
        agentStatus = "Shuru: " + goal.take(80)
        agentJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val result = try {
                TermuxJob(ctx, gemini, key, model) { step -> agentStatus = step }.run(goal)
            } catch (e: Exception) {
                "Kaam beech mein ruk gaya: " + (e.message ?: "")
            }
            agentStatus = "Poora: " + result.take(300)
            live?.tellModel("[TASK RESULT] " + result.take(900) + " (Tell the user this outcome briefly in Hindi.)")
        }
        return "Kaam shuru kar diya. Termux mein chal raha hai, poora hone par bata dungi."
    }

    fun testTermux(cb: (String) -> Unit) {
        viewModelScope.launch {
            val r = TermuxBridge.run(ctx, "echo yesmin-ok; uname -m; python3 --version 2>&1", TermuxBridge.HOME, 25000L)
            cb(if (r.ok) "Chal raha hai: " + r.out.trim().replace("\n", " | ").take(120) else "Nahi chala: " + (if (r.note.isNotBlank()) r.note else r.err).take(220))
        }
    }

    private fun tapSend(a: A11yAction): Boolean {
        val svc = YesminAccessibilityService.instance ?: return false
        if (a is A11yAction.Tap && a.label.lowercase() in setOf("send", "bhejo", "bhej", "bhejen")) {
            for (l in listOf("Send", "भेजें", "भेजना", "Send message")) {
                if (svc.perform(A11yAction.Tap(l))) return true
            }
            return false
        }
        return svc.perform(a)
    }

    fun liveCommand(text: String, confirmed: Boolean): String {

        val raw = text.trim()
        val rawLower = raw.lowercase()
        if (rawLower.startsWith("termux clear") || rawLower.startsWith("termux screen") || rawLower.startsWith("termux saaf")) {
            return if (TermuxBridge.openTerminal(ctx)) "Opened a fresh Termux terminal, so the Termux screen is now clear." else "Could not open the Termux terminal. Check the Termux permission and allow-external-apps."
        }
        if (raw.startsWith("@task")) return startAgent(raw.removePrefix("@task"))
        if (raw.startsWith("@status")) return agentStatus
        if (raw.startsWith("termux:", ignoreCase = true)) {
            val shellCommand = raw.substringAfter(":").trim()
            if (shellCommand.isBlank()) return "Termux command khaali hai."
            return startAgent("Run exactly this one shell command in Termux and report its real output: " + shellCommand)
        }

        val cmd = CommandRouter.parse(text)
        return try {
            when (cmd) {
                is Command.OpenApp -> when (val r = AppLauncher.open(ctx, cmd.name)) {
                    is AppLauncher.R.Opened -> "Opened " + r.label + "."
                    is AppLauncher.R.Ambiguous -> "More than one app matches: " + r.labels.joinToString(", ") + ". Ask the user which one."
                    is AppLauncher.R.Failed -> "Could not open the app: " + r.msg
                    else -> "No app named '" + cmd.name + "' was found on this phone."
                }
                is Command.YouTube -> {
                    val q = if (cmd.query.isBlank()) "music" else cmd.query
                    if (AppLauncher.openYouTube(ctx, q)) {
                        logAction("YouTube search: " + q)
                        "Opened the YouTube search for '" + q + "'. The user has to tap the song to play it."
                    } else {
                        "Could not open YouTube."
                    }
                }
                is Command.Media -> {
                    if (PhoneControl.media(ctx, cmd.action)) "Sent the " + cmd.action + " media key. Android does not confirm whether a player reacted."
                    else "Could not send the media key."
                }
                is Command.Torch -> {
                    if (PhoneControl.torch(ctx, cmd.on)) {
                        logAction(if (cmd.on) "Torch on" else "Torch off")
                        if (cmd.on) "Torch is on." else "Torch is off."
                    } else {
                        "This phone has no torch or Android blocked it."
                    }
                }
                is Command.Volume -> PhoneControl.volume(ctx, cmd.action, cmd.percent).first
                is Command.OpenPanel -> {
                    val name = if (cmd.page == "wifi") "Wi-Fi" else "Bluetooth"
                    if (PhoneControl.openSettings(ctx, cmd.page)) "Opened the $name settings. Android does not let apps switch it, so the user must flip the switch."
                    else "Could not open the $name settings."
                }
                is Command.Dnd -> PhoneControl.dnd(ctx, cmd.on)
                Command.Battery -> PhoneInfo.battery(ctx)
                Command.Time -> PhoneInfo.time()
                Command.Network -> PhoneInfo.network(ctx)
                Command.DeviceInfo -> PhoneInfo.device()
                is Command.Calc -> {
                    val v = Calculator.eval(cmd.expr)
                    if (v == null) "That calculation could not be understood." else cmd.expr.trim() + " = " + Calculator.format(v)
                }
                is Command.Call -> {
                    when (val w = resolveWho(cmd.target)) {
                        is Who.Ask -> w.text
                        is Who.Ok -> {
                            if (!confirmed) {
                                "CONFIRMATION REQUIRED (ask only once): call " + w.name + ". If the user says yes, call phone_command again with the same command and confirmed=true, and do not ask again."
                            } else if (PhoneControl.dial(ctx, w.number)) {
                                logAction("Dial: " + w.name)
                                "The dialer is open with " + w.name + ". The user has to press the green call button."
                            } else {
                                "The dialer could not be opened."
                            }
                        }
                    }
                }
                is Command.Message -> {
                    if (cmd.body.isBlank()) {
                        "Ask the user what the message should say."
                    } else {
                        when (val w = resolveWho(cmd.name)) {
                            is Who.Ask -> w.text
                            is Who.Ok -> {
                                if (!confirmed) {
                                    "CONFIRMATION REQUIRED (ask only once): send a " + cmd.channel + " message to " + w.name + " saying '" + cmd.body + "'. Read it back in one short sentence. If the user says yes, call phone_command again with the same command and confirmed=true, and do not ask again."
                                } else {
                                    val wa = cmd.channel == "whatsapp"
                                    val ok = if (wa) PhoneControl.whatsapp(ctx, w.number, cmd.body) else PhoneControl.sms(ctx, w.number, cmd.body)
                                    if (!ok) {
                                        "The messaging app could not be opened."
                                    } else {
                                        logAction((if (wa) "WhatsApp" else "SMS") + " draft: " + w.name)
                                        if (YesminAccessibilityService.isRunning()) {
                                            viewModelScope.launch {
                                                kotlinx.coroutines.delay(3000L)
                                                val sent = tapSend(A11yAction.Tap("Send"))
                                                live?.tellModel("[SEND RESULT] The Send button " + (if (sent) "was pressed." else "could not be pressed, so the user must press Send.") + " Tell the user briefly.")
                                            }
                                            "The message for " + w.name + " is open. The app presses Send by itself in about 3 seconds and will tell you the result. Say one short line that it is being sent."
                                        } else {
                                            "The message for " + w.name + " is written. Accessibility is off, so the user must press Send."
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                is Command.A11y -> {
                    val a = cmd.action
                    if (!YesminAccessibilityService.isRunning()) {
                        when (YesminAccessibilityService.status(ctx)) {
                            "ENABLED_NOT_CONNECTED" ->
                                "Accessibility is enabled in Android settings, but Jasmine's service is not connected yet. Do not claim it is off; screen actions are unavailable until it connects."
                            else ->
                                "Accessibility appears disabled in Android settings. Do not claim a screen action succeeded."
                        }
                    } else if (a is A11yAction.Tap && CommandRouter.isConsequential(a.label) && !confirmed) {
                        "CONFIRMATION REQUIRED: tapping '" + a.label + "' may send, pay or delete something. Ask the user to confirm. Only after a clear yes, call phone_command again with the same command and confirmed=true."
                    } else if (tapSend(a)) {
                        "Done."
                    } else {
                        "That could not be done (the element was not found or the screen is protected)."
                    }
                }
                is Command.Voice -> "Use stop_assistant or pause_assistant for that."
                Command.None -> "That was not understood as a phone command. Ask the user to say it again in a simpler way."
                else -> "That action is not available in a voice call."
            }
        } catch (e: Exception) {
            "The command failed: " + (e.message ?: "unknown error")
        }
    }

    fun stopSpeaking() { voice.stopSpeaking(); pcm.stop(); afterTurn() }

    // ---------- continuous voice session ----------
    private fun afterTurn() {
        voiceState = if (sessionActive && sessionPaused) VoiceState.Paused else VoiceState.Idle
        if (sessionActive && !sessionPaused) scheduleListen()
    }

    private fun scheduleListen() {
        if (live != null) return
        listenJob?.cancel()
        listenJob = viewModelScope.launch {
            delay(700)
            if (sessionActive && !sessionPaused && voiceState == VoiceState.Idle && !busy) beginListening()
        }
    }

    private fun beginListening() {
        pcm.stop()
        voice.listen(
            localeTag(),
            onText = { silentFails = 0; voiceState = VoiceState.Idle; send(it, true) },
            onFail = { msg -> onListenFail(msg) }
        )
    }

    private fun onListenFail(msg: String) {
        if (!sessionActive) {
            messages.add(ChatMsg(false, msg, true))
            voiceState = VoiceState.Error
            return
        }
        val fatal = msg.contains("permission", true) || msg.contains("service nahi", true)
        if (fatal) {
            messages.add(ChatMsg(false, msg, true))
            stopSession()
            voiceState = VoiceState.Error
            return
        }
        silentFails++
        if (silentFails >= 6) {
            pauseSession()
            notice = "Kaafi der se kuch sunai nahi diya, isliye pause kar diya."
            return
        }
        voiceState = VoiceState.Idle
        scheduleListen()
    }

    fun startSession() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            voiceDenied()
            openRoute = "home"
            return
        }
        if (sessionActive && !sessionPaused) return
        if (!GlowOverlay.canShow(ctx) && !overlayAsked) {
            overlayAsked = true
            notice = "Background mein light dikhane ke liye 'Display over other apps' allow karo."
            try {
                ctx.startActivity(
                    android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:" + ctx.packageName)
                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Exception) {
            }
        }
        sessionActive = true
        sessionPaused = false
        silentFails = 0
        try {
            AssistantService.start(ctx)
        } catch (e: Exception) {
            notice = "Background service shuru nahi ho payi; app khula rahe tab tak session chalega."
        }
        voiceState = VoiceState.Connecting
        startLive()
    }

    private val liveLog = ArrayList<ChatMsg>()

    private fun startLive() {
        val key = prefs.apiKey
        if (key == null) {
            messages.add(ChatMsg(false, "Pehle API key daalo.", true))
            stopSession()
            return
        }
        gateGemini()?.let {
            messages.add(ChatMsg(false, it, true))
            stopSession()
            return
        }
        val p = activeProfile()
        val mem = if (memoryEnabled && !tempMode) memories.filter { it.category != "snippet" }.take(30).map { it.text } else emptyList()
        val system = Personas.chatSystem(persona, language, mem, p.name, p.tone) +
            " This is a live voice call: reply in short spoken sentences, no markdown, no lists. You can see the user's phone screen: call read_screen whenever they ask what is on the screen, ask you to read, explain or help with something shown, or say this or here; call recent_activity when they ask what they just did. Describe what you find in simple spoken words and never read out passwords or OTPs. In this live call you CAN control the phone, but only through the phone_command function (the earlier rule that you cannot control the phone does not apply here). Pass the user's request as a short Roman Hindi command, for example: 'torch on karo', 'torch off karo', 'volume badhao', 'volume kam karo', 'volume 50', 'youtube kholo', 'whatsapp kholo', 'arijit singh ke gaane youtube par chalao', 'gaana pause karo', 'next gaana', 'pichla gaana', 'rahul ko call karo', 'rahul ko whatsapp bhejo: kal milte hain', 'wifi on karo', 'battery kitni hai', 'back jao', 'home jao', 'scroll neeche'. Say you did something only if the function result confirms it, and report the result honestly (for example when the user still has to tap or press a button). If the result starts with CONFIRMATION REQUIRED, ask the user in one short sentence and call the function again with confirmed=true only after a clear yes. Never place calls, send messages or do anything that sends, pays or deletes without that confirmation. If the user asks what you or the app can do, answer from ABOUT YOURSELF. TERMUX AND CODING: when the user wants something built or changed, or wants commands run (an app, an APK, a script, git, project edits), turn his short request into a complete goal yourself, say it back in one short sentence, and start it with phone_command using command '@task <full goal with every detail you can infer>'. It runs in the background while you keep chatting; ask at most one short question first, only if the goal is truly unclear. Use '@status' to tell progress. When a message starting with [TASK RESULT] arrives, tell the user the real outcome briefly. Never say you cannot use Termux or cannot make an APK: you run commands in Termux and GitHub builds the APK. MESSAGES: when the user asks you to send a message without giving the exact words (for example a nice shayari for Imran), write the message yourself in his style, read it out for approval, and only then use it with the 'whatsapp bhejo' command; after the draft opens and he says yes, call phone_command with 'tap send' and confirmed=true. If a person's name or an app name sounds like Termux (thermux, thermox) it is the app Termux. CONFIRMATIONS: ask for confirmation at most once per action; after the user says yes call phone_command with confirmed=true and do not ask again; if a result says a contact was not found, tell the user and stop. If the user asks to clear the Termux screen, call phone_command with 'termux clear'. To play a song from YouTube results: call read_screen, pick the best matching title, then phone_command 'tap <a few words of that title>'; to play the first result use 'tap pehla result'; to see more results use 'scroll neeche'. Recognize your assistant names as Jasmine, Jasmin, and Yesmin. If the user asks to open Termux, call phone_command with command 'termux kholo'. If the user asks to run a shell command in Termux, state the exact command and ask for a clear yes first; only after the user says yes, call phone_command with command 'termux:<exact shell command>' and confirmed=true. Never infer a shell command from a vague request. The bridge cannot verify command output, so report that honestly. Only if the user says your name (Jasmine, Jasmin, or Yesmin) together with a command: when told to stop, close or switch off, say one short line like okay I am switching off and call stop_assistant; when told to be quiet or pause for some time, say one short okay and call pause_assistant. Never do this without your name being said."
        voiceState = VoiceState.Connecting
        viewModelScope.launch {
            val all = gemini.liveModels(key).filter { !it.contains("translate", true) }
            val m = all.firstOrNull { it.contains("live", true) && !it.contains("thinking", true) }
                ?: all.firstOrNull { it.contains("live", true) } ?: all.firstOrNull()
            if (m == null) {
                messages.add(ChatMsg(false, "Is API key par Live voice model nahi mila.", true))
                stopSession()
                voiceState = VoiceState.Error
                return@launch
            }
            if (!sessionActive || sessionPaused) return@launch
            live?.stop()
            val lv = LiveVoice(
                context = ctx,
                onState = { if (sessionActive && !sessionPaused) voiceState = it },
                onUserText = { liveLog.add(ChatMsg(true, it)); if (liveLog.size > 200) liveLog.removeAt(0) },
                onModelText = { liveLog.add(ChatMsg(false, it)); if (liveLog.size > 200) liveLog.removeAt(0); autoRemember() },
                onLevel = { micLevel = it },
                onTool = { c, ok -> liveCommand(c, ok) },
                onCommand = { c -> if (c == "stop") { try { StandbyService.start(ctx) } catch (e: Exception) {}; stopSession() } else { live?.quiet = true; voiceState = VoiceState.Listening } },
                onError = { msg ->
                    messages.add(ChatMsg(false, msg, true))
                    stopSession()
                    voiceState = VoiceState.Error
                }
            )
            live = lv
            voiceSource = "Gemini Live"
            lv.start(key, m, system, p.geminiVoice)
        }
    }

    fun pauseSession() {
        if (!sessionActive) return
        sessionPaused = true
        live?.stop()
        live = null
        listenJob?.cancel()
        voice.cancelListening()
        voice.stopSpeaking()
        pcm.stop()
        voiceState = VoiceState.Paused
    }

    fun resumeSession() {
        if (!sessionActive) { startSession(); return }
        sessionPaused = false
        silentFails = 0
        voiceState = VoiceState.Connecting
        startLive()
    }

    fun stopSession() {
        autoRemember(true)
        live?.stop()
        live = null
        sessionActive = false
        sessionPaused = false
        listenJob?.cancel()
        voice.cancelListening()
        voice.stopSpeaking()
        pcm.stop()
        voiceState = VoiceState.Idle
        try { AssistantService.stop(ctx) } catch (e: Exception) {}
    }

    fun startListening() {
        if (voiceState == VoiceState.Listening) { cancelListening(); return }
        if (busy) return
        pcm.stop()
        voice.listen(localeTag(),
            onText = { voiceState = VoiceState.Idle; send(it, true) },
            onFail = { msg -> messages.add(ChatMsg(false, msg, true)); voiceState = VoiceState.Error })
    }

    fun cancelListening() { voice.cancelListening(); voiceState = VoiceState.Idle }

    fun voiceDenied() {
        messages.add(ChatMsg(false, "Mic permission nahi mili. Text se type karo, ya Settings → Permissions se mic allow karo.", true))
        voiceState = VoiceState.Error
    }

    fun clearChat() { messages.clear(); voiceState = VoiceState.Idle }

    // ---------- AI brain ----------
    fun saveAndTest(newKey: String) {
        if (newKey.isBlank()) { brainStatus = BrainStatus.Error; brainMessage = "Pehle API key daalo."; return }
        busy = true
        brainMessage = "Test ho raha hai…"
        brainStatus = BrainStatus.Connecting
        viewModelScope.launch {
            val r = gemini.listModels(newKey)
            if (!r.ok) { noteResult(r); brainMessage = r.text; busy = false; return@launch }
            if (!prefs.saveApiKey(newKey)) {
                brainStatus = BrainStatus.Error
                brainMessage = "Key secure storage mein save nahi ho payi."
                busy = false
                return@launch
            }
            hasKey = true
            models = r.models
            generateTest(newKey)
            busy = false
        }
    }

    fun testSaved() {
        val k = prefs.apiKey
        if (k == null) { brainStatus = BrainStatus.NotConnected; brainMessage = "Koi key saved nahi hai."; return }
        busy = true
        brainMessage = "Test ho raha hai…"
        brainStatus = BrainStatus.Connecting
        viewModelScope.launch {
            val r = gemini.listModels(k)
            if (r.ok) { models = r.models; generateTest(k) } else { noteResult(r); brainMessage = r.text }
            busy = false
        }
    }

    private suspend fun generateTest(key: String) {
        val t = gemini.generate(key, model, "Reply with the single word OK.", listOf("user" to "ping"), 0f)
        if (t.ok) {
            brainStatus = BrainStatus.Connected
            brainMessage = "Gemini ($model) se asli jawab mila — Connected."
        } else {
            noteResult(t)
            brainMessage = t.text
        }
    }

    fun removeKey() {
        prefs.clearApiKey()
        hasKey = false
        models = emptyList()
        brainStatus = BrainStatus.NotConnected
        brainMessage = "Key hata di gayi."
    }

    fun sampleTest(msg: String) {
        gateGemini()?.let { sampleReply = it; return }
        val key = prefs.apiKey ?: return
        busy = true
        sampleReply = ""
        viewModelScope.launch {
            val r = gemini.generate(key, model, Personas.chatSystem(persona, language, emptyList(), activeProfile().name, activeProfile().tone), listOf("user" to msg), temperature)
            sampleReply = r.text
            busy = false
        }
    }

    fun runTool(instruction: String, input: String) {
        if (input.isBlank()) { toolOutput = "Pehle kuch likho."; return }
        gateGemini()?.let { toolOutput = it; return }
        val key = prefs.apiKey ?: return
        toolBusy = true
        toolOutput = ""
        viewModelScope.launch {
            val r = gemini.generate(key, model, Personas.toolSystem(instruction, language), listOf("user" to input), temperature)
            toolOutput = r.text
            toolBusy = false
        }
    }

    // ---------- YouTube & photo AI ----------
    private fun youtubeFlow(q0: String, speak: Boolean) {
        viewModelScope.launch {
            if (speak) speakThenWait("Theek hai, YouTube par dhundh rahi hoon.")
            val q = refineYoutubeQuery(q0)
            val key = prefs.youtubeKey
            if (key != null) playOnYouTube(key, q, false) else openYouTubeSearchPage(q, false)
        }
    }

    /** Lets Gemini turn the spoken sentence into a clean search query (artist + song/type). */
    private suspend fun refineYoutubeQuery(fallback: String): String {
        val key = prefs.apiKey ?: return fallback
        if (!disclosure || lastUserText.isBlank()) return fallback
        val r = gemini.generate(
            key, model,
            "Turn the user's spoken request into the single best YouTube search query for the song or video they want " +
                "(artist name plus song or type, in Roman letters, e.g. 'Arijit Singh new songs'). " +
                "Output only the query, at most 8 words, no quotes, no explanation.",
            listOf("user" to lastUserText), 0.2f
        )
        val out = r.text.trim().trim('"').lines().firstOrNull().orEmpty().trim()
        return if (r.ok && out.isNotBlank() && out.length <= 80) out else fallback
    }

    private fun openYouTubeSearchPage(q: String, speak: Boolean) {
        if (AppLauncher.openYouTube(ctx, q)) {
            logAction("YouTube search: $q")
            reply("YouTube par '$q' ka search khol diya. Gaana aapko khud chunke play karna hoga. Seedha video ke liye Settings mein YouTube key daalo.", speak)
        } else {
            reply("YouTube ya browser nahi khul paya.", false, true)
        }
    }

    private fun playOnYouTube(key: String, q: String, speak: Boolean) {
        viewModelScope.launch {
            val r = YouTubeClient.search(key, q)
            if (r.videos.isEmpty()) {
                if (r.error.isNotBlank()) notice = "YouTube API: " + r.error
                openYouTubeSearchPage(q, speak)
            } else {
                ytResults = r.videos
                val v = r.videos[0]
                if (AppLauncher.openYouTubeVideo(ctx, v.id)) {
                    logAction("YouTube: " + v.title)
                    reply("'" + v.title + "' (" + v.channel + ") khol rahi hoon. Agar apne aap play na ho to play button dabao.", speak)
                } else {
                    reply("YouTube nahi khul paya.", false, true)
                }
            }
        }
    }

    fun openVideo(v: YouTubeClient.Video) {
        if (AppLauncher.openYouTubeVideo(ctx, v.id)) logAction("YouTube: " + v.title) else notice = "YouTube nahi khul paya."
    }

    fun saveYoutubeKey(k: String) {
        val key = k.trim()
        if (key.isEmpty()) { notice = "Pehle key daalo."; return }
        if (!prefs.saveYoutubeKey(key)) { notice = "Key save nahi ho payi."; return }
        hasYtKey = true
        viewModelScope.launch {
            val r = YouTubeClient.search(key, "music")
            notice = if (r.videos.isNotEmpty()) "YouTube key kaam kar rahi hai." else "Key save hui, par test fail: " + r.error.take(100)
        }
    }

    fun removeYoutubeKey() {
        prefs.clearYoutubeKey()
        hasYtKey = false
        ytResults = emptyList()
        notice = "YouTube key hata di."
    }

    fun askVision(jpeg: ByteArray, question: String) {
        gateGemini()?.let { visionOutput = it; return }
        val key = prefs.apiKey ?: return
        toolBusy = true
        visionOutput = ""
        viewModelScope.launch {
            val r = gemini.generateVision(
                key, model,
                Personas.toolSystem("Look at the user's photo and answer their question accurately. Say so if you cannot tell something.", language),
                question.ifBlank { "Is photo mein kya hai?" }, jpeg
            )
            visionOutput = r.text
            toolBusy = false
        }
    }

    // ---------- app lock & support ----------
    /** Returns null on success, otherwise a message to show. */
    fun tryUnlock(pin: String): String? {
        val now = System.currentTimeMillis()
        if (now < lockoutUntil) return "Bahut galat koshish. " + ((lockoutUntil - now) / 1000 + 1) + " second baad try karo."
        if (PinLock.check(prefs, pin)) {
            wrongAttempts = 0
            locked = false
            return null
        }
        wrongAttempts++
        if (wrongAttempts >= 5) {
            wrongAttempts = 0
            lockoutUntil = now + 30_000L
            return "5 baar galat PIN. 30 second ruko."
        }
        return "PIN galat hai."
    }

    fun unlock() { locked = false }

    fun enableLock(pin: String) {
        PinLock.set(prefs, pin)
        prefs.lockEnabled = true
        lockEnabled = true
        locked = false
        notice = "App lock on ho gaya."
    }

    fun disableLock() {
        PinLock.clear(prefs)
        prefs.lockEnabled = false
        prefs.biometricEnabled = false
        lockEnabled = false
        biometricEnabled = false
        locked = false
        notice = "App lock band kar diya."
    }

    fun changeBiometric(v: Boolean) { biometricEnabled = v; prefs.biometricEnabled = v }
    fun changeSupportNumber(v: String) { supportNumber = v.trim(); prefs.supportNumber = supportNumber }

    // ---------- phone control & messaging ----------
    private fun logAction(text: String, undo: (() -> Unit)? = null) {
        actionLog.add(0, ActionLog(System.currentTimeMillis(), text, undo))
        while (actionLog.size > 15) actionLog.removeAt(actionLog.size - 1)
    }

    fun undoAction(i: Int) {
        val a = actionLog.getOrNull(i) ?: return
        a.undo?.invoke()
        actionLog[i] = a.copy(text = "↩ " + a.text, undo = null)
    }

    fun notificationAccessOn(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    private sealed class Who {
        class Ok(val name: String, val number: String) : Who()
        class Ask(val text: String) : Who()
    }

    private fun resolveWho(target: String): Who {
        val t = target.trim()
        if (Regex("^[+0-9 \\-]{6,}$").matches(t)) return Who.Ok(t, t)
        val found = Contacts.find(ctx, t)
        return when {
            found == null -> Who.Ask("Naam se dhundhne ke liye Contacts ki ijazat chahiye. More → Messages mein 'Contacts allow karo' dabao.")
            found.isEmpty() -> Who.Ask("'" + t + "' naam ka contact nahi mila.")
            Contacts.distinctNames(found).size > 1 -> Who.Ask("Kaunsa: " + Contacts.distinctNames(found).take(5).joinToString(", ") + "? Poora naam bolo.")
            else -> Who.Ok(found[0].first, found[0].second)
        }
    }

    private fun startCall(target: String, speak: Boolean) {
        when (val w = resolveWho(target)) {
            is Who.Ask -> reply(w.text, speak)
            is Who.Ok -> dialNumber(w.name, w.number, speak)
        }
    }

    fun dialNumber(name: String, number: String, speak: Boolean = false) {
        if (PhoneControl.dial(ctx, number)) {
            logAction("Dial: $name")
            reply("$name ka number dialer mein khol diya hai. Call lagane ke liye aap hara button dabao.", speak)
        } else {
            reply("Dialer nahi khul paya.", false, true)
        }
    }

    private fun startMessage(name: String, channel: String, body: String, speak: Boolean) {
        if (body.isBlank()) {
            reply("Message mein kya likhna hai? Jaise: 'Rahul ko whatsapp bhejo: kal milte hain'.", speak)
            return
        }
        when (val w = resolveWho(name)) {
            is Who.Ask -> reply(w.text, speak)
            is Who.Ok -> openMessage(w.name, w.number, channel, body, speak)
        }
    }

    fun openMessage(name: String, number: String, channel: String, body: String, speak: Boolean): Boolean {
        val wa = channel == "whatsapp"
        val ok = if (wa) PhoneControl.whatsapp(ctx, number, body) else PhoneControl.sms(ctx, number, body)
        if (ok) {
            logAction((if (wa) "WhatsApp" else "SMS") + " draft: " + name)
            reply(name + " ke liye " + (if (wa) "WhatsApp" else "SMS") + " khol diya, message likha hua hai. Bhejne ke liye aap Send dabao.", speak)
        } else {
            reply((if (wa) "WhatsApp" else "SMS") + " nahi khul paya. Kya yeh app phone mein hai?", false, true)
        }
        return ok
    }

    fun confirmReply() {
        val p = pendingReply ?: return
        pendingReply = null
        sendReplyDirect(p.msg, p.text)
    }

    fun cancelReply() {
        pendingReply = null
        reply("Theek hai, jawab cancel kar diya.", false)
    }

    fun sendReplyDirect(m: IncomingMessage, text: String) {
        val ok = MessageReply.send(ctx, m, text)
        if (ok) logAction("Reply bheja: " + m.sender)
        reply(if (ok) m.sender + " ko jawab bhej diya." else "Jawab nahi ja paya (us message ka reply option ab nahi raha).", false, !ok)
    }

    fun draftMessage(idea: String, onResult: (String) -> Unit) {
        gateGemini()?.let { notice = it; return }
        val key = prefs.apiKey ?: return
        busy = true
        viewModelScope.launch {
            val r = gemini.generate(
                key, model,
                Personas.toolSystem("Write one short, natural, polite message (WhatsApp style) for the idea below. Output only the message text, nothing else.", language),
                listOf("user" to idea), temperature
            )
            busy = false
            if (r.ok) onResult(r.text.trim()) else notice = r.text
        }
    }

    // ---------- reminders ----------
    fun refreshReminders() { reminders = db.reminders() }

    fun changeHideReminderText(v: Boolean) { hideReminderText = v; prefs.hideReminderText = v }

    private fun createQuickReminder(title: String, minutes: Int, speak: Boolean) {
        val mins = minutes.coerceIn(1, 60 * 24 * 30)
        val time = System.currentTimeMillis() + mins * 60_000L
        val id = db.addReminder(title, "", time, "none")
        if (id < 0) { reply("Reminder save nahi ho paya.", false, true); return }
        ReminderScheduler.schedule(ctx, Reminder(id, title, "", time, "none", false))
        refreshReminders()
        val at = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(time)
        var msg = "Theek hai, $mins minute baad ($at) '$title' yaad dilaungi."
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) {
            msg += " Par Notifications band hain, isliye alert dikhega nahi — More → Reminders mein allow karo."
        } else if (!ReminderScheduler.canExact(ctx)) {
            msg += " Exact alarm ki ijazat nahi hai, isliye thodi der ho sakti hai."
        }
        reply(msg, speak)
    }

    fun saveReminder(id: Long?, title: String, note: String, time: Long, repeat: String): Boolean {
        val t = title.trim()
        if (t.isEmpty()) { notice = "Pehle likho ki kya yaad dilana hai."; return false }
        if (time <= System.currentTimeMillis() && repeat == "none") { notice = "Aage ka time chuno."; return false }
        val n = note.trim()
        if (id == null) {
            val nid = db.addReminder(t, n, time, repeat)
            if (nid < 0) { notice = "Reminder save nahi ho paya."; return false }
            ReminderScheduler.schedule(ctx, Reminder(nid, t, n, time, repeat, false))
        } else {
            ReminderScheduler.cancel(ctx, id)
            if (!db.updateReminder(id, t, n, time, repeat)) { notice = "Update nahi ho paya."; return false }
            ReminderScheduler.schedule(ctx, Reminder(id, t, n, time, repeat, false))
        }
        refreshReminders()
        notice = if (ReminderScheduler.canExact(ctx)) "Reminder lag gaya." else "Reminder laga (exact alarm ki ijazat nahi, thodi der ho sakti hai)."
        return true
    }

    fun completeReminder(id: Long) {
        db.setReminderDone(id, true)
        ReminderScheduler.cancel(ctx, id)
        refreshReminders()
    }

    fun snoozeReminder(id: Long) {
        val r = db.reminder(id) ?: return
        val nt = System.currentTimeMillis() + 10 * 60_000L
        if (r.repeat == "none") {
            db.updateReminderTime(id, nt)
            db.setReminderDone(id, false)
            ReminderScheduler.schedule(ctx, r.copy(timeMillis = nt, done = false))
        } else {
            ReminderScheduler.scheduleOnce(ctx, id, nt)
        }
        refreshReminders()
        notice = "10 minute baad dobara yaad dilaungi."
    }

    fun deleteReminder(id: Long) {
        ReminderScheduler.cancel(ctx, id)
        db.deleteReminder(id)
        refreshReminders()
    }

    private var lastExtractCount = 0
    private var extracting = false

    /** Quietly turns lasting facts from recent chat into saved notes. */
    private fun autoRemember(force: Boolean = false) {
        Dbg.log(ctx, "remember: force=" + force + " mem=" + memoryEnabled + " temp=" + tempMode + " disc=" + disclosure + " busy=" + extracting, false)
        if (!memoryEnabled || tempMode || extracting) return
        val key = prefs.apiKey ?: return
        val all = (messages + liveLog).filter { !it.error }
        if (all.size < lastExtractCount) lastExtractCount = 0
        val fresh = all.size - lastExtractCount
        if (fresh < (if (force) 1 else 4)) return
        val chunk = all.takeLast(minOf(fresh, 24))
        val known = memories.take(40).map { it.text }
        val sys = "You write long-term memory notes about the user from a chat. " +
            "Return ONLY a JSON array of at most 5 short strings (each under 120 characters), written in the user's own language. " +
            "Keep only lasting facts that will still be useful weeks later: name, family, place, work, projects, preferences, goals, routines. " +
            "Never include passwords, PINs, card, bank or ID numbers, OTPs, exact addresses, phone numbers, health details, one-time requests, small talk, or anything the assistant said about itself. " +
            "Skip facts already in the existing notes. If nothing qualifies, return []."
        val transcript = chunk.joinToString("\n") { (if (it.fromUser) "User: " else "Assistant: ") + it.text.take(400) }
        val ask = "Existing notes:\n" + (if (known.isEmpty()) "(none)" else known.joinToString("\n") { "- " + it }) + "\n\nChat:\n" + transcript
        extracting = true
        lastExtractCount = all.size
        viewModelScope.launch {
            try {
                val r = gemini.generate(key, model, sys, listOf("user" to ask), 0.2f)
                if (!r.ok) return@launch
                val t = r.text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                val a = t.indexOf('[')
                val b = t.lastIndexOf(']')
                if (a < 0 || b <= a) return@launch
                val arr = org.json.JSONArray(t.substring(a, b + 1))
                val have = memories.map { it.text.lowercase() }
                var added = 0
                for (i in 0 until minOf(arr.length(), 5)) {
                    val n = arr.optString(i).trim()
                    if (n.length < 4 || n.length > 160) continue
                    if (Sensitive.looksSensitive(n)) continue
                    if (have.any { it == n.lowercase() }) continue
                    if (db.add("auto", n)) added++
                }
                if (added > 0) refreshMemories()
            } catch (e: Exception) {
            } finally {
                extracting = false
            }
        }
    }

    // ---------- memory ----------
    fun refreshMemories() { memories = db.all() }

    fun addMemory(category: String, text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        if (!memoryEnabled) { notice = "Memory band hai (Settings → Memory)."; return false }
        if (tempMode) { notice = "Temporary chat mein kuch save nahi hota."; return false }
        if (Sensitive.looksSensitive(t)) { notice = "Password/PIN/card jaisi sensitive cheez save nahi karti."; return false }
        val ok = db.add(category, t)
        refreshMemories()
        notice = if (ok) "Save ho gaya." else "Save nahi ho paya."
        return ok
    }

    fun editMemory(id: Long, text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        if (Sensitive.looksSensitive(t)) { notice = "Sensitive cheez save nahi karti."; return }
        db.update(id, t)
        refreshMemories()
    }

    fun deleteMemory(id: Long) { db.delete(id); refreshMemories() }
    fun clearMemory() { db.clearMemory(); refreshMemories(); notice = "Saari memory delete ho gayi." }
    fun clearHistory() { db.clearHistory(); notice = "Saved history delete ho gayi." }

    fun wipeAll() {
        MessageStore.clear()
        pendingReply = null
        actionLog.clear()
        db.reminders().forEach { ReminderScheduler.cancel(ctx, it.id) }
        db.clearReminders()
        refreshReminders()
        prefs.clearYoutubeKey()
        hasYtKey = false
        prefs.clearApiKey()
        db.clearMemory()
        db.clearHistory()
        messages.clear()
        hasKey = false
        models = emptyList()
        brainStatus = BrainStatus.NotConnected
        brainMessage = ""
        refreshMemories()
        notice = "Key, memory aur history sab delete ho gaya."
    }
}

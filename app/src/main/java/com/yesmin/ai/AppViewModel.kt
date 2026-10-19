package com.yesmin.ai

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class VoiceState { Idle, Listening, Thinking, Speaking, Error }
enum class BrainStatus { NotConnected, Connected, Error }
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
    var visionOutput by mutableStateOf("")
    var hasYtKey by mutableStateOf(prefs.youtubeKey != null)
    var ytResults by mutableStateOf(listOf<YouTubeClient.Video>())
    private var wrongAttempts = 0
    private var lockoutUntil = 0L
    val actionLog = mutableStateListOf<ActionLog>()

    private val voice = VoiceManager(ctx, { voiceState = it }, { micLevel = it })
    private var ttsNoticeShown = false
    private val pcm = PcmPlayer()

    init {
        refreshMemories()
        refreshReminders()
        if (prefs.saveHistory) messages.addAll(db.history(50))
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
    fun openYouTubeSearch(q: String) { if (q.isNotBlank()) handleCommand(Command.YouTube(q.trim()), false) }
    fun setVoice(v: String?) { voiceName = v; prefs.voiceName = v }
    fun changeMemoryEnabled(v: Boolean) { memoryEnabled = v; prefs.memoryEnabled = v }
    fun changeSaveHistory(v: Boolean) { saveHistory = v; prefs.saveHistory = v }
    fun acceptDisclosure() { disclosure = true; prefs.disclosure = true }
    fun resetDisclosure() { disclosure = false; prefs.disclosure = false }
    fun voiceNames(): List<String> = voice.voices(language)
    fun statusLabel(): String = when (voiceState) {
        VoiceState.Idle -> "Ready"
        VoiceState.Listening -> "Sun rahi hoon…"
        VoiceState.Thinking -> "Soch rahi hoon…"
        VoiceState.Speaking -> "Bol rahi hoon…"
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
        models.firstOrNull { it.contains("tts") && !it.contains("lite") }
            ?: models.firstOrNull { it.contains("tts") }
            ?: "gemini-2.5-flash-preview-tts"
    }

    fun previewProfile(p: Profile) {
        speakWith("Namaste, main " + p.name + " hoon. " + p.tag + " — aapki madad ke liye taiyaar.", p, true)
    }

    private fun speakWith(text: String, p: Profile = activeProfile(), previewing: Boolean = false) {
        val key = prefs.apiKey
        if (voiceEngine == "gemini" && key != null && disclosure) {
            voiceState = VoiceState.Thinking
            viewModelScope.launch {
                val r = gemini.speech(key, ttsModel(), p.geminiVoice, voice.speakable(text).take(1200))
                val audio = r.audio
                if (r.ok && audio != null) {
                    voiceState = VoiceState.Speaking
                    pcm.play(audio) { voiceState = VoiceState.Idle }
                } else {
                    notice = "Gemini awaaz nahi mili (" + r.text.take(80) + ") — phone ki awaaz se bol rahi hoon."
                    phoneSpeak(text, if (previewing) p.pitch else voicePitch, if (previewing) p.rate else voiceRate)
                }
            }
        } else {
            phoneSpeak(text, if (previewing) p.pitch else voicePitch, if (previewing) p.rate else voiceRate)
        }
    }

    private fun phoneSpeak(text: String, pitch: Float, rate: Float) {
        val ok = voice.speak(text, language, voiceName, pitch, rate) { voiceState = VoiceState.Idle }
        if (!ok) {
            voiceState = VoiceState.Idle
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
        if (error) { voiceState = VoiceState.Error; return }
        if (speak) speakWith(text) else voiceState = VoiceState.Idle
    }

    fun send(text: String, speak: Boolean) {
        val t = text.trim()
        if (t.isEmpty() || busy) return
        messages.add(ChatMsg(true, t))
        if (saveHistory && !tempMode) db.addHistory(true, t)
        val cmd = CommandRouter.parse(t)
        if (cmd !is Command.None) handleCommand(cmd, speak) else askGemini(speak)
    }

    private fun handleCommand(cmd: Command, speak: Boolean) {
        when (cmd) {
            is Command.OpenApp -> when (val r = AppLauncher.open(ctx, cmd.name)) {
                is AppLauncher.R.Opened -> reply("${r.label} khol diya.", speak)
                AppLauncher.R.NotFound -> reply("'${cmd.name}' naam ka app is phone mein nahi mila.", speak)
                is AppLauncher.R.Ambiguous -> reply("Kaunsa kholna hai: ${r.labels.joinToString(", ")}? Poora naam bolo.", speak)
                is AppLauncher.R.Failed -> reply("App nahi khul paya: ${r.msg}", false, true)
            }
            is Command.YouTube -> {
                var q = cmd.query
                if (q.isBlank()) {
                    q = (if (memoryEnabled) memories.firstOrNull { it.category == "music" || it.category == "favorite" }?.text else null) ?: "music"
                }
                val ytKey = prefs.youtubeKey
                if (ytKey != null) playOnYouTube(ytKey, q, speak) else openYouTubeSearchPage(q, speak)
            }
            Command.Battery -> reply(PhoneInfo.battery(ctx), speak)
            Command.Time -> reply(PhoneInfo.time(), speak)
            Command.Network -> reply(PhoneInfo.network(ctx), speak)
            Command.DeviceInfo -> reply(PhoneInfo.device(), speak)
            is Command.Reminder -> createQuickReminder(cmd.title, cmd.minutes, speak)
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
            Command.None -> {}
        }
    }

    private fun handleA11y(a: A11yAction, speak: Boolean) {
        if (!YesminAccessibilityService.isRunning()) {
            reply("Screen control ke liye pehle Accessibility service on karni hogi: Settings tab → Permissions.", speak)
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

    private fun gateGemini(): String? {
        if (prefs.apiKey == null) return "Pehle AI Brain screen mein Gemini API key daalo."
        if (!disclosure) return "Pehle Privacy notice accept karo (app dobara kholo ya Settings → Privacy). Local commands tab bhi chalte hain."
        return null
    }

    private fun askGemini(speak: Boolean) {
        gateGemini()?.let { reply(it, false, true); return }
        val key = prefs.apiKey ?: return
        busy = true
        voiceState = VoiceState.Thinking
        val hist = messages.filter { !it.error }.takeLast(12)
            .map { (if (it.fromUser) "user" else "model") to it.text }
            .dropWhile { it.first != "user" }
        val mem = if (memoryEnabled && !tempMode) memories.filter { it.category != "snippet" }.take(30).map { it.text } else emptyList()
        viewModelScope.launch {
            val r = gemini.generate(key, model, Personas.chatSystem(persona, language, mem, activeProfile().name, activeProfile().tone), hist, temperature)
            busy = false
            if (r.ok) reply(r.text, speak) else reply(r.text, false, true)
        }
    }

    fun speakText(text: String) { speakWith(text) }

    fun stopSpeaking() { voice.stopSpeaking(); pcm.stop(); voiceState = VoiceState.Idle }

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
        viewModelScope.launch {
            val r = gemini.listModels(newKey)
            if (!r.ok) { brainStatus = BrainStatus.Error; brainMessage = r.text; busy = false; return@launch }
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
        viewModelScope.launch {
            val r = gemini.listModels(k)
            if (r.ok) { models = r.models; generateTest(k) } else { brainStatus = BrainStatus.Error; brainMessage = r.text }
            busy = false
        }
    }

    private suspend fun generateTest(key: String) {
        val t = gemini.generate(key, model, "Reply with the single word OK.", listOf("user" to "ping"), 0f)
        if (t.ok) {
            brainStatus = BrainStatus.Connected
            brainMessage = "Gemini ($model) se asli jawab mila — Connected."
        } else {
            brainStatus = BrainStatus.Error
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

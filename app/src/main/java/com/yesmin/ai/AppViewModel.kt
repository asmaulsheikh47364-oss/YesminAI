package com.yesmin.ai

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class VoiceState { Idle, Listening, Thinking, Speaking, Error }
enum class BrainStatus { NotConnected, Connected, Error }
data class ChatMsg(val fromUser: Boolean, val text: String, val error: Boolean = false)
data class Pending(val description: String, val action: A11yAction)

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

    private val voice = VoiceManager(ctx, { voiceState = it }, { micLevel = it })
    private var ttsNoticeShown = false
    private val pcm = PcmPlayer()

    init {
        refreshMemories()
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
        if (saveHistory && !error) db.addHistory(false, text)
        if (error) { voiceState = VoiceState.Error; return }
        if (speak) speakWith(text) else voiceState = VoiceState.Idle
    }

    fun send(text: String, speak: Boolean) {
        val t = text.trim()
        if (t.isEmpty() || busy) return
        messages.add(ChatMsg(true, t))
        if (saveHistory) db.addHistory(true, t)
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
                    q = (if (memoryEnabled) memories.firstOrNull { it.category == "favorite" }?.text else null) ?: "music"
                }
                if (AppLauncher.openYouTube(ctx, q)) {
                    reply("YouTube par '$q' ka search khol diya. Gaana aapko khud chunke play karna hoga — main auto-play nahi kar sakti.", speak)
                } else {
                    reply("YouTube ya browser nahi khul paya.", false, true)
                }
            }
            Command.Battery -> reply(PhoneInfo.battery(ctx), speak)
            Command.Time -> reply(PhoneInfo.time(), speak)
            Command.Network -> reply(PhoneInfo.network(ctx), speak)
            Command.DeviceInfo -> reply(PhoneInfo.device(), speak)
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
        val mem = if (memoryEnabled) memories.filter { it.category != "snippet" }.take(30).map { it.text } else emptyList()
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

    // ---------- memory ----------
    fun refreshMemories() { memories = db.all() }

    fun addMemory(category: String, text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        if (!memoryEnabled) { notice = "Memory band hai (Settings → Memory)."; return false }
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

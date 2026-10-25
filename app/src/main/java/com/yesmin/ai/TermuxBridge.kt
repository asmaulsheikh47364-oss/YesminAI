package com.yesmin.ai

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Receives the result that Termux sends back after a command finished. */
class TermuxResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TermuxBridge.deliver(intent)
    }
}

/** Runs shell commands inside Termux through its official RUN_COMMAND service and returns the real output. */
object TermuxBridge {
    const val PERM = "com.termux.permission.RUN_COMMAND"
    const val HOME = "/data/data/com.termux/files/home"
    private const val BASH = "/data/data/com.termux/files/usr/bin/bash"
    private val waiting = ConcurrentHashMap<Int, CompletableDeferred<Res>>()
    private val nextId = AtomicInteger(1000)

    data class Res(val ok: Boolean, val exit: Int, val out: String, val err: String, val note: String)

    fun installed(ctx: Context): Boolean = try {
        ctx.packageManager.getPackageInfo("com.termux", 0)
        true
    } catch (e: Exception) {
        false
    }

    fun permitted(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, PERM) == PackageManager.PERMISSION_GRANTED

    fun ready(ctx: Context): Boolean = installed(ctx) && permitted(ctx)

    fun setupHelp(ctx: Context): String {
        if (!installed(ctx)) return "Termux phone mein nahi mila. Termux install karna hoga."
        if (!permitted(ctx)) return "Termux ki permission nahi mili. Yesmin Settings mein Termux section mein Permission do dabana hoga."
        return "Termux ready hai."
    }

    private fun pick(b: android.os.Bundle, suffix: String): String {
        for (k in b.keySet()) {
            if (k.lowercase().endsWith(suffix)) {
                val v = b.get(k)
                if (v is String) return v
            }
        }
        return ""
    }

    private fun pickExit(b: android.os.Bundle): Int {
        for (k in b.keySet()) {
            val l = k.lowercase()
            if (l.endsWith("exitcode") || l.endsWith("exit_code")) return b.getInt(k, -1)
        }
        return -1
    }

    fun deliver(i: Intent) {
        val id = i.getIntExtra("rid", -1)
        var b: android.os.Bundle? = i.getBundleExtra("result")
            ?: i.getBundleExtra("com.termux.app.extra.TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE")
            ?: i.getBundleExtra("com.termux.service.EXTRA_PLUGIN_RESULT_BUNDLE")
        if (b == null) {
            val ex = i.extras
            if (ex != null) {
                for (k in ex.keySet()) {
                    val v = ex.get(k)
                    if (v is android.os.Bundle) {
                        b = v
                        break
                    }
                }
            }
        }
        val res = if (b == null) {
            Res(false, -1, "", "", "Termux ka result samajh nahi aaya. Extras: " + (i.extras?.keySet()?.joinToString(",") ?: "koi nahi"))
        } else {
            val exit = pickExit(b)
            val msg = pick(b, "errmsg")
            Res(exit == 0 && msg.isBlank(), exit, pick(b, "stdout"), pick(b, "stderr"), msg)
        }
        waiting[id]?.complete(res)
    }

    /** Opens a fresh visible Termux terminal (this really clears the Termux screen). */
    fun openTerminal(ctx: Context): Boolean = try {
        val i = Intent("com.termux.RUN_COMMAND")
            .setClassName("com.termux", "com.termux.app.RunCommandService")
            .putExtra("com.termux.RUN_COMMAND_PATH", BASH)
            .putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", "clear; cd ~/YesminAI 2>/dev/null; exec bash -l"))
            .putExtra("com.termux.RUN_COMMAND_WORKDIR", HOME)
            .putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
            .putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
        ContextCompat.startForegroundService(ctx.applicationContext, i)
        true
    } catch (e: Exception) {
        false
    }

    suspend fun run(ctx: Context, command: String, workdir: String = HOME, timeoutMs: Long = 180000L): Res {
        val app = ctx.applicationContext
        val id = nextId.incrementAndGet()
        val d = CompletableDeferred<Res>()
        waiting[id] = d
        try {
            val back = Intent(app, TermuxResultReceiver::class.java).putExtra("rid", id)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pi = PendingIntent.getBroadcast(app, id, back, flags)
            val i = Intent("com.termux.RUN_COMMAND")
                .setClassName("com.termux", "com.termux.app.RunCommandService")
                .putExtra("com.termux.RUN_COMMAND_PATH", BASH)
                .putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
                .putExtra("com.termux.RUN_COMMAND_WORKDIR", workdir)
                .putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
                .putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pi)
            ContextCompat.startForegroundService(app, i)
            return withTimeoutOrNull(timeoutMs) { d.await() }
                ?: Res(false, -1, "", "", "Timeout: command ne bahut der li ya Termux ne jawab nahi diya. Termux mein allow-external-apps=true set hai?")
        } catch (e: Exception) {
            return Res(false, -1, "", "", "Termux ko command nahi bheja ja saka: " + (e.message ?: "unknown"))
        } finally {
            waiting.remove(id)
        }
    }
}

/** A small autonomous loop: Gemini picks one Termux command at a time, sees the real result, fixes mistakes and stops when done. */
class TermuxJob(
    private val ctx: Context,
    private val gemini: GeminiClient,
    private val key: String,
    private val model: String,
    private val progress: (String) -> Unit
) {
    private val system = """
You are an autonomous coding agent that works inside Termux on the user's Android phone. You run bash commands one at a time and see their real output.
The user is not a programmer. He gives a goal in plain words; you do the whole job yourself and report only when it is finished or truly impossible.

ENVIRONMENT: every command runs in a fresh non-interactive bash (no TTY) that starts in the home directory, so start project commands with: cd ~/YesminAI && ...
Tools available: bash, python3, git, gh (GitHub CLI, already logged in), sed, grep, cat, head, tail, cut, ls, find, zip, curl.
Never use interactive programs (nano, vim, less, top) or long-running servers.
PROJECT: Android app Yesmin AI (Kotlin, Jetpack Compose, Gemini) in ~/YesminAI, git branch memory, remote on GitHub. Source is under app/src/main/java/com/yesmin/ai/.
The phone cannot compile the app. The APK is built by GitHub Actions: push, then run: gh workflow run "Build debug APK" --ref memory ; check with: gh run list --workflow=build.yml --limit 1 ; download with: gh run download <run id> -D ~/apk-out
To make a brand new Android or other project, create the files with python3 heredocs or cat heredocs and add a GitHub Actions workflow if an APK is wanted.

HOW TO WORK:
1. Look before you change: ls, grep -n, sed -n 'a,bp' file | cut -c1-200. Do not print huge files.
2. Edit files with a python3 heredoc that replaces exact text and asserts the text was found once, or with cat > file <<'EOF'. Keep Kotlin syntax correct.
3. After every edit verify it (grep the new text, git diff --stat, python3 -m py_compile for python files).
4. If a command fails, read the error, change your approach and try again. Never repeat the same failing command. Try different solutions several times before giving up.
5. Never say you succeeded unless a command output proves it.
6. git push and gh workflow run are allowed only when the goal asks to build, push or make an APK. Do not delete anything outside the project. Never print secrets or tokens. Some dangerous commands are blocked and will be refused; choose a safer way.

REPLY FORMAT: exactly one JSON object and nothing else.
To run a command: {"thought":"short reason","cmd":"one bash command"}
When finished or impossible: {"done":true,"summary":"1 to 3 short Hinglish sentences saying what you did and the real result"}
Escape newlines inside the cmd string as \n so the JSON stays valid.
""".trimIndent()

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

    private fun blockReason(cmd: String, allowPush: Boolean): String? {
        val c = cmd.lowercase()
        val bad = listOf("mkfs", "dd if=", ":()", "> /dev/sd", "termux-reset", "pkg uninstall", "apt remove", "apt purge",
            "shutdown", "reboot", "gh auth token", "gh auth status -t", "chmod -r", "su -c", " sudo ")
        for (b in bad) if (c.contains(b)) return "'" + b.trim() + "' allowed nahi hai"
        if (Regex("(curl|wget)[^|;]*\\|\\s*(ba)?sh").containsMatchIn(c)) return "internet se script seedha chalana allowed nahi hai"
        if (Regex("\\brm\\s+-[a-z]*[rf]").containsMatchIn(c) && !(c.contains("/yesminai/") || c.contains("apk-out") || c.contains("/tmp/")))
            return "rm sirf project ke andar allowed hai"
        if (!allowPush && (c.contains("git push") || c.contains("gh workflow run") || c.contains("gh release")))
            return "push ya build tabhi jab user ne maanga ho"
        return null
    }

    private fun shrink(h: List<Pair<String, String>>): List<Pair<String, String>> =
        if (h.size <= 16) h else listOf(h.first()) + h.takeLast(14)

    suspend fun run(goal: String): String {
        val allowPush = Regex("(?i)push|build|apk|workflow|deploy|release|banao|bana do|bana de").containsMatchIn(goal)
        val hist = ArrayList<Pair<String, String>>()
        hist.add("user" to ("GOAL: " + goal + "\nStart now. Reply with one JSON object only."))
        var geminiFails = 0
        var lastCmd = ""
        for (step in 1..30) {
            progress("Step " + step + ": soch rahi hoon")
            val r = gemini.generate(key, model, system, shrink(hist), 0.2f)
            if (!r.ok) {
                geminiFails++
                if (geminiFails >= 3) return "Gemini se jawab nahi aaya (" + r.text.take(100) + "). Thodi der baad dobara bolo."
                delay(2500)
                continue
            }
            val reply = r.text.trim()
            hist.add("model" to reply)
            val j = parse(reply)
            if (j == null) {
                hist.add("user" to "Your reply was not a valid single JSON object. Reply again with only the JSON.")
                continue
            }
            if (j.optBoolean("done", false)) {
                val s = j.optString("summary", "").trim()
                return if (s.isEmpty()) "Kaam khatam." else s
            }
            val cmd = j.optString("cmd", "").trim()
            if (cmd.isEmpty()) {
                hist.add("user" to "No cmd was given. Send a cmd or finish with done.")
                continue
            }
            val why = blockReason(cmd, allowPush)
            if (why != null) {
                hist.add("user" to ("BLOCKED: " + why + ". Choose a safer way."))
                continue
            }
            if (cmd == lastCmd) {
                hist.add("user" to "You repeated the same command. Try something different or finish with done.")
                continue
            }
            lastCmd = cmd
            progress("Step " + step + ": " + cmd.take(70))
            val res = TermuxBridge.run(ctx, cmd)
            val sb = StringBuilder()
            sb.append("exit=").append(res.exit).append("\nSTDOUT:\n").append(res.out.takeLast(2500))
            sb.append("\nSTDERR:\n").append(res.err.takeLast(1200))
            if (res.note.isNotBlank()) sb.append("\nNOTE: ").append(res.note)
            hist.add("user" to sb.toString())
            if (res.note.startsWith("Termux ko command nahi") || res.note.startsWith("Timeout")) {
                return "Termux se baat nahi ho pa rahi: " + res.note
            }
        }
        return "30 step mein poora nahi ho paya. Jitna hua wo Termux mein hai; dobara bolo to aage badhaungi."
    }
}

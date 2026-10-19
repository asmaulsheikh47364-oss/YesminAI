@file:OptIn(ExperimentalMaterial3Api::class)

package com.yesmin.ai

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

data class AiTool(val id: String, val title: String, val desc: String, val instr: String, val hint: String, val group: String)

val AI_TOOLS = listOf(
    AiTool("code_gen", "Code Generator", "Natural language se code (Kotlin, Java, Python, JS, HTML, CSS, SQL…)",
        "Write the requested code. Use fenced code blocks with a language tag, then briefly explain how it works and how to run it. State clearly that you have not compiled or tested it.",
        "Kotlin mein calculator app ka code likho", "Coding Assistant"),
    AiTool("code_explain", "Code Explain", "Pasted code ko samjhao",
        "Explain the pasted code step by step in simple language.", "Yahan code paste karo", "Coding Assistant"),
    AiTool("code_bugs", "Bug Finder & Fix", "Bugs dhoondo aur improvements suggest karo",
        "Analyze the pasted code for bugs, edge cases and improvements. Show a fixed version in a fenced code block. Say that the fix is untested.",
        "Yahan code paste karo", "Coding Assistant"),
    AiTool("code_error", "Compiler Error Explain", "Error message ka matlab aur solution",
        "Explain the pasted compiler/build error: what it means, the most likely cause, and concrete fix steps.",
        "Yahan error paste karo", "Coding Assistant"),
    AiTool("code_tests", "Unit Tests", "Code ke liye tests aur debugging tips",
        "Write unit tests for the pasted code (use the idiomatic framework for its language) and add debugging suggestions.",
        "Yahan code paste karo", "Coding Assistant"),
    AiTool("code_plan", "Project Notes & File Plan", "App/project ka file plan aur notes",
        "Create a concise project plan: goals, architecture, file/folder structure, and build steps for the described project.",
        "Ek simple Android login screen banao", "Coding Assistant"),
    AiTool("translate", "Hindi ⇄ English Translate", "Translation",
        "Translate between Hindi and English. If the input is Hindi, translate to English. If English, translate to Hindi (Devanagari) and also give a Hinglish (Roman) version.",
        "Jo translate karna hai woh likho", "Gemini Tools"),
    AiTool("write", "Writing & Message Draft", "Email/message/letter draft",
        "Write the requested message or text. Ask nothing; make reasonable assumptions and keep the tone appropriate.",
        "Boss ko leave ke liye email likho", "Gemini Tools"),
    AiTool("summary", "Summary", "Text ka summary",
        "Summarize the given text in a few clear bullet points, then give a one-line takeaway.", "Text paste karo", "Gemini Tools"),
    AiTool("study", "Study & Quiz", "Samjhao + quiz banao",
        "Explain the topic simply with an example, then create a 5-question quiz with answers at the end.", "Topic likho (jaise: photosynthesis)", "Gemini Tools"),
    AiTool("ideas", "Ideas & Captions", "Creative ideas, captions",
        "Give 8 creative ideas or captions for the request.", "Kis cheez ke liye ideas chahiye?", "Gemini Tools"),
    AiTool("analyze", "Text/Document Analysis", "Paste kiye text ka basic analysis",
        "Analyze the pasted text: main topic, key points, tone, possible issues, and suggested improvements.", "Text paste karo", "Gemini Tools"),
    AiTool("trouble", "Troubleshooting", "Phone/app/tech problem ka guidance",
        "Give step-by-step troubleshooting guidance for the described problem, ordered from the simplest fix to the most involved.",
        "Problem likho (jaise: Wi-Fi connect nahi ho raha)", "Gemini Tools")
)

@Composable
private fun ToolCard(title: String, desc: String, badge: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(badge, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text(desc, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun ToolsScreen(vm: AppViewModel, nav: NavHostController) {
    val ctx = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    val info = remember(tick) { listOf(PhoneInfo.battery(ctx), PhoneInfo.network(ctx), PhoneInfo.time(), PhoneInfo.device()) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Tools", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Text("Local (internet/AI ki zaroorat nahi)", style = MaterialTheme.typography.titleMedium)
        ToolCard("Calculator", "Local code se hisaab", "Local") { nav.navigate("calc") }
        ToolCard("Memory, Notes & Tasks", "Aapki approved memory dekho/edit/delete karo", "Local") { nav.navigate("memory") }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Phone info", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { tick++ }) { Text("Refresh") }
                }
                info.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }

        listOf("Coding Assistant", "Gemini Tools").forEach { g ->
            Text(g + " (Gemini se, internet + key chahiye)", style = MaterialTheme.typography.titleMedium)
            AI_TOOLS.filter { it.group == g }.forEach { t ->
                ToolCard(t.title, t.desc, "Gemini") { nav.navigate("tool/${t.id}") }
            }
        }

        Text("Abhi available nahi", style = MaterialTheme.typography.titleMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("Image understanding, reminders/alarms, wake-word — is version mein implement nahi hue (FEATURES.md dekho).", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun ToolScreen(vm: AppViewModel, id: String) {
    val tool = AI_TOOLS.firstOrNull { it.id == id }
    var input by remember { mutableStateOf("") }
    val clip = LocalClipboardManager.current
    LaunchedEffect(id) { vm.toolOutput = "" }
    if (tool == null) { Text("Tool nahi mila.", Modifier.padding(16.dp)); return }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(tool.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(tool.desc)
        OutlinedTextField(
            value = input, onValueChange = { input = it }, modifier = Modifier.fillMaxWidth(),
            minLines = 5, placeholder = { Text(tool.hint) }
        )
        Button(onClick = { vm.runTool(tool.instr, input) }, enabled = !vm.toolBusy) { Text("Gemini se chalao") }
        Text("Text Google Gemini ko bheja jaata hai. Generated code compile/test nahi hua hota — khud check karo.", style = MaterialTheme.typography.bodySmall)
        if (vm.toolBusy) CircularProgressIndicator()
        if (vm.toolOutput.isNotBlank()) {
            RichText(vm.toolOutput, onSave = { vm.addMemory("snippet", it) })
            OutlinedButton(onClick = { clip.setText(AnnotatedString(vm.toolOutput)) }) { Text("Poora jawab copy karo") }
        }
    }
}

@Composable
fun CalcScreen() {
    var expr by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Calculator (local)", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = expr, onValueChange = { expr = it }, modifier = Modifier.fillMaxWidth(),
            singleLine = true, label = { Text("jaise: (12 + 8) x 3 / 4") }
        )
        Button(onClick = {
            val v = Calculator.eval(expr)
            result = if (v == null) "Samajh nahi aaya (ya zero se divide)." else "= " + Calculator.format(v)
        }) { Text("=") }
        if (result.isNotBlank()) Text(result, style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
fun MemoryScreen(vm: AppViewModel) {
    val cats = listOf("music", "food", "people", "schedule", "projects", "devices", "preference", "note", "task", "favorite", "snippet")
    val emoji = mapOf(
        "music" to "🎵", "food" to "🍽", "people" to "👥", "schedule" to "📅", "projects" to "💼",
        "devices" to "📱", "preference" to "⚙️", "note" to "📝", "task" to "✅", "favorite" to "⭐", "snippet" to "💻"
    )
    fun catLabel(c: String) = (emoji[c] ?: "") + " " + c
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<String?>(null) }
    var cat by remember { mutableStateOf("note") }
    var text by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<MemoryItem?>(null) }
    var editText by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val shown = vm.memories.filter { (filter == null || it.category == filter) && (search.isBlank() || it.text.contains(search, ignoreCase = true)) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Memory, Notes & Tasks", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Sirf wahi save hota hai jo aap yahan ya 'Save' button se dete ho. Yeh sirf is phone par hai (sync nahi). " +
                "Password/PIN/card save mat karo. Memory " + if (vm.memoryEnabled) "ON hai." else "OFF hai (Settings mein on karo).",
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedTextField(value = search, onValueChange = { search = it }, singleLine = true, label = { Text("🔍 Memory mein khojo") }, modifier = Modifier.fillMaxWidth())
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("all") })
            cats.forEach { c -> FilterChip(selected = filter == c, onClick = { filter = c }, label = { Text(catLabel(c)) }) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Nayi entry:", Modifier.padding(top = 8.dp))
            cats.filter { it != "snippet" }.forEach { c -> FilterChip(selected = cat == c, onClick = { cat = c }, label = { Text(catLabel(c)) }) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.weight(1f), label = { Text("jaise: favorite music Arijit Singh") })
            Button(onClick = { if (vm.addMemory(cat, text)) text = "" }) { Text("Add") }
        }
        if (shown.isEmpty()) Text("Abhi kuch saved nahi hai.")
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(shown, key = { it.id }) { m ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(catLabel(m.category), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(m.text.take(400))
                        Row {
                            TextButton(onClick = { editing = m; editText = m.text }) { Text("Edit") }
                            TextButton(onClick = { vm.deleteMemory(m.id) }) { Text("Delete") }
                        }
                    }
                }
            }
        }
        OutlinedButton(onClick = { confirmClear = true }, enabled = vm.memories.isNotEmpty()) { Text("Saari memory delete karo") }
    }

    editing?.let { m ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Edit") },
            text = { OutlinedTextField(value = editText, onValueChange = { editText = it }, minLines = 3) },
            confirmButton = { TextButton(onClick = { vm.editMemory(m.id, editText); editing = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } }
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Saari memory delete?") },
            text = { Text("Yeh wapas nahi aayegi.") },
            confirmButton = { TextButton(onClick = { vm.clearMemory(); confirmClear = false }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}

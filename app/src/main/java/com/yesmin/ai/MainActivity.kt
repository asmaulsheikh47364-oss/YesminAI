package com.yesmin.ai

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.yesmin.ai.agent.TaskOrchestrator
import com.yesmin.ai.ui.LiveTaskMonitor

class MainActivity : AppCompatActivity() {
    private lateinit var taskOrchestrator: TaskOrchestrator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        taskOrchestrator = TaskOrchestrator(this)
        LiveTaskMonitor.logStatus("MainActivity", "READY", "System initialized successfully.")
        val vm = (application as YesminApp).vm
        setContent { YesminUi(vm) }
    }
}

@Composable
fun YesminUi(vm: AppViewModel) {
    MaterialTheme {
        val nav = rememberNavController()
        NavHost(nav, startDestination = "home") {
            composable("home") { HomeScreen(vm, nav) }
            composable("chat") { ChatScreen(vm) }
            composable("tools") { ToolsScreen(vm, nav) }
            composable("settings") { SettingsScreen(vm, nav) }
            composable("voice") { VoiceScreen(vm) }
            composable("more") { MoreScreen(nav) }
            composable("messages") { MessagesScreen(vm) }
            composable("reminders") { RemindersScreen(vm) }
            composable("brain") { BrainScreen(vm) }
            composable("calc") { CalcScreen() }
            composable("memory") { MemoryScreen(vm) }
            composable("vision") { VisionScreen(vm) }
            composable("profiles") { ProfilesScreen(vm) }
            composable("device") { DeviceScreen() }
            composable("apps") { AppsScreen(vm, nav) }
            composable("support") { SupportScreen(vm) }
            composable("tool/{id}") { entry ->
                ToolScreen(vm, entry.arguments?.getString("id") ?: "")
            }
        }
    }
}

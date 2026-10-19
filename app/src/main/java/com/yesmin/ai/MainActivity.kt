@file:OptIn(ExperimentalMaterial3Api::class)

package com.yesmin.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ReminderScheduler.createChannel(this)
        setContent { YesminRoot() }
    }
}

private data class Tab(val route: String, val label: String, val icon: String)

/** Switch between the five bottom-bar tabs. */
fun navTab(nav: NavHostController, route: String) {
    nav.navigate(route) {
        popUpTo("home") { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun YesminRoot() {
    val vm: AppViewModel = viewModel()
    val dark = when (vm.theme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    var dismissed by remember { mutableStateOf(false) }
    YesminTheme(dark) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (vm.onboardingDone) MainScaffold(vm) else OnboardingScreen(vm)
        }
        if (vm.onboardingDone && !vm.disclosure && !dismissed) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Privacy notice") },
                text = {
                    Text(
                        "Aapke chat/voice ke text messages (aur Tools mein jo text daalo) jawab ke liye internet par Google Gemini ko bheje jaate hain. " +
                            "Phone commands (app kholna, battery, calculator) local hote hain. API key aur memory sirf aapke phone par rehti hai. " +
                            "Memory/history delete karne ke controls Settings mein hain."
                    )
                },
                confirmButton = { TextButton(onClick = { vm.acceptDisclosure() }) { Text("Accept") } },
                dismissButton = { TextButton(onClick = { dismissed = true }) { Text("Abhi nahi") } }
            )
        }
    }
}

@Composable
fun MainScaffold(vm: AppViewModel) {
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm.notice) {
        if (vm.notice.isNotBlank()) {
            snack.showSnackbar(vm.notice)
            vm.notice = ""
        }
    }
    val tabs = listOf(
        Tab("home", "Home", "🏠"), Tab("chat", "Chat", "💬"), Tab("voice", "Voice", "🎤"),
        Tab("apps", "Apps", "📱"), Tab("more", "More", "☰")
    )
    val moreRoutes = setOf("more", "brain", "tools", "settings", "memory", "calc", "device", "support", "profiles", "reminders", "messages")
    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            NavigationBar {
                tabs.forEach { t ->
                    val selected = route == t.route ||
                        (t.route == "more" && (route in moreRoutes || (route?.startsWith("tool/") == true)))
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navTab(nav, t.route) },
                        icon = { Text(t.icon) },
                        label = { Text(t.label) }
                    )
                }
            }
        }
    ) { pad ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(pad)) {
            composable("home") { HomeScreen(vm, nav) }
            composable("chat") { ChatScreen(vm) }
            composable("voice") { VoiceScreen(vm) }
            composable("apps") { AppsScreen(vm, nav) }
            composable("more") { MoreScreen(nav) }
            composable("brain") { BrainScreen(vm) }
            composable("tools") { ToolsScreen(vm, nav) }
            composable("settings") { SettingsScreen(vm, nav) }
            composable("tool/{id}") { e -> ToolScreen(vm, e.arguments?.getString("id") ?: "") }
            composable("memory") { MemoryScreen(vm) }
            composable("calc") { CalcScreen() }
            composable("device") { DeviceScreen() }
            composable("support") { SupportScreen() }
            composable("profiles") { ProfilesScreen(vm) }
            composable("reminders") { RemindersScreen(vm) }
            composable("messages") { MessagesScreen(vm) }
        }
    }
}

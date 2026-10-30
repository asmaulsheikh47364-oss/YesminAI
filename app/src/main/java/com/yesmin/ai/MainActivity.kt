@file:OptIn(ExperimentalMaterial3Api::class)

package com.yesmin.ai

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.core.view.WindowCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
    private val vm: AppViewModel get() = (application as YesminApp).vm
    private var stoppedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ReminderScheduler.createChannel(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        setContent { YesminRoot() }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        if (i == null) return
        i.getStringExtra("route")?.let { vm.openRoute = it }
        if (i.getBooleanExtra("start_session", false)) {
            vm.openRoute = "home"
            if (!(vm.lockEnabled && vm.locked)) vm.startSession()
        }
    }

    override fun onStop() {
        super.onStop()
        AppVisibility.inForeground.value = false
        stoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.inForeground.value = true
        if (vm.lockEnabled && stoppedAt > 0 && SystemClock.elapsedRealtime() - stoppedAt > 30_000L) vm.locked = true
    }
}

private data class Tab(val route: String, val label: String, val icon: String, val color: androidx.compose.ui.graphics.Color, val shape: androidx.compose.ui.graphics.Shape)

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
    val vm = (LocalContext.current.applicationContext as YesminApp).vm
    val dark = when (vm.theme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    var dismissed by remember { mutableStateOf(false) }
    YesminTheme(dark) {
        Box(Modifier.fillMaxSize()) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(Modifier.fillMaxSize().statusBarsPadding()) {
                    if (vm.lockEnabled && vm.locked) LockScreen(vm)
                    else if (vm.onboardingDone) MainScaffold(vm)
                    else OnboardingScreen(vm)
                }
            }
            EdgeGlow(vm)
        }
        if (vm.onboardingDone && !vm.disclosure && !dismissed && !(vm.lockEnabled && vm.locked)) {
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
    LaunchedEffect(vm.openRoute) {
        vm.openRoute?.let { r ->
            navTab(nav, r)
            vm.openRoute = null
        }
    }
    LaunchedEffect(vm.notice) {
        if (vm.notice.isNotBlank()) {
            snack.showSnackbar(vm.notice)
            vm.notice = ""
        }
    }
    val tabs = listOf(
        Tab("home", "Home", "🏠", androidx.compose.ui.graphics.Color(0xFF22D3EE), androidx.compose.foundation.shape.CircleShape),
        Tab("chat", "Chat", "💬", androidx.compose.ui.graphics.Color(0xFF8B5CF6), androidx.compose.foundation.shape.RoundedCornerShape(10.dp)),
        Tab("camera", "Camera", "📷", androidx.compose.ui.graphics.Color(0xFFF472B6), androidx.compose.foundation.shape.CutCornerShape(9.dp)),
        Tab("brain", "AI Brain", "🧠", androidx.compose.ui.graphics.Color(0xFFF59E0B), androidx.compose.foundation.shape.RoundedCornerShape(topStart = 14.dp, topEnd = 4.dp, bottomEnd = 14.dp, bottomStart = 4.dp)),
        Tab("more", "More", "☰", androidx.compose.ui.graphics.Color(0xFF34D399), androidx.compose.foundation.shape.RoundedCornerShape(percent = 32))
    )
    val moreRoutes = setOf("more", "apps", "calc", "support", "vision")
    Scaffold(
        topBar = { },
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            NavigationBar {
                tabs.forEach { t ->
                    val selected = route == t.route ||
                        (t.route == "more" && (route in moreRoutes || (route?.startsWith("tool/") == true)))
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navTab(nav, t.route) },
                        icon = {
                            Box(
                                Modifier.size(34.dp).clip(t.shape).background(if (selected) t.color else t.color.copy(alpha = 0.22f)),
                                contentAlignment = androidx.compose.ui.Alignment.Center
                            ) { Text(t.icon, fontSize = 16.sp) }
                        },
                        label = { Text(t.label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                        colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                            selectedTextColor = t.color,
                            unselectedTextColor = t.color.copy(alpha = 0.8f),
                            indicatorColor = androidx.compose.ui.graphics.Color.Transparent
                        )
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
            composable("settings") { SettingsScreen(vm, nav) }
            composable("memory") { MemoryScreen(vm) }
            composable("calc") { CalcScreen() }
            composable("device") { DeviceScreen() }
            composable("support") { SupportScreen(vm) }
            composable("profiles") { ProfilesScreen(vm) }
            composable("vision") { VisionScreen(vm) }
            composable("camera") { CameraScreen(vm) }
        }
    }
}

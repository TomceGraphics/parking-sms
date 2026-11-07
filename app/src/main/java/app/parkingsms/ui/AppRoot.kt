package app.parkingsms.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.parkingsms.container
import app.parkingsms.data.AppSettings
import app.parkingsms.data.ThemeMode
import app.parkingsms.ui.dashboard.DashboardScreen
import app.parkingsms.ui.history.HistoryDetailScreen
import app.parkingsms.ui.history.HistoryScreen
import app.parkingsms.ui.onboarding.OnboardingScreen
import app.parkingsms.ui.permissions.PermissionsScreen
import app.parkingsms.ui.settings.ManageCitiesScreen
import app.parkingsms.ui.settings.ManagePlatesScreen
import app.parkingsms.ui.settings.SettingsScreen
import app.parkingsms.ui.theme.ParkingTheme

@Composable
fun AppRoot() {
    val container = LocalContext.current.container
    val settings by container.settingsStore.settings.collectAsStateWithLifecycle<AppSettings?>(initialValue = null)
    val s = settings
    val loaded = s != null
    // Decided once, when settings first arrive.
    val start = remember(loaded) { if (s?.onboardingDone == true) "dashboard" else "onboarding" }

    ParkingTheme(
        themeMode = s?.themeMode ?: ThemeMode.SYSTEM,
        dynamicColor = s?.dynamicColor ?: false
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (!loaded) {
                Box(modifier = Modifier.fillMaxSize())
            } else {
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = start) {
                    composable("onboarding") {
                        OnboardingScreen(onFinished = {
                            nav.navigate("dashboard") { popUpTo("onboarding") { inclusive = true } }
                        })
                    }
                    composable("dashboard") {
                        DashboardScreen(
                            onSettings = { nav.navigate("settings") },
                            onHistory = { nav.navigate("history") },
                            onPermissions = { nav.navigate("permissions") }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            onBack = { nav.popBackStack() },
                            onCities = { nav.navigate("cities") },
                            onPlates = { nav.navigate("plates") },
                            onPermissions = { nav.navigate("permissions") }
                        )
                    }
                    composable("cities") { ManageCitiesScreen(onBack = { nav.popBackStack() }) }
                    composable("plates") { ManagePlatesScreen(onBack = { nav.popBackStack() }) }
                    composable("permissions") { PermissionsScreen(onBack = { nav.popBackStack() }) }
                    composable("history") {
                        HistoryScreen(
                            onBack = { nav.popBackStack() },
                            onOpen = { id -> nav.navigate("history/$id") }
                        )
                    }
                    composable(
                        route = "history/{id}",
                        arguments = listOf(navArgument("id") { type = NavType.LongType })
                    ) { entry ->
                        HistoryDetailScreen(
                            sessionId = entry.arguments?.getLong("id") ?: -1L,
                            onBack = { nav.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}

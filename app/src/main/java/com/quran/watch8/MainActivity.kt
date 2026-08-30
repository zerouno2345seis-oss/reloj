package com.quran.watch8

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.*
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.quran.watch8.ui.screens.*
import com.quran.watch8.ui.theme.AccentGold
import com.quran.watch8.ui.theme.QuranWatchTheme
import com.quran.watch8.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private lateinit var viewModelRef: MainViewModel

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (::viewModelRef.isInitialized) {
            viewModelRef.onPermissionsResult(results)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            QuranWatchTheme {
                val navController = rememberSwipeDismissableNavController()
                val viewModel: MainViewModel = viewModel()
                viewModelRef = viewModel

                var permissionsHandled by remember { mutableStateOf(false) }
                var showPermissionScreen by remember { mutableStateOf(false) }

                // On first launch: request ALL needed permissions automatically
                LaunchedEffect(Unit) {
                    viewModel.loadQuran()
                    viewModel.checkPermissions()

                    val permissions = mutableListOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.RECORD_AUDIO
                    )
                    if (Build.VERSION.SDK_INT >= 33) {
                        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                    }

                    val missing = permissions.filter {
                        ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED
                    }

                    if (missing.isNotEmpty()) {
                        // Request immediately (default for location + audio)
                        showPermissionScreen = true
                        requestPermissionLauncher.launch(missing.toTypedArray())
                    } else {
                        permissionsHandled = true
                        viewModel.fetchCurrentLocation()
                    }
                }

                // After permission dialog closes
                LaunchedEffect(viewModel.hasLocationPermission, viewModel.hasAudioPermission) {
                    if (viewModel.hasLocationPermission || viewModel.hasAudioPermission) {
                        permissionsHandled = true
                        showPermissionScreen = false
                    }
                }

                if (showPermissionScreen && !permissionsHandled) {
                    // Simple permission rationale screen while system dialog is up
                    PermissionRationaleScreen(
                        onContinue = {
                            // User may have denied; still continue with defaults (Buenos Aires)
                            permissionsHandled = true
                            showPermissionScreen = false
                            if (!viewModel.hasLocationPermission) {
                                viewModel.selectPreset(
                                    com.quran.watch8.data.model.ArgentinaLocations.BUENOS_AIRES_CABA
                                )
                            }
                        }
                    )
                } else {
                    SwipeDismissableNavHost(
                        navController = navController,
                        startDestination = "home"
                    ) {
                        composable("home") {
                            HomeScreen(
                                onNavigate = { route -> navController.navigate(route) },
                                viewModel = viewModel
                            )
                        }
                        composable("quran") {
                            QuranScreen(
                                onBack = { navController.popBackStack() },
                                onNavigateToReader = { surah ->
                                    navController.navigate("reader/$surah")
                                },
                                onNavigateToBookmarks = { navController.navigate("bookmarks") },
                                viewModel = viewModel
                            )
                        }
                        composable("reader/{surahNumber}") { backStackEntry ->
                            val surah = backStackEntry.arguments?.getString("surahNumber")?.toIntOrNull() ?: 1
                            QuranReaderScreen(
                                surahNumber = surah,
                                onBack = { navController.popBackStack() },
                                viewModel = viewModel
                            )
                        }
                        composable("bookmarks") {
                            BookmarksScreen(
                                onBack = { navController.popBackStack() },
                                onOpenAyah = { surah, ayah ->
                                    navController.navigate("reader/$surah")
                                },
                                viewModel = viewModel
                            )
                        }
                        composable("prayer") {
                            PrayerTimesScreen(
                                onBack = { navController.popBackStack() },
                                viewModel = viewModel
                            )
                        }
                        composable("locations") {
                            LocationsScreen(
                                onBack = { navController.popBackStack() },
                                viewModel = viewModel
                            )
                        }
                        composable("notes") {
                            VoiceNotesScreen(
                                onBack = { navController.popBackStack() },
                                viewModel = viewModel
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                onBack = { navController.popBackStack() },
                                viewModel = viewModel
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionRationaleScreen(onContinue: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "الأذونات المطلوبة",
                style = MaterialTheme.typography.title3,
                color = AccentGold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "يحتاج التطبيق إلى:\n\n" +
                        "📍 الموقع → أوقات الصلاة وحفظ المواقع\n" +
                        "🎤 الميكروفون → البحث الصوتي والملاحظات\n" +
                        "🔔 الإشعارات → تنبيهات الصلاة\n\n" +
                        "الافتراضي عند الرفض: بوينس آيرس (الأرجنتين)",
                style = MaterialTheme.typography.caption1,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Chip(
                onClick = onContinue,
                label = { Text("متابعة / Continuar") },
                colors = ChipDefaults.primaryChipColors(
                    backgroundColor = AccentGold,
                    contentColor = Color.Black
                )
            )
        }
    }
}

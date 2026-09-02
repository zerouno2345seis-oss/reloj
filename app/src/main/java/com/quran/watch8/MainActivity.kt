package com.quran.watch8

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.navArgument
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
    private var navControllerRef: androidx.navigation.NavHostController? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (::viewModelRef.isInitialized) viewModelRef.onPermissionsResult(results)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handlePresetIntent(intent)
    }

    /**
     * Pull the cloud config every time the app comes to the foreground. Without
     * this the watch only checked once per cold start, so a design synced from
     * the web while the app was already open never arrived. One HTTPS GET on
     * resume costs nothing while the app is closed, unlike background polling.
     */
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val (ok, message) = com.quran.watch8.util.LocalSyncServer.syncWithCloud(applicationContext, "pull")
            if (ok && message.startsWith(com.quran.watch8.util.LocalSyncServer.APPLIED_PREFIX)) {
                Toast.makeText(
                    this@MainActivity,
                    message.removePrefix(com.quran.watch8.util.LocalSyncServer.APPLIED_PREFIX),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    private fun handlePresetIntent(intent: android.content.Intent?) {
        val presetId = intent?.getStringExtra("apply_preset")
        if (!presetId.isNullOrBlank()) {
            com.quran.watch8.data.model.PresetManager.applyPreset(applicationContext, presetId)
            navControllerRef?.popBackStack("watchface", false)
        }
        val modelName = intent?.getStringExtra("set_watchface_model")
        if (!modelName.isNullOrBlank() && ::viewModelRef.isInitialized) {
            try {
                val m = com.quran.watch8.data.model.WatchFaceModelId.valueOf(modelName)
                viewModelRef.setWatchFaceModel(m)
                navControllerRef?.popBackStack("watchface", false)
            } catch (_: Exception) {}
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.quran.watch8.util.LocalSyncServer.start(applicationContext)

        setContent {
            QuranWatchTheme {
                val navController = rememberSwipeDismissableNavController()
                navControllerRef = navController
                val viewModel: MainViewModel = viewModel()
                viewModelRef = viewModel
                LaunchedEffect(Unit) {
                    handlePresetIntent(intent)
                }

                var permissionsHandled by remember { mutableStateOf(false) }
                var showPermissionScreen by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    viewModel.loadQuran()
                    viewModel.checkPermissions()

                    val permissions = mutableListOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.RECORD_AUDIO
                    )
                    if (Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.POST_NOTIFICATIONS)

                    val missing = permissions.filter {
                        ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED
                    }
                    if (missing.isNotEmpty()) {
                        showPermissionScreen = true
                        requestPermissionLauncher.launch(missing.toTypedArray())
                    } else {
                        permissionsHandled = true
                        viewModel.fetchCurrentLocation()
                    }
                }

                LaunchedEffect(viewModel.hasLocationPermission, viewModel.hasAudioPermission) {
                    if (viewModel.hasLocationPermission || viewModel.hasAudioPermission) {
                        permissionsHandled = true
                        showPermissionScreen = false
                    }
                }

                if (showPermissionScreen && !permissionsHandled) {
                    PermissionRationaleScreen(
                        onContinue = {
                            permissionsHandled = true
                            showPermissionScreen = false
                            if (!viewModel.hasLocationPermission) {
                                viewModel.selectPreset(com.quran.watch8.data.model.ArgentinaLocations.BUENOS_AIRES_CABA)
                            }
                        }
                    )
                } else {
                    LaunchedEffect(intent) {
                        val route = intent.getStringExtra("targetRoute")
                        if (!route.isNullOrBlank()) {
                            navController.navigate(route)
                        }
                    }

                    SwipeDismissableNavHost(
                        navController    = navController,
                        startDestination = "watchface"
                    ) {
                        composable("watchface") {
                            WatchFaceHomeScreen(
                                onNavigate = { route -> navController.navigate(route) },
                                onOpenAppDrawer = { navController.navigate("app_drawer") },
                                viewModel = viewModel
                            )
                        }

                        composable("tiles") {
                            HomeScreen(
                                onNavigate = { route -> navController.navigate(route) },
                                viewModel  = viewModel
                            )
                        }

                        composable("home") {
                            HomeScreen(
                                onNavigate = { route -> navController.navigate(route) },
                                viewModel  = viewModel
                            )
                        }

                        composable("app_drawer") {
                            AppDrawerScreen(
                                onBack = { navController.popBackStack() },
                                onExitToWatchFace = { navController.popBackStack("watchface", false) },
                                viewModel = viewModel
                            )
                        }

                        composable("quran") {
                            QuranScreen(
                                onBack               = { navController.popBackStack() },
                                onNavigateToReader   = { surah -> navController.navigate("reader/$surah") },
                                onNavigateToBookmarks = { navController.navigate("bookmarks") },
                                viewModel            = viewModel
                            )
                        }

                        // ── Reader  supports two optional params:
                        //    startAyah  → 1-based ayah number (from search/bookmark)
                        //    listIndex  → raw ScalingLazyColumn index (from resume)
                        composable(
                            route = "reader/{surahNumber}?startAyah={startAyah}&listIndex={listIndex}",
                            arguments = listOf(
                                navArgument("surahNumber") { type = NavType.IntType },
                                navArgument("startAyah")  { type = NavType.IntType; defaultValue = -1 },
                                navArgument("listIndex")  { type = NavType.IntType; defaultValue = -1 }
                            )
                        ) { back ->
                            val surah     = back.arguments?.getInt("surahNumber") ?: 1
                            val startAyah = back.arguments?.getInt("startAyah")   ?: -1
                            val listIndex = back.arguments?.getInt("listIndex")   ?: -1
                            QuranReaderScreen(
                                surahNumber = surah,
                                startAyah   = startAyah,
                                listIndex   = listIndex,
                                onBack      = { navController.popBackStack() },
                                viewModel   = viewModel
                            )
                        }

                        composable("bookmarks") {
                            BookmarksScreen(
                                onBack = { navController.popBackStack() },
                                onOpenAyah = { surah, ayah ->
                                    navController.navigate("reader/$surah?startAyah=$ayah")
                                },
                                viewModel = viewModel
                            )
                        }

                        composable("prayer") {
                            PrayerTimesScreen(onBack = { navController.popBackStack() }, viewModel = viewModel)
                        }
                        composable("qibla") {
                            QiblaCompassScreen(onBack = { navController.popBackStack() }, viewModel = viewModel)
                        }
                        composable("locations") {
                            LocationsScreen(onBack = { navController.popBackStack() }, viewModel = viewModel)
                        }
                        composable("notes") {
                            VoiceNotesScreen(onBack = { navController.popBackStack() }, viewModel = viewModel)
                        }
                        composable("voice_notes") {
                            VoiceNotesScreen(onBack = { navController.popBackStack() }, viewModel = viewModel)
                        }
                        composable("presets") {
                            PresetsScreen(onBack = { navController.popBackStack() }, viewModel = viewModel)
                        }
                        composable("settings") {
                            SettingsScreen(onBack = { navController.popBackStack() }, viewModel = viewModel)
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
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text  = "الأذونات المطلوبة",
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
                       "الافتراضي عند الرفض: بوينس آيرس",
                style = MaterialTheme.typography.caption1,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Chip(
                onClick = onContinue,
                label   = { Text("متابعة / Continuar") },
                colors  = ChipDefaults.primaryChipColors(backgroundColor = AccentGold, contentColor = Color.Black)
            )
        }
    }
}

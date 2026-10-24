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
    // A route asked for by a watch-face complication or the tile.
    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRoute = intent.getStringExtra("targetRoute")
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (::viewModelRef.isInitialized) viewModelRef.onPermissionsResult(results)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingRoute = intent.getStringExtra("targetRoute")
        setContent {
            QuranWatchTheme {
                val navController = rememberSwipeDismissableNavController()
                val viewModel: MainViewModel = viewModel()
                viewModelRef = viewModel
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
                    // Back from the first page leaves the app, to the watch face:
                    // there is no screen of the app's own behind the reader any more.
                    val leave: () -> Unit = { if (!navController.popBackStack()) finish() }

                    SwipeDismissableNavHost(
                        navController    = navController,
                        startDestination = "start"
                    ) {
                        // The way in is the mushaf itself, where reading stopped
                        // (or the surah list the first time). A page asked for by
                        // the face or the tile opens instead.
                        composable("start") {
                            LaunchedEffect(Unit) {
                                if (pendingRoute != null) return@LaunchedEffect
                                navController.navigate(viewModel.resumeRoute()) { popUpTo("start") { inclusive = true } }
                            }
                        }

                        composable("quran") {
                            QuranScreen(
                                onBack               = leave,
                                onNavigateToReader   = { surah -> navController.navigate("reader/$surah") },
                                onNavigateToBookmarks = { navController.navigate("bookmarks") },
                                viewModel            = viewModel,
                                onNavigateToAyah     = { surah, ayah -> navController.navigate("reader/$surah?startAyah=$ayah") }
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
                                onBack      = leave,
                                viewModel   = viewModel,
                                onOpenSettings = { navController.navigate("settings") },
                                onOpenIndex = { navController.navigate("quran") },
                                // Previous/next surah replaces this page rather than
                                // stacking one reader on another for every surah read.
                                onOpenSurah = { next ->
                                    navController.navigate("reader/$next") {
                                        popUpTo(back.destination.id) { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("bookmarks") {
                            BookmarksScreen(
                                onBack = leave,
                                onOpenAyah = { surah, ayah ->
                                    navController.navigate("reader/$surah?startAyah=$ayah")
                                },
                                viewModel = viewModel
                            )
                        }

                        composable("prayer") {
                            PrayerTimesScreen(onBack = leave, viewModel = viewModel)
                        }
                        composable("locations") {
                            LocationsScreen(onBack = { navController.popBackStack() }, viewModel = viewModel)
                        }
                        composable("settings") {
                            SettingsScreen(
                                onBack = leave,
                                viewModel = viewModel,
                                onOpenLocations = { navController.navigate("locations") }
                            )
                        }
                    }

                    // A page asked for by the face or the tile becomes the first
                    // page, so back from it returns to the face.
                    LaunchedEffect(pendingRoute) {
                        // "resume" (the face's mushaf icon and its time): where reading stopped.
                        val route = pendingRoute?.let { if (it == "resume") viewModel.resumeRoute() else it }
                        if (!route.isNullOrBlank()) {
                            navController.navigate(route) { popUpTo(navController.graph.id) { inclusive = true } }
                            pendingRoute = null
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

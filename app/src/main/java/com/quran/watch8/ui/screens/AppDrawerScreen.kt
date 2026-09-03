package com.quran.watch8.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import com.quran.watch8.ui.components.rememberRotaryScrollModifier
import com.quran.watch8.ui.components.WatchSafeInsets
import com.quran.watch8.ui.theme.AccentGold
import com.quran.watch8.ui.theme.AyahYellow
import com.quran.watch8.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledAppItem(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val launchIntent: Intent?
)

/**
 * Process-wide cache of the launcher list. Loading it is dominated by
 * ResolveInfo.loadIcon() per app, which took 1-3s on the watch and re-ran every
 * time the drawer was navigated to (the composable's remember{} is dropped when
 * the NavHost leaves the screen). Now the drawer paints instantly from here and
 * only the very first open pays the cost.
 */
object InstalledAppsCache {
    @Volatile var apps: List<InstalledAppItem>? = null
    @Volatile var iconsLoaded: Boolean = false
}

private fun compactAppName(raw: String): String = when (raw.trim()) {
    "التطبيقات الأخيرة" -> "التطبيقات"
    else -> raw.trim()
}

@Composable
fun AppDrawerScreen(
    onBack: () -> Unit,
    viewModel: MainViewModel,
    onExitToWatchFace: () -> Unit = onBack
) {
    val context = LocalContext.current
    val view = LocalView.current
    val pm = context.packageManager
    val listState = rememberScalingLazyListState()
    val rotaryMod = rememberRotaryScrollModifier(listState)
    val pinnedPackages by viewModel.pinnedApps.collectAsState()
    val viewMode by viewModel.drawerViewMode.collectAsState()
    val recentPackages by viewModel.recentApps.collectAsState()

    var allApps by remember { mutableStateOf(InstalledAppsCache.apps ?: emptyList()) }
    var isLoading by remember { mutableStateOf(InstalledAppsCache.apps == null) }

    fun vibrate() {
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    fun launch(app: InstalledAppItem) {
        app.launchIntent?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(it)
        }
        viewModel.recordAppLaunch(app.packageName)
    }

    LaunchedEffect(Unit) {
        // Already cached with icons: nothing to do, the list is on screen.
        if (InstalledAppsCache.apps != null && InstalledAppsCache.iconsLoaded) return@LaunchedEffect

        withContext(Dispatchers.IO) {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolved = pm.queryIntentActivities(mainIntent, 0)
                .filter { it.activityInfo.packageName != context.packageName }
                .distinctBy { it.activityInfo.packageName }

            // Phase 1 — labels + launch intents only (fast). The list can render.
            if (InstalledAppsCache.apps == null) {
                val fast = resolved
                    .map { ri ->
                        val pkg = ri.activityInfo.packageName
                        InstalledAppItem(pkg, compactAppName(ri.loadLabel(pm).toString()), null, pm.getLaunchIntentForPackage(pkg))
                    }
                    .sortedBy { it.appName }
                InstalledAppsCache.apps = fast
                withContext(Dispatchers.Main) {
                    allApps = fast
                    isLoading = false
                }
            }

            // Phase 2 — decode each launcher icon, then swap in the full list once.
            val withIcons = resolved
                .map { ri ->
                    val pkg = ri.activityInfo.packageName
                    InstalledAppItem(pkg, compactAppName(ri.loadLabel(pm).toString()), ri.loadIcon(pm), pm.getLaunchIntentForPackage(pkg))
                }
                .sortedBy { it.appName }
            InstalledAppsCache.apps = withIcons
            InstalledAppsCache.iconsLoaded = true
            withContext(Dispatchers.Main) {
                allApps = withIcons
                isLoading = false
            }
        }
    }

    val pinnedAppsList = remember(allApps, pinnedPackages) {
        allApps.filter { pinnedPackages.contains(it.packageName) }
    }

    val otherAppsList = remember(allApps, pinnedPackages) {
        allApps.filter { !pinnedPackages.contains(it.packageName) }
    }

    val recentAppsList = remember(allApps, recentPackages) {
        recentPackages.mapNotNull { pkg -> allApps.find { it.packageName == pkg } }
    }

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(indicatorColor = AccentGold)
            }
        } else {
            ScalingLazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .then(rotaryMod),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = WatchSafeInsets.listContentPadding
            ) {
                // ── Top toolbar: exit to the watch face · list/grid toggle ──
                item {
                    Row(
                        modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E293B))
                                .border(1.dp, AccentGold.copy(alpha = 0.7f), CircleShape)
                                .clickable {
                                    vibrate()
                                    onExitToWatchFace()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("⌂", color = AccentGold, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E293B))
                                .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.7f), CircleShape)
                                .clickable {
                                    vibrate()
                                    viewModel.setDrawerViewMode(if (viewMode == "list") "grid" else "list")
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (viewMode == "list") "▦" else "☰",
                                color = Color(0xFF38BDF8),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // ── RECENT APPS (opened from this drawer) ──
                if (recentAppsList.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth(WatchSafeInsets.contentWidthFraction)
                                .padding(vertical = 1.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            recentAppsList.take(3).forEach { app ->
                                AppGridIconItem(
                                    app = app,
                                    isPinned = pinnedPackages.contains(app.packageName),
                                    onLaunch = { launch(app) },
                                    onTogglePin = { vibrate(); viewModel.togglePinnedApp(app.packageName) }
                                )
                            }
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(modifier = Modifier.fillMaxWidth(0.8f).height(0.5.dp).background(Color.DarkGray))
                    }
                }

                // ── 1. PINNED APPS SECTION ──
                if (pinnedAppsList.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("المثبتة", color = AyahYellow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (viewMode == "grid") {
                        // Grid Mode for Pinned
                        val rows = pinnedAppsList.chunked(3)
                        items(rows) { rowApps ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth(WatchSafeInsets.contentWidthFraction)
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                rowApps.forEach { app ->
                                    AppGridIconItem(
                                        app = app,
                                        isPinned = true,
                                        onLaunch = { launch(app) },
                                        onTogglePin = {
                                            vibrate()
                                            viewModel.togglePinnedApp(app.packageName)
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        // List Mode for Pinned
                        items(pinnedAppsList, key = { "pinned_" + it.packageName }) { app ->
                            AppListRowItem(
                                app = app,
                                isPinned = true,
                                onLaunch = { launch(app) },
                                onTogglePin = {
                                    vibrate()
                                    viewModel.togglePinnedApp(app.packageName)
                                }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(modifier = Modifier.fillMaxWidth(0.8f).height(0.5.dp).background(Color.DarkGray))
                    }
                }

                // ── 2. ALL / OTHER APPS SECTION ──
                if (viewMode == "grid") {
                    val rows = otherAppsList.chunked(3)
                    items(rows) { rowApps ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth(WatchSafeInsets.contentWidthFraction)
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            rowApps.forEach { app ->
                                AppGridIconItem(
                                    app = app,
                                    isPinned = false,
                                    onLaunch = { launch(app) },
                                    onTogglePin = {
                                        vibrate()
                                        viewModel.togglePinnedApp(app.packageName)
                                    }
                                )
                            }
                        }
                    }
                } else {
                    items(otherAppsList, key = { it.packageName }) { app ->
                        AppListRowItem(
                            app = app,
                            isPinned = false,
                            onLaunch = { launch(app) },
                            onTogglePin = {
                                vibrate()
                                viewModel.togglePinnedApp(app.packageName)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppListRowItem(
    app: InstalledAppItem,
    isPinned: Boolean,
    onLaunch: () -> Unit,
    onTogglePin: () -> Unit
) {
    Chip(
        onClick = onLaunch,
        icon = {
            val bmp = remember(app.icon) {
                try {
                    app.icon?.toBitmap(width = 44, height = 44)?.asImageBitmap()
                } catch (_: Exception) { null }
            }
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = app.appName,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        label = {
            Text(
                text = app.appName,
                style = MaterialTheme.typography.body2,
                color = Color.White,
                maxLines = 1
            )
        },
        secondaryLabel = {
            Text(
                text = if (isPinned) "⭐ مثبت" else "ضغطة مطولة للتثبيت",
                fontSize = 8.sp,
                color = if (isPinned) AyahYellow else Color.Gray
            )
        },
        colors = ChipDefaults.secondaryChipColors(
            backgroundColor = if (isPinned) Color(0xFF1E293B) else Color(0xFF131A29)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onLaunch() },
                    onLongPress = { onTogglePin() }
                )
            }
    )
}

@Composable
private fun AppGridIconItem(
    app: InstalledAppItem,
    isPinned: Boolean,
    onLaunch: () -> Unit,
    onTogglePin: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(horizontal = 3.dp, vertical = 1.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onLaunch() },
                    onLongPress = { onTogglePin() }
                )
            }
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(if (isPinned) Color(0xFF1E293B) else Color(0xFF111827))
                .border(
                    width = if (isPinned) 1.5.dp else 1.dp,
                    color = if (isPinned) AccentGold else Color(0xFF334155),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            val bmp = remember(app.icon) {
                try {
                    app.icon?.toBitmap(width = 44, height = 44)?.asImageBitmap()
                } catch (_: Exception) { null }
            }
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = app.appName,
                    modifier = Modifier.size(28.dp)
                )
            }
            if (isPinned) {
                Text(
                    text = "⭐",
                    fontSize = 8.sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(1.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = app.appName,
            fontSize = 8.sp,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            lineHeight = 9.sp,
            modifier = Modifier.width(72.dp).heightIn(min = 18.dp)
        )
    }
}

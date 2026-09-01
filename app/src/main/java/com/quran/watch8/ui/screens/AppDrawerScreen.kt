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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import com.quran.watch8.ui.components.rememberRotaryScrollModifier
import com.quran.watch8.ui.theme.AccentGold
import com.quran.watch8.ui.theme.AyahYellow
import com.quran.watch8.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledAppItem(
    val packageName: String,
    val appName: String,
    val icon: Drawable,
    val launchIntent: Intent?
)

@Composable
fun AppDrawerScreen(
    onBack: () -> Unit,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val view = LocalView.current
    val pm = context.packageManager
    val listState = rememberScalingLazyListState()
    val rotaryMod = rememberRotaryScrollModifier(listState)
    val pinnedPackages by viewModel.pinnedApps.collectAsState()
    val viewMode by viewModel.drawerViewMode.collectAsState()

    var allApps by remember { mutableStateOf<List<InstalledAppItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    fun vibrate() {
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolved = pm.queryIntentActivities(mainIntent, 0)
            val appList = resolved
                .mapNotNull { resolveInfo ->
                    val pkg = resolveInfo.activityInfo.packageName
                    if (pkg == context.packageName) return@mapNotNull null
                    val name = resolveInfo.loadLabel(pm).toString()
                    val icon = resolveInfo.loadIcon(pm)
                    val intent = pm.getLaunchIntentForPackage(pkg)
                    InstalledAppItem(pkg, name, icon, intent)
                }
                .sortedBy { it.appName }
            withContext(Dispatchers.Main) {
                allApps = appList
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
                contentPadding = PaddingValues(top = 26.dp, bottom = 36.dp, start = 8.dp, end = 8.dp)
            ) {
                // ── Top: Single Icon Button for Mode Switch (List vs Grid) ──
                item {
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp, bottom = 6.dp)
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
                            Text("⭐ المثبتة", color = AyahYellow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (viewMode == "grid") {
                        // Grid Mode for Pinned
                        val rows = pinnedAppsList.chunked(3)
                        items(rows) { rowApps ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                rowApps.forEach { app ->
                                    AppGridIconItem(
                                        app = app,
                                        isPinned = true,
                                        onLaunch = {
                                            app.launchIntent?.let {
                                                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                context.startActivity(it)
                                            }
                                        },
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
                                onLaunch = {
                                    app.launchIntent?.let {
                                        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(it)
                                    }
                                },
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
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            rowApps.forEach { app ->
                                AppGridIconItem(
                                    app = app,
                                    isPinned = false,
                                    onLaunch = {
                                        app.launchIntent?.let {
                                            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            context.startActivity(it)
                                        }
                                    },
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
                            onLaunch = {
                                app.launchIntent?.let {
                                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(it)
                                }
                            },
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
                    app.icon.toBitmap(width = 44, height = 44).asImageBitmap()
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
            .padding(4.dp)
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
                    app.icon.toBitmap(width = 44, height = 44).asImageBitmap()
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
            fontSize = 9.sp,
            color = Color.White,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(48.dp)
        )
    }
}

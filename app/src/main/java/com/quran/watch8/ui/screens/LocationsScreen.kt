package com.quran.watch8.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import com.quran.watch8.data.model.LocationType
import com.quran.watch8.data.model.SavedLocation
import com.quran.watch8.ui.components.WatchIcons
import com.quran.watch8.ui.components.rememberRotaryScrollModifier
import com.quran.watch8.ui.theme.AccentGold
import com.quran.watch8.ui.theme.AyahYellow
import com.quran.watch8.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun LocationsScreen(
    onBack: () -> Unit,
    viewModel: MainViewModel
) {
    val listState = rememberScalingLazyListState()
    val rotaryMod = rememberRotaryScrollModifier(listState)
    val locations by viewModel.locations.collectAsState()
    val isLoading by remember { derivedStateOf { viewModel.isLoadingLocation } }
    val currentLoc by remember { derivedStateOf { viewModel.currentLocation } }
    val context = LocalContext.current
    val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale("ar"))

    // Location detail modal state
    var selectedLocForAction by remember { mutableStateOf<SavedLocation?>(null) }
    var showRenamePicker by remember { mutableStateOf(false) }

    // Voice recognition for renaming
    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val newName = matches?.firstOrNull()?.trim()
            if (!newName.isNullOrBlank() && selectedLocForAction != null) {
                viewModel.updateLocationName(selectedLocForAction!!.id, newName)
                selectedLocForAction = null
                showRenamePicker = false
            }
        }
    }

    LaunchedEffect(Unit) {
        if (currentLoc == null) viewModel.fetchCurrentLocation()
    }

    Scaffold(
        timeText = { if (selectedLocForAction == null) TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            ScalingLazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (selectedLocForAction == null) rotaryMod else Modifier),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(top = 28.dp, bottom = 48.dp, start = 8.dp, end = 8.dp)
            ) {
                item {
                    Text(
                        text = "المواقع المحفوظة",
                        style = MaterialTheme.typography.title3,
                        color = AccentGold,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }

                // Quick Save Buttons
                item {
                    Text(
                        text = if (currentLoc != null) "📍 جاهز للحفظ (GPS)" else if (isLoading) "جاري تحديد الموقع..." else "اضغط للحفظ فوراً",
                        style = MaterialTheme.typography.caption2,
                        color = if (currentLoc != null) AyahYellow else Color.Gray,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(0.96f),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        LumiaSaveButton("🚗 سيارتي", Color(0xFF14532D), Modifier.weight(1f)) {
                            viewModel.fetchCurrentLocation()
                            viewModel.saveLocation("موقع السيارة", LocationType.CAR)
                        }
                        LumiaSaveButton("⭐ مهم", Color(0xFF581C87), Modifier.weight(1f)) {
                            viewModel.fetchCurrentLocation()
                            viewModel.saveLocation("موقع مهم", LocationType.IMPORTANT)
                        }
                        LumiaSaveButton("🕌 مسجد", Color(0xFF0F766E), Modifier.weight(1f)) {
                            viewModel.fetchCurrentLocation()
                            viewModel.saveLocation("مسجد", LocationType.MOSQUE)
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "قائمة المواقع (${locations.size})",
                        style = MaterialTheme.typography.caption1,
                        color = Color.Gray
                    )
                }

                if (locations.isEmpty()) {
                    item {
                        Text(
                            text = "لا توجد مواقع بعد\nاضغط على أي زر أعلاه للحفظ",
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    items(locations.sortedByDescending { it.timestamp }) { loc ->
                        val icon = when (loc.type) {
                            LocationType.CAR -> "🚗"
                            LocationType.MOSQUE -> "🕌"
                            LocationType.IMPORTANT -> "⭐"
                            else -> "📍"
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.96f)
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E293B))
                                .clickable {
                                    selectedLocForAction = loc
                                    showRenamePicker = false
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "$icon ${loc.name}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "⚙️",
                                        fontSize = 12.sp,
                                        color = AyahYellow
                                    )
                                }
                                if (loc.address.isNotBlank()) {
                                    Text(
                                        text = "🏠 ${loc.address}",
                                        fontSize = 10.sp,
                                        color = Color(0xFF94A3B8),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Scrollable Location Action Modal (3 Icons Only: Navigation, Edit, Delete) ──
            AnimatedVisibility(
                visible = selectedLocForAction != null,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                if (selectedLocForAction != null) {
                    val loc = selectedLocForAction!!
                    val modalScrollState = rememberScrollState()

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.94f))
                            .clickable { selectedLocForAction = null },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(modalScrollState)
                                .padding(horizontal = 14.dp, vertical = 26.dp)
                                .clickable(enabled = false) {}
                        ) {
                            // Title & Address
                            Text(
                                text = "📍 ${loc.name}",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                            if (loc.address.isNotBlank()) {
                                Text(
                                    text = loc.address,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 10.5.sp,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                                )
                            } else {
                                Spacer(modifier = Modifier.height(10.dp))
                            }

                            if (!showRenamePicker) {
                                // ── 3 Action Icons (No Text Underneath) ─────────────
                                Row(
                                    modifier = Modifier.fillMaxWidth(0.92f),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 1. Navigation Icon (Blue Circle)
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF0284C7))
                                            .clickable {
                                                openGoogleMapsNavigation(context, loc.latitude, loc.longitude, loc.name)
                                                selectedLocForAction = null
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        WatchIcons.NavigationArrow(
                                            modifier = Modifier.size(24.dp),
                                            color = Color.White
                                        )
                                    }

                                    // 2. Edit Icon (Amber Circle)
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFD97706))
                                            .clickable { showRenamePicker = true },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        WatchIcons.EditPencil(
                                            modifier = Modifier.size(24.dp),
                                            color = Color.White
                                        )
                                    }

                                    // 3. Delete Icon (Red Circle)
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFDC2626))
                                            .clickable {
                                                viewModel.removeLocation(loc.id)
                                                selectedLocForAction = null
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        WatchIcons.DeleteTrash(
                                            modifier = Modifier.size(24.dp),
                                            color = Color.White
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "انقر خارج الصندوق للإغلاق",
                                    color = Color.Gray,
                                    fontSize = 9.sp
                                )
                            } else {
                                // ── Rename Mode (Voice or Quick Presets) ────────────
                                Text("اختر اسماً أو تحدّث به:", color = AyahYellow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(6.dp))

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.9f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF78350F))
                                        .clickable {
                                            speechLauncher.launch(
                                                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar")
                                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "انطق اسم الموقع...")
                                                }
                                            )
                                        }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("🎤 انطق الاسم بالصوت", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(0.9f),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf("المنزل", "العمل", "موقفي").forEach { preset ->
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFF334155))
                                                .clickable {
                                                    viewModel.updateLocationName(loc.id, preset)
                                                    selectedLocForAction = null
                                                    showRenamePicker = false
                                                }
                                                .padding(vertical = 5.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(preset, color = Color.White, fontSize = 10.sp, textAlign = TextAlign.Center)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LumiaSaveButton(label: String, bgColor: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

private fun openGoogleMapsNavigation(
    context: Context,
    latitude: Double,
    longitude: Double,
    label: String
) {
    val navUri = Uri.parse("google.navigation:q=$latitude,$longitude")
    val navIntent = Intent(Intent.ACTION_VIEW, navUri).apply {
        setPackage("com.google.android.apps.maps")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    try {
        context.startActivity(navIntent)
    } catch (_: Exception) {
        val webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$latitude,$longitude")
        val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { context.startActivity(webIntent) }
    }
}

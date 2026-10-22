package com.quran.watch8.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.quran.watch8.ui.components.rememberRotaryScrollModifier
import com.quran.watch8.ui.components.ReaderTypography
import com.quran.watch8.ui.components.UpdateChip
import com.quran.watch8.ui.components.WatchSafeInsets
import com.quran.watch8.ui.theme.AccentGold
import com.quran.watch8.ui.theme.AyahGreen
import com.quran.watch8.ui.theme.AyahYellow
import com.quran.watch8.ui.viewmodel.MainViewModel

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val listState = rememberScalingLazyListState()
    val rotaryMod = rememberRotaryScrollModifier(listState)
    val fontSize by viewModel.fontSize.collectAsState()
    val ayahColor by viewModel.ayahColor.collectAsState()
    val readerBgColor by viewModel.readerBgColor.collectAsState()
    val readerTextColor by viewModel.readerTextColor.collectAsState()
    val customAyahColor by viewModel.customAyahColor.collectAsState()
    val customReaderBgColor by viewModel.customReaderBgColor.collectAsState()
    val customReaderTextColor by viewModel.customReaderTextColor.collectAsState()
    val fontFamily by viewModel.fontFamily.collectAsState()
    val notifications by viewModel.notificationsEnabled.collectAsState()
    // Only choices that draw differently. The old list offered nine names, most
    // of which fell back to the same system font. System fonts lack some
    // Uthmani marks, so the bundled Quran font is the default.
    val readerFontOptions = listOf(
        "default" to "المدينة", "amiri" to "الأميري", "sansserif" to "بسيط", "serif" to "تقليدي"
    )


    fun vibrateShort() {
        try {
            val v = context.getSystemService(android.os.Vibrator::class.java)
            v?.vibrate(android.os.VibrationEffect.createOneShot(25, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {}
    }

    Scaffold(
        timeText = {},
        vignette = {},
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .then(rotaryMod),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = WatchSafeInsets.listContentPadding
        ) {
            item {
                UpdateChip(modifier = Modifier.fillMaxWidth(WatchSafeInsets.contentWidthFraction))
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "⚙️ إعدادات عامة",
                    style = MaterialTheme.typography.caption1,
                    color = AccentGold,
                    fontWeight = FontWeight.Bold
                )
            }

            // A size and a face are only judgeable against real text, so the
            // page itself is shown here -- same font, same colours, same
            // stored values the reader uses. Changing either here or in the
            // reader moves this sample and the page together.
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(WatchSafeInsets.contentWidthFraction)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ReaderTypography.backgroundColor(readerBgColor, customReaderBgColor))
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = ReaderTypography.SAMPLE_AYAH,
                        color = ReaderTypography.textColor(readerTextColor, customReaderTextColor),
                        fontFamily = ReaderTypography.fontFamily(fontFamily),
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize * ReaderTypography.LINE_HEIGHT_FACTOR).sp,
                        textAlign = TextAlign.Center,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text("حجم خط القرآن", style = MaterialTheme.typography.caption2, color = Color.Gray)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CompactChip(
                        onClick = { viewModel.setFontSize(ReaderTypography.coerceFontSize(fontSize - 2f)) },
                        label = { Text("−") }
                    )
                    Text("${fontSize.toInt()} sp", color = AyahYellow, fontWeight = FontWeight.Bold)
                    CompactChip(
                        onClick = { viewModel.setFontSize(ReaderTypography.coerceFontSize(fontSize + 2f)) },
                        label = { Text("+") }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text("خط القراءة", style = MaterialTheme.typography.caption2, color = Color.Gray)
            }

            items(readerFontOptions.chunked(2)) { fontRow ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    fontRow.forEach { (id, label) ->
                        CompactChip(
                            onClick = { viewModel.setFontFamily(id) },
                            label = { Text(label, fontSize = 8.sp, maxLines = 1) },
                            colors = ChipDefaults.chipColors(
                                backgroundColor = if (fontFamily == id || (id == "default" && fontFamily !in readerFontOptions.map { it.first })) Color(0xFF0D9488) else Color(0xFF222222),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text("لون أرقام الآيات", style = MaterialTheme.typography.caption2, color = Color.Gray)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Chip(
                        onClick = { viewModel.setAyahColor("yellow") },
                        label = { Text("أصفر 🟡", color = if (ayahColor == "yellow") Color.Black else Color.White, fontSize = 11.sp) },
                        colors = ChipDefaults.primaryChipColors(
                            backgroundColor = if (ayahColor == "yellow") AyahYellow else Color(0xFF222222)
                        ),
                        modifier = Modifier.weight(1f).padding(end = 4.dp)
                    )
                    Chip(
                        onClick = { viewModel.setAyahColor("green") },
                        label = { Text("أخضر 🟢", color = if (ayahColor == "green") Color.Black else Color.White, fontSize = 11.sp) },
                        colors = ChipDefaults.primaryChipColors(
                            backgroundColor = if (ayahColor == "green") AyahGreen else Color(0xFF222222)
                        ),
                        modifier = Modifier.weight(1f).padding(start = 4.dp)
                    )
                }
            }

            item {
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CompactChip(onClick = { viewModel.setAyahColor("cyan") }, label = { Text("سماوي", fontSize = 9.sp) }, modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(4.dp))
                    CompactChip(onClick = { viewModel.setAyahColor("rose") }, label = { Text("وردي", fontSize = 9.sp) }, modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(4.dp))
                    CompactChip(
                        onClick = { viewModel.setAyahColor("custom") },
                        label = { Text("مخصص", fontSize = 9.sp, color = parseHexColor(customAyahColor, Color.White)) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text("خلفية القارئ", style = MaterialTheme.typography.caption2, color = Color.Gray)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("black" to "أسود", "navy" to "كحلي", "sepia" to "ورقي", "forest" to "أخضر", "slate" to "رمادي").forEach { (id, label) ->
                        CompactChip(
                            onClick = { viewModel.setReaderBgColor(id) },
                            label = { Text(label, fontSize = 7.sp) },
                            colors = ChipDefaults.chipColors(backgroundColor = if (readerBgColor == id) Color(0xFF0D9488) else Color(0xFF222222)),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                CompactChip(
                    onClick = { viewModel.setReaderBgColor("custom") },
                    label = { Text("اللون المخصص من المصمم", fontSize = 8.sp) },
                    colors = ChipDefaults.chipColors(backgroundColor = parseHexColor(customReaderBgColor, Color(0xFF222222))),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text("لون متن الآية", style = MaterialTheme.typography.caption2, color = Color.Gray)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("white" to "أبيض", "ivory" to "عاجي", "mint" to "نعناع", "golden" to "ذهبي", "cyan" to "سماوي").forEach { (id, label) ->
                        CompactChip(
                            onClick = { viewModel.setReaderTextColor(id) },
                            label = { Text(label, fontSize = 7.sp) },
                            colors = ChipDefaults.chipColors(backgroundColor = if (readerTextColor == id) Color(0xFF0D9488) else Color(0xFF222222)),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                CompactChip(
                    onClick = { viewModel.setReaderTextColor("custom") },
                    label = { Text("اللون المخصص من المصمم", fontSize = 8.sp, color = parseHexColor(customReaderTextColor, Color.White)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                ToggleChip(
                    checked = notifications,
                    onCheckedChange = { viewModel.setNotifications(it) },
                    label = { Text("تنبيهات الأذان", fontSize = 11.sp) },
                    toggleControl = {
                        Switch(
                            checked = notifications,
                            onCheckedChange = { viewModel.setNotifications(it) }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

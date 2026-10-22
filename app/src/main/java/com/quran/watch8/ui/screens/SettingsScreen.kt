package com.quran.watch8.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import com.quran.watch8.ui.components.ReaderTypography
import com.quran.watch8.ui.components.UpdateChip
import com.quran.watch8.ui.components.WatchSafeInsets
import com.quran.watch8.ui.components.rememberRotaryScrollModifier
import com.quran.watch8.ui.theme.AccentGold
import com.quran.watch8.ui.viewmodel.MainViewModel
import com.quran.watch8.util.PrayerTimesHelper

/** A reading theme: one tap sets the page's background and text together. */
private data class ReadingTheme(val bg: String, val text: String)

// Three rows of tiny chips (7-8 sp, unreadable on the watch) became four
// ready pairs. The ids are the ones ReaderTypography already understands.
private val readingThemes = listOf(
    ReadingTheme("black", "white"),
    ReadingTheme("sepia", "ivory"),
    ReadingTheme("forest", "mint"),
    ReadingTheme("navy", "cyan"),
)

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: MainViewModel,
    onOpenLocations: () -> Unit = {}
) {
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
    val locationName by viewModel.selectedLocationName.collectAsState()
    val method by viewModel.calculationMethod.collectAsState()

    val pageBg = ReaderTypography.backgroundColor(readerBgColor, customReaderBgColor)
    val pageText = ReaderTypography.textColor(readerTextColor, customReaderTextColor)
    val numberColor = ReaderTypography.ayahNumberColor(ayahColor, customAyahColor)
    val width = Modifier.fillMaxWidth(WatchSafeInsets.contentWidthFraction)

    Scaffold(
        timeText = {},
        vignette = {},
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().then(rotaryMod),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = WatchSafeInsets.listContentPadding
        ) {
            item {
                Text(
                    text = "الإعدادات",
                    color = AccentGold,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // The page itself, in the stored font, size and colours, so every
            // choice below is judged against real text before reading.
            item {
                Box(
                    modifier = width
                        .clip(RoundedCornerShape(14.dp))
                        .background(pageBg)
                        .border(1.dp, Color(0xFF1F2937), RoundedCornerShape(14.dp))
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Closed the way the reader closes a verse.
                    Text(
                        text = buildAnnotatedString {
                            append(ReaderTypography.SAMPLE_AYAH)
                            append('\u00A0')
                            withStyle(SpanStyle(color = numberColor, fontFamily = ReaderTypography.QuranFont)) {
                                append(ReaderTypography.ayahEndMark(82))
                            }
                        },
                        color = pageText,
                        fontFamily = ReaderTypography.fontFamily(fontFamily),
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize * ReaderTypography.LINE_HEIGHT_FACTOR).sp,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Size: two big targets and the number between them.
            item {
                Row(
                    modifier = width
                        .clip(RoundedCornerShape(30.dp))
                        .background(Color(0xFF151922))
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RoundButton("+") { viewModel.setFontSize(ReaderTypography.coerceFontSize(fontSize + 2f)) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(ReaderTypography.arabicDigits(fontSize.toInt()), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("حجم الخط", color = Color(0xFF9CA3AF), fontSize = 10.sp)
                    }
                    RoundButton("−") { viewModel.setFontSize(ReaderTypography.coerceFontSize(fontSize - 2f)) }
                }
            }

            // Face: one of two. "default" is the Madinah mushaf face.
            item {
                Row(
                    modifier = width
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF151922))
                        .padding(3.dp)
                ) {
                    val madinah = fontFamily != "amiri"
                    Segment("المدينة", selected = madinah, modifier = Modifier.weight(1f)) { viewModel.setFontFamily("default") }
                    Segment("الأميري", selected = !madinah, modifier = Modifier.weight(1f)) { viewModel.setFontFamily("amiri") }
                }
            }

            item {
                Text("ألوان القراءة", color = Color(0xFF9CA3AF), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    readingThemes.forEach { theme ->
                        val selected = readerBgColor == theme.bg && readerTextColor == theme.text
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(ReaderTypography.backgroundColor(theme.bg, ""))
                                .border(
                                    BorderStroke(if (selected) 2.dp else 1.dp, if (selected) AccentGold else Color(0xFF374151)),
                                    CircleShape
                                )
                                .clickable {
                                    viewModel.setReaderBgColor(theme.bg)
                                    viewModel.setReaderTextColor(theme.text)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "ق",
                                color = ReaderTypography.textColor(theme.text, ""),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            item {
                ToggleChip(
                    checked = notifications,
                    onCheckedChange = { viewModel.setNotifications(it) },
                    label = { Text("تنبيهات الأذان", fontSize = 13.sp) },
                    toggleControl = { Switch(checked = notifications, onCheckedChange = null) },
                    modifier = width.padding(top = 6.dp)
                )
            }

            item {
                Chip(
                    onClick = onOpenLocations,
                    label = { Text("الموقع", fontSize = 13.sp) },
                    secondaryLabel = { Text(locationName, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = width
                )
            }

            // Read-only: the times follow IslamicFinder.
            item {
                Chip(
                    onClick = {},
                    enabled = false,
                    label = { Text("طريقة الحساب", fontSize = 13.sp) },
                    secondaryLabel = {
                        Text("${PrayerTimesHelper.methodDisplayName(method)} · IslamicFinder", fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = width
                )
            }

            item {
                UpdateChip(modifier = width.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun RoundButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color(0xFF273142))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Segment(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Color(0xFF0F766E) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) Color.White else Color(0xFFCBD5E1),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

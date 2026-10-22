package com.quran.watch8.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Text
import com.quran.watch8.util.SelfUpdater
import kotlinx.coroutines.launch

/**
 * "Check for update": one tap checks GitHub, a second downloads and hands the
 * new build to the system installer, which asks to confirm.
 */
@Composable
fun UpdateChip(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val installed = remember { SelfUpdater.installedVersionCode(context) }
    var status by remember { mutableStateOf("النسخة الحالية $installed") }
    var available by remember { mutableStateOf<SelfUpdater.Check.Available?>(null) }
    var busy by remember { mutableStateOf(false) }

    Chip(
        onClick = {
            if (busy) return@Chip
            busy = true
            scope.launch {
                val update = available
                if (update == null) {
                    status = "جارٍ التحقق…"
                    when (val result = SelfUpdater.check(context)) {
                        is SelfUpdater.Check.Available -> {
                            available = result
                            status = "نسخة ${result.versionCode} متاحة — اضغط للتثبيت"
                        }
                        SelfUpdater.Check.UpToDate -> status = "لديك آخر نسخة ($installed)"
                        is SelfUpdater.Check.Failed -> status = "تعذّر التحقق: ${result.reason}"
                    }
                } else {
                    try {
                        val apk = SelfUpdater.download(context, update.apkUrl) { p ->
                            status = "جارٍ التنزيل ${(p * 100).toInt()}٪"
                        }
                        status = "أكّد التثبيت على الشاشة"
                        SelfUpdater.install(context, apk)
                    } catch (e: Exception) {
                        status = "تعذّر التنزيل: ${e.message ?: ""}"
                    }
                }
                busy = false
            }
        },
        label = { Text("تحقّق من التحديث", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
        secondaryLabel = { Text(status, fontSize = 10.sp, maxLines = 2) },
        colors = ChipDefaults.primaryChipColors(backgroundColor = Color(0xFF0F766E)),
        modifier = modifier.fillMaxWidth().padding(bottom = 6.dp)
    )
}

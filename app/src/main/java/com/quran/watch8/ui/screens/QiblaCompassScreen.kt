package com.quran.watch8.ui.screens

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Text
import com.quran.watch8.ui.theme.AccentGold
import com.quran.watch8.ui.viewmodel.MainViewModel
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private const val KAABA_LATITUDE = 21.422487
private const val KAABA_LONGITUDE = 39.826206

private fun qiblaBearing(latitude: Double, longitude: Double): Float {
    val userLat = Math.toRadians(latitude)
    val kaabaLat = Math.toRadians(KAABA_LATITUDE)
    val longitudeDelta = Math.toRadians(KAABA_LONGITUDE - longitude)
    val y = sin(longitudeDelta) * cos(kaabaLat)
    val x = cos(userLat) * sin(kaabaLat) - sin(userLat) * cos(kaabaLat) * cos(longitudeDelta)
    return ((atan2(y, x) * 180.0 / PI + 360.0) % 360.0).toFloat()
}

private fun normalizedRotation(degrees: Float): Float = ((degrees + 540f) % 360f) - 180f

@Composable
fun QiblaCompassScreen(onBack: () -> Unit, viewModel: MainViewModel) {
    val context = LocalContext.current
    val latitude by viewModel.selectedLat.collectAsState()
    val longitude by viewModel.selectedLng.collectAsState()
    val bearing = remember(latitude, longitude) { qiblaBearing(latitude, longitude) }
    var heading by remember { mutableFloatStateOf(0f) }

    val sensorManager = remember {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    val rotationSensor = remember(sensorManager) {
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    }

    DisposableEffect(sensorManager, rotationSensor) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val rotation = FloatArray(9)
                val orientation = FloatArray(3)
                SensorManager.getRotationMatrixFromVector(rotation, event.values)
                SensorManager.getOrientation(rotation, orientation)
                heading = ((Math.toDegrees(orientation[0].toDouble()).toFloat() + 360f) % 360f)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        rotationSensor?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose { sensorManager.unregisterListener(listener) }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().clip(CircleShape).padding(horizontal = 22.dp, vertical = 12.dp)
        ) {
            Text("اتجاه القبلة", color = AccentGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Box(
                modifier = Modifier.size(92.dp).border(2.dp, Color.White.copy(alpha = 0.28f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "▲",
                    color = AccentGold,
                    fontSize = 48.sp,
                    modifier = Modifier.graphicsLayer(rotationZ = normalizedRotation(bearing - heading))
                )
            }
            Text(
                text = if (rotationSensor == null) "الاتجاه التقريبي ${bearing.toInt()}°" else "القبلة ${bearing.toInt()}° · الجهاز ${heading.toInt()}°",
                color = Color.White,
                fontSize = 9.sp,
                textAlign = TextAlign.Center
            )
            Chip(
                onClick = onBack,
                label = { Text("رجوع", fontSize = 9.sp) },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.height(28.dp)
            )
        }
    }
}

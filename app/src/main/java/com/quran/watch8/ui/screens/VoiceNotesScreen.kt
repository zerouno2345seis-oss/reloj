package com.quran.watch8.ui.screens

import android.app.Activity
import android.content.Intent
import android.media.MediaPlayer
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import com.quran.watch8.data.model.VoiceNote
import com.quran.watch8.ui.components.WatchIcons
import com.quran.watch8.ui.components.rememberRotaryScrollModifier
import com.quran.watch8.ui.theme.AccentGold
import com.quran.watch8.ui.theme.AyahYellow
import com.quran.watch8.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

/**
 * Unified Voice Notes Studio Screen for Galaxy Watch
 *
 * Workflow:
 *  1. Single Record Button.
 *  2. Tap Record -> Starts audio recording.
 *  3. Tap Stop & Save -> Saves audio file immediately.
 *  4. Automatically launches Speech-to-Text Transcription.
 *  5. Saves transcription text associated with the audio note.
 *  6. If transcription fails, audio note is never lost and status indicates "Transcription pending (Tap to retry)".
 */
@Composable
fun VoiceNotesScreen(
    onBack: () -> Unit,
    viewModel: MainViewModel
) {
    val listState  = rememberScalingLazyListState()
    val rotaryMod  = rememberRotaryScrollModifier(listState)
    val notes      by viewModel.voiceNotes.collectAsState()
    val isRecording by remember { derivedStateOf { viewModel.isRecording } }
    val context    = LocalContext.current
    val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale("ar"))

    var recordSeconds by remember { mutableStateOf(0) }
    var currentPendingNoteId by remember { mutableStateOf<String?>(null) }
    var playingNoteId by remember { mutableStateOf<String?>(null) }
    val mediaPlayerRef = remember { mutableStateOf<MediaPlayer?>(null) }

    // Speech-to-Text Transcription Launcher
    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val transcribed = matches?.firstOrNull()?.trim()
            if (!transcribed.isNullOrBlank() && currentPendingNoteId != null) {
                viewModel.updateVoiceNoteTranscription(currentPendingNoteId!!, transcribed)
            }
        }
        currentPendingNoteId = null
    }

    // Recording duration timer
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordSeconds = 0
            while (isRecording) {
                delay(1000L)
                recordSeconds++
            }
        } else {
            recordSeconds = 0
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayerRef.value?.apply { runCatching { stop() }; release() }
            mediaPlayerRef.value = null
        }
    }

    fun playNote(noteId: String, filePath: String) {
        mediaPlayerRef.value?.apply { runCatching { stop() }; release() }
        mediaPlayerRef.value = null

        if (playingNoteId == noteId) {
            playingNoteId = null
            return
        }

        runCatching {
            val mp = MediaPlayer().apply {
                setDataSource(filePath)
                setOnCompletionListener {
                    playingNoteId = null
                    it.release()
                    mediaPlayerRef.value = null
                }
                setOnErrorListener { _, _, _ ->
                    playingNoteId = null
                    mediaPlayerRef.value = null
                    false
                }
                prepare()
                start()
            }
            mediaPlayerRef.value = mp
            playingNoteId = noteId
        }.onFailure {
            playingNoteId = null
        }
    }

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            state    = listState,
            modifier = Modifier.fillMaxSize().background(Color.Black).then(rotaryMod),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(top = 28.dp, bottom = 42.dp, start = 8.dp, end = 8.dp)
        ) {
            item {
                Text(
                    text  = "🎤 استوديو الصوتيات",
                    style = MaterialTheme.typography.title3,
                    color = AccentGold,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            // ── Unified Record & Auto-Transcribe Button ───────────────────────
            item {
                Spacer(modifier = Modifier.height(4.dp))
                if (!isRecording) {
                    // Single Idle Button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.94f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFDC2626))
                            .clickable { viewModel.startRecording(context) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            WatchIcons.MicRecording(
                                modifier = Modifier.size(20.dp),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "بدء تسجيل صوتي جديد",
                                fontSize = 12.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    // Active Recording Console
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(0.94f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1E293B))
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFDC2626))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val mins = String.format("%02d", recordSeconds / 60)
                            val secs = String.format("%02d", recordSeconds % 60)
                            Text("جاري التسجيل: $mins:$secs", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Stop, Save & Auto-Transcribe Button
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0F766E))
                                .clickable {
                                    val title = "تسجيل " + SimpleDateFormat("HH:mm", Locale("ar")).format(Date())
                                    viewModel.stopRecording(title = title)
                                }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("⏹️ إيقاف وحفظ", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ── Saved Notes List ──────────────────────────────────────────────
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text  = "الملاحظات المحفوظة (${notes.size})",
                    style = MaterialTheme.typography.caption1,
                    color = Color.Gray
                )
            }

            if (notes.isEmpty()) {
                item {
                    Text(
                        text = "لا توجد تسجيلات بعد\nاضغط على الزر أعلاه للتسجيل",
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp),
                        fontSize = 11.sp
                    )
                }
            } else {
                items(notes.sortedByDescending { it.timestamp }) { note ->
                    val isPlaying = playingNoteId == note.id
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.96f)
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E293B))
                            .padding(8.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🎤 ${note.title}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = dateFormat.format(Date(note.timestamp)),
                                    fontSize = 9.sp,
                                    color = Color.Gray
                                )
                            }

                            // Transcription text (or retry button if empty)
                            if (!note.transcription.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "📝 ${note.transcription}",
                                    fontSize = 11.sp,
                                    color = Color(0xFFA7F3D0),
                                    lineHeight = 14.sp
                                )
                            } else {
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable {
                                        currentPendingNoteId = note.id
                                        speechLauncher.launch(
                                            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar")
                                                putExtra(RecognizerIntent.EXTRA_PROMPT, "أضف عنواناً أو نصاً للملاحظة...")
                                            }
                                        )
                                    }
                                ) {
                                    Text(
                                        text = "➕ إضافة عنوان أو ملخص (انقر للتحدث)",
                                        fontSize = 9.5.sp,
                                        color = AyahYellow
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Action buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (note.filePath.isNotBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isPlaying) Color(0xFF0F766E) else Color(0xFF0284C7))
                                            .clickable { playNote(note.id, note.filePath) }
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (isPlaying) "⏹️ إيقاف" else "▶ تشغيل",
                                            fontSize = 10.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF991B1B))
                                        .clickable {
                                            if (playingNoteId == note.id) {
                                                mediaPlayerRef.value?.apply { runCatching { stop() }; release() }
                                                mediaPlayerRef.value = null
                                                playingNoteId = null
                                            }
                                            viewModel.removeVoiceNote(note.id)
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("🗑️ حذف", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

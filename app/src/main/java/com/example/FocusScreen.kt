package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.StudySpaceTokens
import com.example.ui.viewmodel.StudyViewModel
import com.example.data.FocusSession
import com.example.data.StudyTask
import com.example.util.StudyLocalization
import com.example.util.StudyReminderWorker
import com.example.util.StudySkillRequest
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// --- FOCUS ROOM / POMODORO TIMER SCREEN ---
@Composable
fun FocusScreen(viewModel: StudyViewModel) {
    val lang by viewModel.language
    val timerSecs by viewModel.timerSeconds
    val timerMax by viewModel.timerMaxSeconds
    val running by viewModel.timerRunning
    val sessionType by viewModel.sessionType
    val selectedTask = viewModel.selectedTask.value
    val tasks by viewModel.allTasks.collectAsState()

    // Guided meditation instructions parameters
    val breatheLabel by viewModel.breatheState
    val animScale by viewModel.breatheScale

    val minutes = timerSecs / 60
    val seconds = timerSecs % 60
    val timeFormatted = String.format("%02d:%02d", minutes, seconds)

    val listActiveTasks = tasks.filter { !it.isCompleted }
    val activeSoundMode by viewModel.activeSoundscapeMode
    val sessionAccent = MaterialTheme.colorScheme.secondary
    val primaryActionText = when {
        running -> if (lang == "vi") "Tạm dừng" else "Pause"
        sessionType == "break" -> if (lang == "vi") "Bắt đầu nghỉ" else "Start break"
        sessionType == "meditation" -> if (lang == "vi") "Bắt đầu thiền" else "Start meditation"
        else -> if (lang == "vi") "Bắt đầu tập trung" else "Start focus"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App header inside screen
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = StudyLocalization.translate("focus", lang),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (sessionType == "focus") {
                        if (lang == "vi") "Tập trung sâu, tránh xao nhãng" else "Deep focus, fewer distractions"
                    } else if (sessionType == "break") {
                        if (lang == "vi") "Nghỉ ngắn để hồi phục năng lượng" else "Short break to recharge"
                    } else {
                        if (lang == "vi") "Thở chậm và thả lỏng" else "Slow breathing and reset"
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .background(sessionAccent.copy(alpha = 0.12f), StudySpaceTokens.controlShape)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (running) (if (lang == "vi") "Đang chạy" else "Running") else (if (lang == "vi") "Sẵn sàng" else "Ready"),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = sessionAccent
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // Selection of active goal to focus
        if (sessionType == "focus") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                        RoundedCornerShape(16.dp)
                    ),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedTask?.title ?: (if (lang == "vi") "Tập trung tự do" else "Free focus"),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (selectedTask != null) {
                                "${selectedTask.category} • ${selectedTask.elapsedTimeSeconds / 60}/${selectedTask.targetMinutes} phút"
                            } else {
                                if (lang == "vi") "Chưa liên kết mục tiêu nào" else "No linked task"
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                    
                    TextButton(
                        onClick = {
                            // Simple cycle selection of active task
                            if (listActiveTasks.isNotEmpty()) {
                                if (selectedTask == null) {
                                    viewModel.selectTaskToFocus(listActiveTasks.first())
                                } else {
                                    val idx = listActiveTasks.indexOfFirst { it.id == selectedTask.id }
                                    if (idx == -1 || idx == listActiveTasks.lastIndex) {
                                        viewModel.selectTaskToFocus(null)
                                    } else {
                                        viewModel.selectTaskToFocus(listActiveTasks[idx + 1])
                                    }
                                }
                            } else {
                                Toast.makeText(viewModel.getApplication(), if (lang == "vi") "Mục tiêu trống! Hãy tạo mục tiêu trong Lịch Học trước." else "No active tasks. Create one in Scheduler first.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.testTag("select_focus_task_button")
                    ) {
                        Text(text = if (lang == "vi") "Đổi" else "Pick", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Mode Toggles: Focus, Break, Meditation
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val modes = listOf("focus", "break", "meditation")
            modes.forEach { mode ->
                val isSel = sessionType == mode
                val modeTitle = when (mode) {
                    "focus" -> if (lang == "vi") "Tập trung" else "Focus"
                    "break" -> if (lang == "vi") "Nghỉ" else "Break"
                    else -> if (lang == "vi") "Thiền" else "Meditate"
                }
                val modeMinutes = when (mode) {
                    "focus" -> 25
                    "break" -> 5
                    else -> 10
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isSel) sessionAccent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                        )
                        .clickable { viewModel.setSessionMode(mode) }
                        .testTag("mode_$mode"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = modeTitle,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSel) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (lang == "vi") "$modeMinutes phút" else "${modeMinutes}m",
                            fontSize = 9.sp,
                            color = if (isSel) MaterialTheme.colorScheme.background.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // High-fidelity pre-set timer duration selector (Forest, Focus To-Do style)
        if (sessionType != "meditation") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val durations = if (sessionType == "focus") {
                    listOf(15, 25, 35, 45, 60)
                } else {
                    listOf(3, 5, 10, 15, 20)
                }
                Text(
                    text = if (lang == "vi") "Thời lượng:" else "Duration:",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(70.dp)
                )
                durations.forEach { mins ->
                    val isCurrent = (timerMax / 60) == mins
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(StudySpaceTokens.buttonShape)
                            .background(
                                if (isCurrent) sessionAccent.copy(alpha = 0.12f)
                                else Color.Transparent
                            )
                            .border(
                                1.dp,
                                if (isCurrent) sessionAccent
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                StudySpaceTokens.buttonShape
                            )
                            .clickable { viewModel.setCustomTimerDuration(mins) }
                            .testTag("duration_$mins"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (lang == "vi") "${mins}p" else "${mins}m",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isCurrent) sessionAccent else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // High-fidelity central focus timer progress circle or Meditation instruction view
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, sessionAccent.copy(alpha = 0.18f), RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(224.dp),
                contentAlignment = Alignment.Center
            ) {
                val strokeColor = sessionAccent
                val secondaryColor = MaterialTheme.colorScheme.secondary
                val secondaryStrokeColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)

                if (sessionType == "meditation") {
                    Box(
                        modifier = Modifier
                            .size(155.dp)
                            .scale(animScale.coerceIn(0.92f, 1.18f))
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(strokeColor.copy(alpha = 0.22f), Color.Transparent)
                                ),
                                CircleShape
                            )
                            .border(1.5.dp, strokeColor.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = breatheLabel,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = strokeColor,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = timeFormatted,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                } else {
                    val progress = if (timerMax > 0) timerSecs.toFloat() / timerMax.toFloat() else 0f

                    Canvas(modifier = Modifier.size(188.dp)) {
                        val radius = size.minDimension / 2f
                        val center = Offset(size.width / 2f, size.height / 2f)

                        drawCircle(
                            color = secondaryStrokeColor,
                            radius = radius - 15f,
                            center = center,
                            style = Stroke(width = 9.dp.toPx())
                        )

                        val gradientBrush = Brush.sweepGradient(
                            colors = listOf(strokeColor, secondaryColor, strokeColor),
                            center = center
                        )

                        drawArc(
                            brush = gradientBrush,
                            startAngle = -90f,
                            sweepAngle = progress * 360f,
                            useCenter = false,
                            topLeft = Offset(15f, 15f),
                            size = Size(size.width - 30f, size.height - 30f),
                            style = Stroke(width = 11.dp.toPx(), cap = StrokeCap.Round)
                        )

                        if (progress > 0f) {
                            val angleRad = Math.toRadians((progress * 360f - 90f).toDouble())
                            val dotRadius = radius - 15f
                            val dotX = center.x + dotRadius * Math.cos(angleRad).toFloat()
                            val dotY = center.y + dotRadius * Math.sin(angleRad).toFloat()

                            drawCircle(
                                color = strokeColor.copy(alpha = 0.32f),
                                radius = 11.dp.toPx(),
                                center = Offset(dotX, dotY)
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 5.dp.toPx(),
                                center = Offset(dotX, dotY)
                            )
                            drawCircle(
                                color = strokeColor,
                                radius = 3.dp.toPx(),
                                center = Offset(dotX, dotY)
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = timeFormatted,
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = if (sessionType == "focus") (if (lang == "vi") "PHIÊN TẬP TRUNG" else "FOCUS SESSION") else (if (lang == "vi") "NGHỈ GIẢI LAO" else "REST TIME"),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = strokeColor,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                if (running) viewModel.pauseTimer() else viewModel.startTimer()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(StudySpaceTokens.buttonHeight)
                .testTag("trigger_timer_button"),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = StudySpaceTokens.buttonShape,
            elevation = ButtonDefaults.buttonElevation(6.dp)
        ) {
            Icon(
                imageVector = if (running) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (running) "Pause" else "Play",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = primaryActionText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Ambient sound helper (for nice study atmosphere)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.035f), RoundedCornerShape(18.dp))
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (lang == "vi") "Âm nền" else "Soundscape",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = sessionAccent,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val soundModes = listOf(
                    Triple("none", if (lang == "vi") "Tắt" else "Muted", Icons.AutoMirrored.Filled.VolumeOff),
                    Triple("rain", if (lang == "vi") "Mưa" else "Rain", Icons.Default.WaterDrop),
                    Triple("binaural", if (lang == "vi") "Binaural" else "Alpha Beats", Icons.Default.Hearing),
                    Triple("zen", if (lang == "vi") "Thiền" else "Zen", Icons.Default.SelfImprovement)
                )
                
                soundModes.forEach { (modeCode, modeLabel, icon) ->
                    val isSelected = activeSoundMode == modeCode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(StudySpaceTokens.buttonShape)
                            .background(
                                if (isSelected) sessionAccent.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
                            )
                            .border(
                                1.dp,
                                if (isSelected) sessionAccent
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                StudySpaceTokens.buttonShape
                            )
                            .clickable {
                                viewModel.setSoundscapeMode(modeCode)
                            }
                            .testTag("sound_$modeCode"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = modeLabel,
                                modifier = Modifier.size(13.dp),
                                tint = if (isSelected) sessionAccent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text = modeLabel,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) sessionAccent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))

        // Action Buttons Row: Control timer
        TextButton(
            onClick = { viewModel.resetTimer() },
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .testTag("reset_timer_button")
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Reset",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (lang == "vi") "Đặt lại bộ đếm" else "Reset timer",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )
        }
        Spacer(modifier = Modifier.height(88.dp))
    }
}


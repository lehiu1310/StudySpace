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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.InsertChartOutlined
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.automirrored.filled.EventNote
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
import androidx.compose.runtime.LaunchedEffect
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
import com.example.learning.AiProviderBadge
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// --- DASHBOARD SCREEN ---
// Custom-rendered weekly bar chart from real focus sessions only.
@Composable
fun WeeklyProgressChart(minutesByDay: FloatArray, lang: String, onStartFocus: () -> Unit = {}) {
    val daysLabelKeys = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
    val hasData = minutesByDay.any { it > 0f }
    val weeklyValues = minutesByDay.toList()
    val animatedBars = remember { List(7) { Animatable(0f) } }
    LaunchedEffect(weeklyValues) {
        animatedBars.forEachIndexed { index, bar ->
            launch {
                delay(index * 45L)
                bar.animateTo(weeklyValues.getOrElse(index) { 0f }, animationSpec = tween(460))
            }
        }
    }
    val animatedMinutes = animatedBars.map { it.value }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    val textLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
    ) {
        if (!hasData) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = androidx.compose.foundation.BorderStroke(1.dp, primaryColor.copy(alpha = 0.24f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.InsertChartOutlined, contentDescription = null, tint = primaryColor)
                    Text(
                        text = if (lang == "vi") "Học 15 phút hôm nay để bắt đầu biểu đồ của bạn." else "Study for 15 minutes today to start your chart.",
                        modifier = Modifier.weight(1f),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    TextButton(onClick = onStartFocus) {
                        Text(if (lang == "vi") "Bắt đầu" else "Start")
                    }
                }
            }
        } else Column {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .padding(horizontal = 8.dp)
            ) {
                val width = size.width
                val height = size.height
                val maxVal = (minutesByDay.maxOrNull() ?: 0f).coerceAtLeast(60f)

                // Draw horizontal grid lines
                val gridLinesCount = 4
                for (i in 0..gridLinesCount) {
                    val y = (height / gridLinesCount) * i
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1f
                    )
                }

                // Draw bars
                val barsCount = 7
                val barSpacing = width / (barsCount + 1)
                val barWidth = barSpacing * 0.45f

                for (idx in 0 until barsCount) {
                    val minutes = animatedMinutes[idx]
                    val progressHeight = (minutes / maxVal) * (height - 30f)
                    val barHeight = if (minutes <= 0f) 0f else progressHeight.coerceAtLeast(8f)
                    
                    val barX = barSpacing * (idx + 1) - (barWidth / 2f)
                    val barY = height - 30f - barHeight

                    // Gradient brush for bar cylinder
                    val brush = Brush.verticalGradient(
                        colors = listOf(primaryColor, secondaryColor),
                        startY = barY,
                        endY = height - 30f
                    )

                    // Draw soft background shadows/glares for each bar slot
                    drawRoundRect(
                        color = primaryColor.copy(alpha = 0.04f),
                        topLeft = Offset(barX - 4f, 0f),
                        size = Size(barWidth + 8f, height - 30f),
                        cornerRadius = CornerRadius(10f, 10f)
                    )

                    if (barHeight > 0f) {
                        // Round corner bar rectangle (main)
                        drawRoundRect(
                            brush = brush,
                            topLeft = Offset(barX, barY),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(12f, 12f)
                        )

                        drawCircle(
                            color = Color.White.copy(alpha = 0.85f),
                            radius = 3.dp.toPx(),
                            center = Offset(barX + barWidth / 2f, barY + 5.dp.toPx())
                        )
                    }
                }
            }

            // Labels row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                daysLabelKeys.forEachIndexed { idx, day ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = day,
                            fontSize = 11.sp,
                            color = textLabelColor,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${minutesByDay[idx].toInt()}m",
                            fontSize = 8.sp,
                            color = primaryColor.copy(alpha = 0.8f),
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

// AI Personalized Coaching Component (Gemini API Integration)
@Composable
fun AISkillSection(viewModel: StudyViewModel, tasks: List<StudyTask>, sessions: List<FocusSession>, online: com.example.learning.LearningViewModel) {
    val lang by viewModel.language
    val isChallengeLoading = online.busy
    val currentChallenge by viewModel.currentChallenge
    
    var selectedCategoryForAI by remember { mutableStateOf("Toán") }
    var selectedTaskTitleForAI by remember { mutableStateOf("") }
    var selectedLevelForAI by remember { mutableStateOf("Cơ bản") }
    var selectedWeaknessForAI by remember { mutableStateOf("Hay trì hoãn") }
    var selectedLearningStyleForAI by remember { mutableStateOf("Thực hành") }
    var selectedDeadlineForAI by remember { mutableStateOf("Hôm nay") }
    var selectedTimeBudgetForAI by remember { mutableStateOf(25) }
    
    var inputQuizAnswer by remember { mutableStateOf("") }

    if (selectedTaskTitleForAI.isEmpty() && tasks.isNotEmpty()) {
        val firstTask = tasks.first()
        selectedTaskTitleForAI = firstTask.title
        selectedCategoryForAI = normalizeAiCategory(firstTask.category)
    }

    val weekStart = remember {
        java.util.Calendar.getInstance().apply {
            firstDayOfWeek = java.util.Calendar.MONDAY
            set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val focusSessions = sessions.filter { it.sessionType == "focus" }
    val weeklyFocusMinutes = focusSessions.filter { it.timestamp >= weekStart }.sumOf { it.durationMinutes }
    val openTaskCount = tasks.count { !it.isCompleted }
    val completedTaskCount = tasks.count { it.isCompleted }
    val selectedTask = tasks.firstOrNull { it.title == selectedTaskTitleForAI }
    val selectedProgressText = selectedTask?.let { task ->
        val learned = task.elapsedTimeSeconds / 60
        "${learned}/${task.targetMinutes}p"
    } ?: if (lang == "vi") "Chưa gắn" else "Not linked"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
            .testTag("ai_coach_card"),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "AI", fontSize = 12.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = StudyLocalization.translate("ai_exercise", lang),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (lang == "vi") "Đọc dữ liệu học tập rồi tạo bài luyện đúng điểm yếu" else "Reads your study data and builds a targeted drill",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        lineHeight = 16.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (currentChallenge == null) {
                Text(
                    text = if (lang == "vi") "AI đang đọc dữ liệu hiện tại" else "AI context from your app",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    AiContextMetric(label = if (lang == "vi") "Tuần này" else "This week", value = "${weeklyFocusMinutes}p", modifier = Modifier.weight(1f))
                    AiContextMetric(label = if (lang == "vi") "Đang mở" else "Open", value = "$openTaskCount", modifier = Modifier.weight(1f))
                    AiContextMetric(label = if (lang == "vi") "Mục tiêu" else "Goal", value = selectedProgressText, modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = if (lang == "vi") "1. Môn học & mục tiêu chính" else "1. Subject and main goal",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                AiChipGrid(
                    options = listOf("Toán", "Ngoại ngữ", "Lập trình", "Khác"),
                    selected = selectedCategoryForAI,
                    onSelected = { selectedCategoryForAI = it },
                    selectedColor = MaterialTheme.colorScheme.primary,
                    columns = 2
                )

                Spacer(modifier = Modifier.height(10.dp))
                if (tasks.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().clip(StudySpaceTokens.controlShape).background(MaterialTheme.colorScheme.surfaceVariant).padding(StudySpaceTokens.spaceMedium),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(StudySpaceTokens.spaceSmall)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.EventNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        Text(if (lang == "vi") "Chưa có mục tiêu" else "No planner goal", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text(
                            if (lang == "vi") "AI sẽ tạo bài luyện nền tảng trước, rồi bạn có thể lưu thành mục tiêu nhỏ." else "AI will start with a baseline drill you can save as a small goal.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        TextButton(onClick = { viewModel.navigateTo("planner") }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(StudySpaceTokens.spaceSmall))
                            Text(if (lang == "vi") "Thêm mục tiêu" else "Add a goal")
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        tasks.take(4).forEach { task ->
                            val isSel = selectedTaskTitleForAI == task.title
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                    .clip(StudySpaceTokens.controlShape)
                    .background(if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.035f))
                    .border(1.dp, if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f), StudySpaceTokens.controlShape)
                                    .clickable {
                                        selectedTaskTitleForAI = task.title
                                        selectedCategoryForAI = normalizeAiCategory(task.category)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = task.title,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${task.category} • ${task.elapsedTimeSeconds / 60}/${task.targetMinutes}p",
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        maxLines = 1
                                    )
                                }
                                if (isSel) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = if (lang == "vi") "2. Hồ sơ học tập phiên này" else "2. Session learning profile",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                AiChipGrid(options = listOf("Mất gốc", "Cơ bản", "Trung bình", "Khá"), selected = selectedLevelForAI, onSelected = { selectedLevelForAI = it }, selectedColor = MaterialTheme.colorScheme.primary, columns = 2)
                Spacer(modifier = Modifier.height(8.dp))
                AiChipGrid(options = listOf("Hay trì hoãn", "Dễ quên", "Không hiểu gốc", "Sai khi làm bài"), selected = selectedWeaknessForAI, onSelected = { selectedWeaknessForAI = it }, selectedColor = MaterialTheme.colorScheme.primary, columns = 2)
                Spacer(modifier = Modifier.height(8.dp))
                AiChipGrid(options = listOf("Thực hành", "Checklist", "Ôn thi nhanh", "Giải thích lại"), selected = selectedLearningStyleForAI, onSelected = { selectedLearningStyleForAI = it }, selectedColor = MaterialTheme.colorScheme.primary, columns = 2)
                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = if (lang == "vi") "Thời gian rảnh" else "Time budget", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f))
                        Spacer(modifier = Modifier.height(6.dp))
                        AiChipGrid(
                            options = listOf("15p", "25p", "45p", "60p"),
                            selected = "${selectedTimeBudgetForAI}p",
                            onSelected = { selectedTimeBudgetForAI = it.removeSuffix("p").toIntOrNull() ?: 25 },
                            selectedColor = MaterialTheme.colorScheme.primary,
                            columns = 2
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = if (lang == "vi") "Độ gấp" else "Deadline", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f))
                        Spacer(modifier = Modifier.height(6.dp))
                        AiChipGrid(
                            options = listOf("Hôm nay", "Tuần này", "Ôn thi", "Không gấp"),
                            selected = selectedDeadlineForAI,
                            onSelected = { selectedDeadlineForAI = it },
                            selectedColor = MaterialTheme.colorScheme.primary,
                            columns = 2
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        val request = StudySkillRequest(
                            category = selectedCategoryForAI,
                            goal = selectedTaskTitleForAI.ifEmpty { if (lang == "vi") "Nâng cao năng suất học tập" else "Improve study productivity" },
                            level = selectedLevelForAI,
                            weakness = selectedWeaknessForAI,
                            learningStyle = selectedLearningStyleForAI,
                            timeBudgetMinutes = selectedTimeBudgetForAI,
                            deadline = selectedDeadlineForAI,
                            weeklyFocusMinutes = weeklyFocusMinutes,
                            openTaskCount = openTaskCount,
                            completedTaskCount = completedTaskCount,
                            focusSessionCount = focusSessions.size,
                            recentTaskTitles = tasks.take(5).map { "${it.title} (${it.category}, ${it.elapsedTimeSeconds / 60}/${it.targetMinutes}p)" }
                        )
                        val fallback = com.example.util.GeminiAssistant.buildOfflineChallenge(request)
                        online.generateAIChallenge(request, fallback, viewModel::showAIChallenge)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(StudySpaceTokens.buttonHeight)
                        .testTag("ai_generate_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    enabled = !isChallengeLoading,
                    shape = StudySpaceTokens.buttonShape
                ) {
                    if (isChallengeLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = StudyLocalization.translate("ai_loading", lang), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(text = if (lang == "vi") "Phân tích hồ sơ & tạo bài luyện" else "Analyze profile and create drill", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            } else {
                val challenge = currentChallenge!!
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Text(
                        text = challenge.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Text(text = "${challenge.estimatedMinutes}p • ${challenge.difficulty}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
                AiProviderBadge(online.aiChallengeProvider ?: "offline")
                Spacer(modifier = Modifier.height(10.dp))

                if (challenge.diagnosis.isNotBlank()) {
                    AiResultBlock(title = if (lang == "vi") "Chẩn đoán" else "Diagnosis", body = challenge.diagnosis, accent = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (challenge.microPlan.isNotEmpty()) {
                    AiResultBlock(title = if (lang == "vi") "Kế hoạch phiên học" else "Session plan", items = challenge.microPlan, accent = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                AiResultBlock(title = StudyLocalization.translate("personalized_tip", lang), body = challenge.tip, accent = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.height(8.dp))
                AiResultBlock(title = StudyLocalization.translate("personalized_ex", lang), body = challenge.exercise, accent = MaterialTheme.colorScheme.primary)
                if (challenge.answerChecklist.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AiResultBlock(title = if (lang == "vi") "Checklist tự chấm" else "Self-check checklist", items = challenge.answerChecklist, accent = MaterialTheme.colorScheme.secondary)
                }
                if (challenge.nextAction.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AiResultBlock(title = if (lang == "vi") "Bước tiếp theo" else "Next action", body = challenge.nextAction, accent = MaterialTheme.colorScheme.primary)
                }

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = inputQuizAnswer,
                    onValueChange = { inputQuizAnswer = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_answer_input"),
                    placeholder = { Text(text = StudyLocalization.translate("enter_answer", lang), fontSize = 12.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = {
                            inputQuizAnswer = ""
                            viewModel.clearAIChallenge()
                        },
                        modifier = Modifier.weight(1f).height(StudySpaceTokens.buttonHeight),
                        shape = StudySpaceTokens.buttonShape,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                    ) {
                        Text(text = if (lang == "vi") "Tạo lại" else "Regenerate", fontSize = 11.sp, maxLines = 1)
                    }
                    Button(
                        onClick = {
                            if (inputQuizAnswer.isNotBlank()) {
                                viewModel.claimAIChallengeRewards()
                                inputQuizAnswer = ""
                            } else {
                                Toast.makeText(viewModel.getApplication(), if (lang == "vi") "Vui lòng nhập lời giải trước khi gửi nộp!" else "Please write a brief answer before submitting!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1.4f).height(StudySpaceTokens.buttonHeight).testTag("submit_ai_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = StudySpaceTokens.buttonShape,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                    ) {
                        Text(text = StudyLocalization.translate("submit_challenge", lang), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun AiContextMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, StudySpaceTokens.controlShape)
            .heightIn(min = 56.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, textAlign = TextAlign.Center)
            Text(text = value, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun AiChipGrid(
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    selectedColor: Color,
    columns: Int = 2
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        options.chunked(columns).forEach { rowOptions ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                rowOptions.forEach { option ->
                    val isSelected = selected == option
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(StudySpaceTokens.buttonShape)
                            .background(if (isSelected) selectedColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                            .border(1.dp, if (isSelected) selectedColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f), StudySpaceTokens.buttonShape)
                            .clickable { onSelected(option) }
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = option,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                repeat(columns - rowOptions.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AiResultBlock(
    title: String,
    body: String = "",
    items: List<String> = emptyList(),
    accent: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(accent.copy(alpha = 0.07f), RoundedCornerShape(8.dp))
            .border(1.dp, accent.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(text = title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accent)
            if (body.isNotBlank()) {
                Text(text = body, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f), lineHeight = 17.sp)
            }
            items.forEachIndexed { index, item ->
                Text(text = "${index + 1}. $item", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f), lineHeight = 17.sp)
            }
        }
    }
}

private fun normalizeAiCategory(category: String): String {
    return when {
        category.contains("toán", ignoreCase = true) || category.contains("math", ignoreCase = true) -> "Toán"
        category.contains("ngoại", ignoreCase = true) || category.contains("anh", ignoreCase = true) || category.contains("english", ignoreCase = true) -> "Ngoại ngữ"
        category.contains("lập", ignoreCase = true) || category.contains("code", ignoreCase = true) || category.contains("program", ignoreCase = true) || category.contains("kotlin", ignoreCase = true) -> "Lập trình"
        else -> "Khác"
    }
}

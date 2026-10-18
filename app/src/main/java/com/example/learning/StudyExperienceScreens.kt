package com.example.learning

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.StudyEmptyState
import com.example.ui.theme.StudySpaceTokens
import com.example.ui.viewmodel.StudyViewModel
import org.json.JSONObject
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

@Composable
fun AiProviderBadge(provider: String, lang: String = "vi") {
    val direct = provider.equals("gemini", ignoreCase = true)
    Surface(
        shape = CircleShape,
        color = if (direct) StudySpaceTokens.accent50 else StudySpaceTokens.primary50
    ) {
        Text(
            aiProviderLabelText(provider, lang),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (direct) StudySpaceTokens.accent600 else StudySpaceTokens.primary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

internal fun aiProviderLabelText(provider: String, lang: String): String = when {
    provider.equals("gemini", ignoreCase = true) -> if (lang == "vi") "AI trực tiếp · Gemini" else "Direct AI · Gemini"
    lang == "vi" -> "Gợi ý offline"
    else -> "Offline suggestion"
}

@Composable
fun ProductGoalsScreen(study: StudyViewModel, online: LearningViewModel) {
    val context = LocalContext.current
    val sessions by study.allSessions.collectAsState()
    val tasks by study.allTasks.collectAsState()
    val rewards by study.allRewards.collectAsState()
    val history = online.progress?.optJSONArray("history")?.objects().orEmpty()
        .filterNot { it.isNull("submitted_at") }
    val weekStart = remember { weekStartMillis() }
    val focusMinutes = WeeklyActivitySummary.calculate(sessions, tasks, rewards).focusMinutesByDay.sum().toInt()
    val quizCount = history.count { it.optLong("submitted_at") >= weekStart }
    val preferences = remember(context) { context.getSharedPreferences("studyspace_weekly_goals", 0) }
    var minuteTarget by rememberSaveable { mutableIntStateOf(preferences.getInt("minutes", 300)) }
    var quizTarget by rememberSaveable { mutableIntStateOf(preferences.getInt("quizzes", 5)) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    val lang by study.language

    if (online.signedIn && online.progress == null && !online.busy) {
        LaunchedEffect(Unit) { online.refresh() }
    }
    if (editorOpen) {
        WeeklyGoalEditor(
            initialMinutes = minuteTarget,
            initialQuizzes = quizTarget,
            onDismiss = { editorOpen = false },
            onSave = { minutes, quizzes ->
                minuteTarget = minutes
                quizTarget = quizzes
                preferences.edit().putInt("minutes", minutes).putInt("quizzes", quizzes).apply()
                editorOpen = false
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = StudySpaceTokens.spaceMedium),
        verticalArrangement = Arrangement.spacedBy(StudySpaceTokens.spaceMedium),
        contentPadding = PaddingValues(top = 20.dp, bottom = 120.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (lang == "vi") "Mục tiêu" else "Goals", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(if (lang == "vi") "Nhịp học của tuần này" else "Your pace this week", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { editorOpen = true }) { Icon(Icons.Default.Insights, if (lang == "vi") "Sửa mục tiêu" else "Edit goals") }
            }
        }
        item {
            GoalProgressBlock(
                title = if (lang == "vi") "Thời gian học" else "Study time",
                value = focusMinutes,
                target = minuteTarget,
                unit = if (lang == "vi") "phút" else "min",
                tint = StudySpaceTokens.primary
            )
        }
        item {
            GoalProgressBlock(
                title = if (lang == "vi") "Bài quiz đã nộp" else "Quizzes submitted",
                value = quizCount,
                target = quizTarget,
                unit = if (lang == "vi") "bài" else "quizzes",
                tint = StudySpaceTokens.accent
            )
        }
        item {
            OutlinedButton(onClick = { editorOpen = true }, modifier = Modifier.fillMaxWidth().height(StudySpaceTokens.buttonHeight), shape = StudySpaceTokens.buttonShape) {
                Text(if (lang == "vi") "Điều chỉnh mục tiêu tuần" else "Edit weekly targets")
            }
        }
        if (!online.signedIn) item {
            StudyEmptyState(
                title = if (lang == "vi") "Quiz sẽ được tính ở đây" else "Quiz goals appear here",
                body = if (lang == "vi") "Kết nối tài khoản học tập để xem số bài quiz đã nộp trong tuần." else "Connect your learning account to track this week's quizzes.",
                icon = Icons.Default.School,
                compact = true
            )
        }
        if (online.error != null && online.progress == null) item {
            RetryMessage(online.error.orEmpty(), online::refresh, lang)
        }
    }
}

@Composable
private fun GoalProgressBlock(title: String, value: Int, target: Int, unit: String, tint: Color) {
    Surface(shape = StudySpaceTokens.cardShape, color = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(1.dp, StudySpaceTokens.border)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.size(94.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawArc(StudySpaceTokens.muted, -90f, 360f, false, style = Stroke(width = 9.dp.toPx()))
                    drawArc(tint, -90f, 360f * (value.toFloat() / target.coerceAtLeast(1)).coerceIn(0f, 1f), false, style = Stroke(width = 9.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${(value.toFloat() / target.coerceAtLeast(1) * 100).toInt().coerceAtMost(100)}%", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = tint)
                    Text("$value/$target", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("$value / $target $unit", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (value >= target) "Mục tiêu tuần đã đạt" else "Còn ${(target - value).coerceAtLeast(0)} $unit", style = MaterialTheme.typography.labelMedium, color = tint)
            }
        }
    }
}

@Composable
private fun WeeklyGoalEditor(initialMinutes: Int, initialQuizzes: Int, onDismiss: () -> Unit, onSave: (Int, Int) -> Unit) {
    var minutes by rememberSaveable { mutableStateOf(initialMinutes.toString()) }
    var quizzes by rememberSaveable { mutableStateOf(initialQuizzes.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mục tiêu tuần") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(4) }, label = { Text("Phút học mỗi tuần") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                OutlinedTextField(quizzes, { quizzes = it.filter(Char::isDigit).take(2) }, label = { Text("Số quiz mỗi tuần") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(minutes.toInt(), quizzes.toInt()) }, enabled = (minutes.toIntOrNull() ?: 0) in 30..5000 && (quizzes.toIntOrNull() ?: 0) in 1..99) { Text("Lưu") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
    )
}

@Composable
fun ProductReportsScreen(study: StudyViewModel, online: LearningViewModel) {
    val context = LocalContext.current
    val exportProgress = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { target ->
        if (target != null) online.exportReport(target)
    }
    val sessions by study.allSessions.collectAsState()
    val history = online.progress?.optJSONArray("history")?.objects().orEmpty()
        .filterNot { it.isNull("submitted_at") }
    val mastery = online.progress?.optJSONArray("mastery")?.objects().orEmpty()
    val lang by study.language
    val exportSnackbar = remember { SnackbarHostState() }
    LaunchedEffect(online.personalSyncStatus, online.error) {
        (online.personalSyncStatus ?: online.error)?.let { exportSnackbar.showSnackbar(it) }
    }
    PersonalSyncDialog(study, online)
    if (online.signedIn && online.progress == null && !online.busy) {
        LaunchedEffect(Unit) { online.refresh() }
    }
    val series = remember(history) { weeklyScoreSeries(history) }
    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = StudySpaceTokens.spaceMedium),
        verticalArrangement = Arrangement.spacedBy(StudySpaceTokens.spaceMedium),
        contentPadding = PaddingValues(top = 20.dp, bottom = 120.dp)
    ) {
        item {
            Column {
                Text(if (lang == "vi") "Báo cáo học tập" else "Learning reports", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(if (lang == "vi") "Tiến bộ được ghi nhận theo thời gian" else "Your progress over time", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (online.busy && online.progress == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (online.offline && online.progress != null) item { OfflineBanner(lang) }
        online.error?.takeIf { online.progress == null }?.let { error -> item { RetryMessage(error, online::refresh, lang) } }
        item {
            Text(if (lang == "vi") "Xu hướng điểm quiz · 6 tuần" else "Quiz score trend · 6 weeks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (series.any { it != null }) ScoreTrendChart(series, lang)
            else StudyEmptyState(
                title = if (lang == "vi") "Chưa có xu hướng điểm" else "No score trend yet",
                body = if (lang == "vi") "Nộp một bài quiz để bắt đầu theo dõi kết quả theo tuần." else "Submit a quiz to begin your weekly score trend.",
                icon = Icons.Default.BarChart,
                compact = true,
                actionLabel = if (lang == "vi") "Đến môn học" else "Browse courses",
                onAction = { study.navigateTo("learning") }
            )
        }
        item {
            Text(if (lang == "vi") "Mức độ nắm theo chương" else "Mastery by chapter", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (mastery.isEmpty()) {
                StudyEmptyState(
                    title = if (lang == "vi") "Chưa có dữ liệu theo chương" else "No chapter data yet",
                    body = if (lang == "vi") "Kết quả sẽ xuất hiện sau khi bạn làm quiz." else "Chapter results appear after you take quizzes.",
                    icon = Icons.Default.Insights,
                    compact = true
                )
            } else MasteryRadar(mastery, lang)
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (lang == "vi") "Đồng bộ dữ liệu" else "Sync study data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(online.personalSyncStatus ?: if (online.signedIn) "Kế hoạch và lịch sử tập trung" else "Đăng nhập để đồng bộ trên nhiều thiết bị", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (online.signedIn) IconButton(onClick = online::syncPersonalStudy, enabled = !online.busy && !study.timerRunning.value) { Icon(Icons.Default.CloudSync, "Đồng bộ") }
            }
        }
        item { Text(if (lang == "vi") "Lịch sử gần đây" else "Recent activity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        if (history.isEmpty()) item {
            StudyEmptyState(
                title = stringResource(R.string.product_empty_history_title),
                body = stringResource(R.string.product_empty_history_body),
                icon = Icons.Default.BarChart,
                compact = true
            )
        }
        items(history.take(8), key = { it.optLong("id") }) { attempt ->
            val total = attempt.optInt("total")
            val score = attempt.optInt("score")
            val percent = if (total > 0) score * 100 / total else 0
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).background(StudySpaceTokens.primary.copy(alpha = .09f), CircleShape), contentAlignment = Alignment.Center) {
                    Text("${percent}%", style = MaterialTheme.typography.labelMedium, color = StudySpaceTokens.primary, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(attempt.optString("chapter_title", "Quiz"), maxLines = 1, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(attempt.optLong("submitted_at"))), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("$score/$total", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider(color = StudySpaceTokens.border)
        }
        item {
            HorizontalDivider(color = StudySpaceTokens.border)
            Text(if (lang == "vi") "Xuất dữ liệu" else "Export", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = { online.prepareProgressReportExport { ready -> if (ready) exportProgress.launch("StudySpace-ket-qua.pdf") } },
                    enabled = online.signedIn && !online.busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (online.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.BarChart, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.product_pdf))
                }
                TextButton(onClick = { study.exportData(context, "PDF") }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Timer, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.product_focus_pdf))
                }
            }
        }
    }
    SnackbarHost(exportSnackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp))
    }
}

@Composable
private fun PersonalSyncDialog(study: StudyViewModel, online: LearningViewModel) {
    val timerRunning by study.timerRunning
    online.personalSyncChoice?.let { choice ->
        AlertDialog(
            onDismissRequest = { if (!online.busy) online.dismissPersonalSyncChoice() },
            title = { Text(stringResource(R.string.personal_sync_choose)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.personal_sync_comparison, choice.localPlans, choice.localSessions, choice.cloudPlans, choice.cloudSessions))
                    if (choice.cloudUpdatedAt > 0) Text(stringResource(R.string.personal_sync_revision, choice.version, java.text.DateFormat.getDateTimeInstance().format(Date(choice.cloudUpdatedAt))), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.personal_sync_warning))
                    online.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                Column {
                    TextButton(onClick = { online.resolvePersonalSync(true, { !study.timerRunning.value }, {}) }, enabled = !online.busy && !timerRunning) { Text(stringResource(R.string.personal_sync_keep_device)) }
                    TextButton(onClick = { online.resolvePersonalSync(false, { !study.timerRunning.value }, { study.selectTaskToFocus(null) }) }, enabled = !online.busy && !timerRunning) { Text(stringResource(R.string.personal_sync_use_cloud)) }
                }
            },
            dismissButton = { TextButton(onClick = online::dismissPersonalSyncChoice, enabled = !online.busy) { Text(stringResource(R.string.product_cancel)) } }
        )
    }
}

@Composable
internal fun ScoreTrendChart(values: List<Int?>, lang: String, chartHeight: androidx.compose.ui.unit.Dp = 180.dp) {
    val labels = if (lang == "vi") listOf("5T", "4T", "3T", "2T", "T-1", "Nay") else listOf("5w", "4w", "3w", "2w", "1w", "Now")
    Canvas(Modifier.fillMaxWidth().height(chartHeight).padding(top = 12.dp)) {
        val chartHeight = size.height - 26.dp.toPx()
        val slot = size.width / values.size
        repeat(4) { i ->
            val y = chartHeight * i / 3f
            drawLine(StudySpaceTokens.border, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        }
        values.forEachIndexed { index, value ->
            val x = slot * index + slot / 2
            val height = chartHeight * (value ?: 0) / 100f
            if (value != null) {
                drawRoundRect(
                    color = if (index == values.lastIndex) StudySpaceTokens.primary else StudySpaceTokens.primary.copy(alpha = .34f),
                    topLeft = Offset(x - 9.dp.toPx(), chartHeight - height),
                    size = androidx.compose.ui.geometry.Size(18.dp.toPx(), height.coerceAtLeast(3.dp.toPx())),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
                )
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        labels.forEachIndexed { i, label ->
            Text(values[i]?.let { "$it%" } ?: label, style = MaterialTheme.typography.labelSmall, color = if (i == labels.lastIndex) StudySpaceTokens.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun MasteryRadar(mastery: List<JSONObject>, lang: String, chartHeight: androidx.compose.ui.unit.Dp = 250.dp) {
    val values = mastery.mapNotNull { chapter -> chapter.optDouble("percentage").takeIf { !chapter.isNull("percentage") }?.toFloat()?.coerceIn(0f, 100f) }
    if (values.size < 3) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
            mastery.take(5).forEach { chapter ->
                val value = chapter.optDouble("percentage").toInt().coerceIn(0, 100)
                Text("${chapter.optString("title")} · ${value}%", style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator({ value / 100f }, Modifier.fillMaxWidth(), color = StudySpaceTokens.primary)
            }
        }
        return
    }
    val primary = MaterialTheme.colorScheme.primary
    val labels = mastery.map { it.optString("title") }.take(values.size)
    Canvas(Modifier.fillMaxWidth().height(chartHeight).padding(12.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = minOf(size.width, size.height) * .38f
        val count = values.size
        for (ring in 1..4) {
            val r = radius * ring / 4f
            val path = Path()
            repeat(count) { i ->
                val angle = -Math.PI / 2 + 2 * Math.PI * i / count
                val point = Offset(center.x + kotlin.math.cos(angle).toFloat() * r, center.y + kotlin.math.sin(angle).toFloat() * r)
                if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            }
            path.close()
            drawPath(path, StudySpaceTokens.border, style = Stroke(1.dp.toPx()))
        }
        val shape = Path()
        values.forEachIndexed { i, value ->
            val angle = -Math.PI / 2 + 2 * Math.PI * i / count
            val r = radius * value / 100f
            val point = Offset(center.x + kotlin.math.cos(angle).toFloat() * r, center.y + kotlin.math.sin(angle).toFloat() * r)
            if (i == 0) shape.moveTo(point.x, point.y) else shape.lineTo(point.x, point.y)
            drawLine(primary.copy(alpha = .3f), center, Offset(center.x + kotlin.math.cos(angle).toFloat() * radius, center.y + kotlin.math.sin(angle).toFloat() * radius), 1.dp.toPx())
        }
        shape.close()
        drawPath(shape, primary.copy(alpha = .17f))
        drawPath(shape, primary, style = Stroke(2.dp.toPx()))
        values.forEachIndexed { i, value ->
            val angle = -Math.PI / 2 + 2 * Math.PI * i / count
            val r = radius * value / 100f
            drawCircle(StudySpaceTokens.accent, 4.dp.toPx(), Offset(center.x + kotlin.math.cos(angle).toFloat() * r, center.y + kotlin.math.sin(angle).toFloat() * r))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        labels.forEachIndexed { i, label ->
            Text("${label.take(42)} · ${values[i].toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ProductAssistant(study: StudyViewModel, online: LearningViewModel) {
    val lang by study.language
    val progress = online.progress
    val mastery = progress?.optJSONArray("mastery")?.objects().orEmpty().filterNot { it.isNull("percentage") }
    val weakest = mastery.minByOrNull { it.optDouble("percentage") }
    val history = progress?.optJSONArray("history")?.objects().orEmpty().filterNot { it.isNull("submitted_at") }
    val scores = remember(history) { history.mapNotNull { attempt ->
        val total = attempt.optInt("total")
        if (total > 0) (attempt.optInt("score") * 100 / total) else null
    } }
    val average = scores.take(5).average().takeIf { !it.isNaN() }?.toInt()
    val recent = scores.take(2).average().takeIf { !it.isNaN() }
    val previous = scores.drop(2).take(3).average().takeIf { !it.isNaN() }
    val direction = if (recent != null && previous != null) (recent - previous).toInt() else 0
    LaunchedEffect(online.signedIn) {
        if (online.signedIn && online.progress == null && !online.busy) online.refresh()
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = StudySpaceTokens.spaceMedium),
        verticalArrangement = Arrangement.spacedBy(StudySpaceTokens.spaceMedium),
        contentPadding = PaddingValues(top = 20.dp, bottom = 120.dp)
    ) {
        item {
            Column {
                Text(if (lang == "vi") "Chẩn đoán học tập" else "Learning diagnosis", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(if (lang == "vi") "Phân tích dựa trên kết quả thật của bạn" else "Based on your actual study results", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { AiProviderBadge("offline", lang) }
        if (online.busy && progress == null) item {
            Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text(if (lang == "vi") "Đang phân tích dữ liệu học tập…" else "Analyzing your study data…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (online.offline && progress != null) item { OfflineBanner(lang) }
        online.error?.let { item { RetryMessage(it, online::refresh, lang) } }
        if (weakest != null) {
            item {
                Surface(shape = StudySpaceTokens.cardShape, color = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(1.dp, StudySpaceTokens.border)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(if (lang == "vi") "PHẦN NÊN ƯU TIÊN" else "FOCUS AREA", style = MaterialTheme.typography.labelMedium, color = StudySpaceTokens.primary, fontWeight = FontWeight.Bold)
                        Text(weakest.optString("title"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        val score = weakest.optDouble("percentage").toInt()
                        LinearProgressIndicator({ score / 100f }, Modifier.fillMaxWidth(), color = masteryTint(score))
                        Text(if (lang == "vi") "Mức nắm hiện tại $score%" else "Current mastery $score%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(
                            onClick = { online.generateWeakChapterQuiz(); study.navigateTo("learning") },
                            enabled = online.signedIn && !online.busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = StudySpaceTokens.buttonHeight),
                            shape = StudySpaceTokens.buttonShape
                        ) {
                            if (online.busy) CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            else Icon(Icons.Default.AutoAwesome, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (lang == "vi") "Ôn 15 phút · ${weakest.optString("title")}" else "Review 15 min · ${weakest.optString("title")}", maxLines = 2, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
            item {
                DiagnosisFact(
                    title = if (lang == "vi") "Xu hướng điểm" else "Score trend",
                    value = when {
                        direction > 0 -> if (lang == "vi") "Đang cải thiện" else "Improving"
                        direction < 0 -> if (lang == "vi") "Cần củng cố" else "Needs reinforcement"
                        else -> if (lang == "vi") "Ổn định" else "Steady"
                    },
                    detail = if (average != null) (if (lang == "vi") "Trung bình gần đây $average%" else "Recent average $average%") else if (lang == "vi") "Làm thêm quiz để hình thành xu hướng." else "Take more quizzes to establish a trend."
                )
            }
            item {
                DiagnosisFact(
                    title = if (lang == "vi") "Dự báo ngắn hạn" else "Short-term outlook",
                    value = when {
                        direction > 5 -> if (lang == "vi") "Đà tiến bộ tốt" else "Positive momentum"
                        direction < -5 -> if (lang == "vi") "Nên ôn lại nền tảng" else "Review the foundations"
                        else -> if (lang == "vi") "Duy trì nhịp học" else "Keep your pace"
                    },
                    detail = if (lang == "vi") "Dựa trên tối đa 5 bài quiz gần nhất; đây là gợi ý, không phải điểm dự đoán." else "Based on up to five recent quizzes; guidance, not a guaranteed score."
                )
            }
        } else if (!online.busy) item {
            StudyEmptyState(
                title = if (lang == "vi") "Chưa đủ dữ liệu để chẩn đoán" else "Not enough data to diagnose yet",
                body = if (lang == "vi") "Làm quiz theo chương để StudySpace tìm phần cần ôn và theo dõi tiến bộ." else "Take chapter quizzes so StudySpace can find review areas and track progress.",
                icon = Icons.Default.Insights,
                actionLabel = if (lang == "vi") "Đến môn học" else "Browse courses",
                onAction = { study.navigateTo("learning") }
            )
        }
        if (!online.signedIn) item {
            StudyEmptyState(
                title = if (lang == "vi") "Kết nối để phân tích kết quả quiz" else "Connect to analyze quiz results",
                body = if (lang == "vi") "Dữ liệu bài làm được gắn với tài khoản học tập của bạn." else "Quiz attempts are linked to your learning account.",
                icon = Icons.Default.School,
                compact = true
            )
        }
    }
}

@Composable private fun DiagnosisFact(title: String, value: String, detail: String) {
    Surface(shape = StudySpaceTokens.controlShape, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun OfflineBanner(lang: String) {
    Surface(shape = StudySpaceTokens.controlShape, color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(if (lang == "vi") "Ngoại tuyến · Đang dùng dữ liệu đã lưu trên thiết bị" else "Offline · Showing saved data", modifier = Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable private fun RetryMessage(message: String, retry: () -> Unit, lang: String) {
    Surface(shape = StudySpaceTokens.controlShape, color = MaterialTheme.colorScheme.errorContainer) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            IconButton(onClick = retry) { Icon(Icons.Default.Refresh, if (lang == "vi") "Thử lại" else "Retry", tint = MaterialTheme.colorScheme.error) }
        }
    }
}

private fun weekStartMillis(now: Long = System.currentTimeMillis()): Long = Calendar.getInstance().apply {
    timeInMillis = now
    firstDayOfWeek = Calendar.MONDAY
    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

internal fun weeklyScoreSeries(history: List<JSONObject>): List<Int?> {
    val currentStart = weekStartMillis()
    val weekMs = 7L * 24 * 60 * 60 * 1000
    val buckets = Array(6) { mutableListOf<Int>() }
    history.forEach { attempt ->
        val timestamp = attempt.optLong("submitted_at")
        val total = attempt.optInt("total")
        val age = ((currentStart - weekStartMillis(timestamp)) / weekMs).toInt()
        if (timestamp > 0 && total > 0 && age in 0..5) buckets[5 - age].add(attempt.optInt("score") * 100 / total)
    }
    return buckets.map { values -> values.takeIf { it.isNotEmpty() }?.average()?.toInt() }
}

private fun masteryTint(score: Int): Color = when {
    score < 50 -> Color(0xFFEF4444)
    score < 75 -> Color(0xFFF59E0B)
    else -> StudySpaceTokens.accent600
}

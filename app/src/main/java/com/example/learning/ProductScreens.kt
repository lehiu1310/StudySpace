package com.example.learning

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.StudyTask
import com.example.ui.components.StudyEmptyState
import com.example.ui.theme.StudySpaceTokens
import com.example.ui.viewmodel.StudyViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.widget.Toast

@Composable
fun ProductAuth(study: StudyViewModel, online: LearningViewModel, onClose: (() -> Unit)? = null) {
    var register by rememberSaveable { mutableStateOf(false) }
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var display by rememberSaveable { mutableStateOf("") }
    var server by rememberSaveable { mutableStateOf(online.endpoint) }
    var settings by remember { mutableStateOf(false) }
    val lang by study.language
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().imePadding().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(vertical = 24.dp)) {
        item {
            if (onClose != null) IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.learning_back)) }
            Surface(shape = StudySpaceTokens.cardShape, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, StudySpaceTokens.border)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(48.dp).background(StudySpaceTokens.primary, StudySpaceTokens.controlShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.School, null, tint = Color.White, modifier = Modifier.size(27.dp))
                        }
                        Column {
                            Text("StudySpace", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.product_signin_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AuthBenefit(Icons.AutoMirrored.Filled.MenuBook, if (lang == "vi") "Học theo lộ trình" else "Guided learning", Modifier.weight(1f))
                        AuthBenefit(Icons.Default.Insights, if (lang == "vi") "Theo dõi tiến độ" else "Track progress", Modifier.weight(1f))
                        AuthBenefit(Icons.Default.AutoAwesome, if (lang == "vi") "Ôn tập cá nhân" else "Personal review", Modifier.weight(1f))
                    }
                }
            }
        }
        item { Text(stringResource(if (register) R.string.learning_register else R.string.learning_login), style = MaterialTheme.typography.titleLarge) }
        item { OutlinedTextField(username, { username = it }, label = { Text(stringResource(R.string.learning_username)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        if (register) item { OutlinedTextField(display, { display = it }, label = { Text(stringResource(R.string.product_display_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(password, { password = it }, label = { Text(stringResource(R.string.learning_password)) }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth()) }
        online.error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        item { Button(onClick = {
            val isRegistration = register
            online.authenticate(server, username, password, isRegistration) { remoteName, id ->
                if (isRegistration) Toast.makeText(context, if (lang == "vi") "Chào mừng bạn đến với StudySpace!" else "Welcome to StudySpace!", Toast.LENGTH_SHORT).show()
                study.activateOnlineAccount(remoteName, id, server, password, display, linkCurrent = onClose != null) { local -> online.adoptOwner(local); onClose?.invoke() }
            }
        }, enabled = !online.busy && username.isNotBlank() && password.length in 8..72, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(27.dp)) {
            if (online.busy) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            else Text(stringResource(if (register) R.string.learning_register else R.string.learning_login))
        } }
        item { TextButton(onClick = { register = !register }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(if (register) R.string.product_have_account else R.string.product_new_account)) } }
        item { TextButton(onClick = { settings = !settings }) { Icon(Icons.Default.Settings, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.product_server_settings)) } }
        if (settings) item { OutlinedTextField(server, { server = it }, label = { Text(stringResource(R.string.learning_server)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
    }
}

@Composable private fun AuthBenefit(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, modifier: Modifier = Modifier) {
    Column(modifier.background(StudySpaceTokens.primary50, StudySpaceTokens.controlShape).padding(9.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(icon, null, tint = StudySpaceTokens.primary, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 2)
    }
}

@Composable private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = StudySpaceTokens.spaceSmall, bottom = 4.dp))
}

@Composable
private fun PlanEmptyState(
    completed: Boolean,
    compact: Boolean = false,
    onAdd: () -> Unit,
    onBrowse: () -> Unit
) {
    StudyEmptyState(
        title = stringResource(if (completed) R.string.product_empty_done_title else R.string.product_empty_plan_title),
        body = stringResource(if (completed) R.string.product_empty_done_body else R.string.product_empty_plan_body),
        icon = if (completed) Icons.Default.CheckCircle else Icons.AutoMirrored.Filled.EventNote,
        actionLabel = stringResource(R.string.product_empty_add_cta),
        onAction = onAdd,
        secondaryActionLabel = stringResource(R.string.product_empty_browse_cta),
        onSecondaryAction = onBrowse,
        compact = compact
    )
}

@Composable
private fun DashboardMetric(
    value: String,
    unit: String,
    label: String,
    status: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    iconContainer: Color,
    statusTint: Color,
    statusContainer: Color,
    modifier: Modifier = Modifier
) {
    val targetValue = value.toIntOrNull() ?: 0
    val animatedValue = remember { Animatable(0f) }
    LaunchedEffect(targetValue) {
        animatedValue.snapTo(0f)
        animatedValue.animateTo(targetValue.toFloat(), animationSpec = tween(durationMillis = 520))
    }
    Surface(
        modifier = modifier,
        shape = StudySpaceTokens.cardShape,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(min = 156.dp).padding(12.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(Modifier.size(34.dp).background(iconContainer, StudySpaceTokens.controlShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(18.dp))
            }
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, maxLines = 1)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(animatedValue.value.toInt().toString(), fontSize = 26.sp, lineHeight = 30.sp, color = iconTint, fontWeight = FontWeight.Bold)
                Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp), maxLines = 1)
            }
            Surface(shape = CircleShape, color = statusContainer) {
                Text(status, style = MaterialTheme.typography.labelSmall, color = statusTint, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), maxLines = 1)
            }
        }
    }
}

@Composable
private fun DashboardGoal(title: String, value: Int, target: Int, unit: String, tint: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text("$value/$target $unit", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(
            progress = { (value.toFloat() / target.coerceAtLeast(1)).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = tint
        )
    }
}

@Composable
private fun MilestoneBadge(label: String, achieved: Boolean) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (achieved) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Default.EmojiEvents,
                contentDescription = null,
                tint = if (achieved) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun CelebrationNotice(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            Text(text, color = MaterialTheme.colorScheme.onTertiaryContainer, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable fun LearningHome(study: StudyViewModel, online: LearningViewModel, onConnect: () -> Unit) {
    val user by study.currentUser
    val tasks by study.allTasks.collectAsState()
    val sessions by study.allSessions.collectAsState()
    val rewards by study.allRewards.collectAsState()
    val context = LocalContext.current
    val name = user?.displayName.orEmpty()
    val courseItems = online.courses
    val history = online.progress?.optJSONArray("history")?.objects().orEmpty().filter { !it.isNull("submitted_at") }
    val announcements = online.announcements
    val completed = courseItems.sumOf { it.optInt("completed_count") }
    val lessonActivity = online.progress?.optJSONArray("lessonActivity")?.objects().orEmpty().mapNotNull { row -> row.optLong("completed_at").takeIf { it > 0L } }
    val streakDays = remember(sessions, tasks, history, lessonActivity) {
        calculateStudyStreak(
            focusSessionTimestamps = sessions.filter { it.sessionType == "focus" }.map { it.timestamp },
            completedPlanTimestamps = tasks.filter { it.isCompleted }.mapNotNull { it.completedAt },
            quizSubmissionTimestamps = history.mapNotNull { it.optLong("submitted_at").takeIf { timestamp -> timestamp > 0L } },
            lessonCompletionTimestamps = lessonActivity
        )
    }
    val quizScores = history.mapNotNull { quiz ->
        val total = quiz.optInt("total")
        if (total > 0) quiz.optInt("score") to total else null
    }
    val scoreTrend = remember(history) { weeklyScoreSeries(history) }
    val badges = calculateStudyBadges(completed, quizScores, streakDays)
    val lang by study.language
    val badgeLabels = badges.associate { badge ->
        val vietnamese = context.getString(when (badge.id) {
            "first_lesson" -> R.string.product_badge_first_lesson
            "five_lessons" -> R.string.product_badge_five_lessons
            "ten_quizzes" -> R.string.product_badge_ten_quizzes
            "quiz_80" -> R.string.product_badge_quiz_80
            "streak_3" -> R.string.product_badge_streak_3
            else -> R.string.product_badge_streak_7
        })
        badge.id to if (lang == "vi") vietnamese else when (badge.id) {
            "first_lesson" -> "First lesson"
            "five_lessons" -> "5 lessons"
            "ten_quizzes" -> "10 quizzes"
            "quiz_80" -> "Quiz score 80%+"
            "streak_3" -> "3-day streak"
            else -> "7-day streak"
        }
    }
    val badgeSnackbar = remember { SnackbarHostState() }
    val achievedBadgeIds = badges.filter { it.achieved }.map { it.id }
    LaunchedEffect(user?.username, achievedBadgeIds) {
        val newlyUnlocked = study.claimNewStudyBadges(user?.username.orEmpty(), achievedBadgeIds.toSet())
        if (newlyUnlocked.isNotEmpty()) {
            val names = newlyUnlocked.mapNotNull(badgeLabels::get).joinToString(" · ")
            badgeSnackbar.showSnackbar(
                if (lang == "vi") context.getString(R.string.product_badge_unlocked, names) else "New badge unlocked: $names"
            )
        }
    }
    val weekly = WeeklyActivitySummary.calculate(sessions, tasks, rewards)
    val minutes = weekly.focusMinutesByDay
    val averageScore = quizScores.take(5).let { recent -> if (recent.isEmpty()) null else recent.sumOf { it.first * 100 / it.second } / recent.size }
    val performanceLabel = when {
        averageScore == null -> if (lang == "vi") "Chưa có dữ liệu" else "No data yet"
        averageScore >= 80 -> if (lang == "vi") "Giỏi" else "Strong"
        averageScore >= 60 -> if (lang == "vi") "Khá" else "Fair"
        else -> if (lang == "vi") "Cần ôn thêm" else "Review"
    }
    val performanceTint = when {
        averageScore == null -> StudySpaceTokens.mutedForeground
        averageScore >= 80 -> StudySpaceTokens.success
        averageScore >= 60 -> StudySpaceTokens.warning
        else -> StudySpaceTokens.error
    }
    val performanceSurface = when {
        averageScore == null -> StudySpaceTokens.muted
        averageScore >= 80 -> Color(0xFFECFDF5)
        averageScore >= 60 -> Color(0xFFFFFBEB)
        else -> Color(0xFFFEF2F2)
    }
    val nextCourse = courseItems.firstOrNull { it.optInt("completed_count") < it.optInt("lesson_count") } ?: courseItems.firstOrNull()
    val mastery = online.progress?.optJSONArray("mastery")?.objects().orEmpty().filterNot { it.isNull("percentage") }
    val weakest = mastery.minByOrNull { it.optDouble("percentage") }
    val goalsPreferences = remember(context) { context.getSharedPreferences("studyspace_weekly_goals", 0) }
    val minuteGoal = goalsPreferences.getInt("minutes", 300)
    val quizGoal = goalsPreferences.getInt("quizzes", 5)
    val weekStart = remember {
        Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val weeklyQuizzes = history.count { it.optLong("submitted_at") >= weekStart }
    LaunchedEffect(online.signedIn) { if (online.signedIn && !online.busy) online.refresh() }
    Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = StudySpaceTokens.spaceMedium), verticalArrangement = Arrangement.spacedBy(StudySpaceTokens.spaceMedium), contentPadding = PaddingValues(top = StudySpaceTokens.spaceMedium, bottom = 144.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(if (lang == "vi") "Tổng quan" else "Overview", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = StudySpaceTokens.foreground)
                        Text(if (lang == "vi") "Hôm nay" else "Today", style = MaterialTheme.typography.titleMedium, color = StudySpaceTokens.mutedForeground, modifier = Modifier.padding(bottom = 3.dp))
                    }
                    Text(stringResource(R.string.product_greeting, name), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = { study.navigateTo("focus") }, modifier = Modifier.fillMaxWidth().height(StudySpaceTokens.buttonHeight), shape = StudySpaceTokens.buttonShape) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (lang == "vi") "Ghi nhận học" else "Log study")
                }
            }
        }
        if (online.signedIn && nextCourse != null) item {
            OutlinedCard(
                onClick = { online.openCourse(nextCourse.getLong("id")); study.navigateTo("learning") },
                modifier = Modifier.fillMaxWidth(),
                shape = StudySpaceTokens.cardShape,
                border = BorderStroke(1.dp, StudySpaceTokens.primary.copy(alpha = 0.48f)),
                colors = CardDefaults.outlinedCardColors(containerColor = StudySpaceTokens.primary50)
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = StudySpaceTokens.primary)
                        Text(if (lang == "vi") "TIẾP TỤC HỌC" else "CONTINUE LEARNING", style = MaterialTheme.typography.labelMedium, color = StudySpaceTokens.primary, fontWeight = FontWeight.Bold)
                    }
                    Text(nextCourse.optString("title"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    val courseTotal = nextCourse.optInt("lesson_count").coerceAtLeast(0)
                    val courseDone = nextCourse.optInt("completed_count").coerceIn(0, courseTotal)
                    Text(if (lang == "vi") "$courseDone/$courseTotal bài đã hoàn thành" else "$courseDone of $courseTotal lessons completed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LinearProgressIndicator({ if (courseTotal == 0) 0f else courseDone.toFloat() / courseTotal }, Modifier.fillMaxWidth())
                    Text(if (lang == "vi") "Tiếp tục học →" else "Continue →", color = StudySpaceTokens.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (!online.signedIn) item {
            StudyEmptyState(
                title = if (lang == "vi") "Kết nối để tiếp tục học" else "Connect to continue learning",
                body = if (lang == "vi") "Đăng nhập tài khoản học tập để xem lộ trình và tiến độ đã lưu." else "Sign in to see your courses and saved progress.",
                icon = Icons.AutoMirrored.Filled.MenuBook,
                actionLabel = stringResource(R.string.product_link),
                onAction = onConnect,
                compact = true
            )
        }
        item {
            Card(
                onClick = {
                    if (online.signedIn && weakest != null) {
                        online.generateWeakChapterQuiz()
                        study.navigateTo("learning")
                    } else study.navigateTo("assistant")
                },
                modifier = Modifier.fillMaxWidth(),
                shape = StudySpaceTokens.cardShape,
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(if (lang == "vi") "HÔM NAY NÊN HỌC GÌ" else "WHAT TO STUDY TODAY", style = MaterialTheme.typography.labelMedium, color = StudySpaceTokens.accent200, fontWeight = FontWeight.Bold)
                        AiProviderBadge("offline", lang)
                    }
                    Text(weakest?.optString("title")?.takeIf { it.isNotBlank() } ?: if (lang == "vi") "Bắt đầu với một bài quiz" else "Start with a quiz", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(
                        if (weakest != null) {
                            val score = weakest.optDouble("percentage").toInt()
                            if (lang == "vi") "Dựa trên: $score% ở chương này · Ôn 15 phút để củng cố." else "Based on your $score% chapter score · review for 15 minutes."
                        } else if (lang == "vi") "Dựa trên kết quả quiz đã lưu. Làm một quiz để nhận gợi ý phù hợp." else "Based on your saved quiz results. Take a quiz to get a focused recommendation.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = .78f)
                    )
                    Text(if (lang == "vi") if (weakest != null) "Ôn ngay →" else "Mở trợ lý học tập →" else if (weakest != null) "Review now →" else "Open study assistant →", color = StudySpaceTokens.accent200, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (online.signedIn && (announcements.isNotEmpty() || online.announcementsError != null || online.announcementsLoading)) item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = StudySpaceTokens.cardShape,
                color = StudySpaceTokens.accent50,
                border = BorderStroke(1.dp, StudySpaceTokens.accent600.copy(alpha = .24f))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Campaign, null, tint = StudySpaceTokens.accent600)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.product_class_announcements), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { online.refreshAnnouncements() }, enabled = !online.announcementsLoading) {
                            Text(if (online.announcementsLoading) stringResource(R.string.product_loading_short) else stringResource(R.string.product_refresh_short))
                        }
                    }
                    when {
                        online.announcementsLoading && announcements.isEmpty() -> Text(stringResource(R.string.product_loading_announcements), style = MaterialTheme.typography.bodySmall)
                        online.announcementsError != null && announcements.isEmpty() -> TextButton(onClick = { online.refreshAnnouncements() }) {
                            Text(stringResource(R.string.product_announcements_retry, online.announcementsError.orEmpty()))
                        }
                        announcements.isEmpty() -> Text(stringResource(R.string.product_no_announcements), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else -> announcements.take(3).forEach { notice ->
                            Column(Modifier.fillMaxWidth().clickable { study.navigateTo("groups") }.padding(vertical = 4.dp)) {
                                Text(notice.optString("body"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(
                                    "${notice.optString("teacher_name")} · ${notice.optString("course_title")} · ${Date(notice.optLong("created_at")).let { SimpleDateFormat("HH:mm · dd/MM", Locale.getDefault()).format(it) }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (lang == "vi") "Chuỗi học tập" else "Study streak", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(
                            if (streakDays == 0) {
                                if (lang == "vi") "Học một chút hôm nay để bắt đầu chuỗi." else "Study a little today to start your streak."
                            } else {
                                if (lang == "vi") "Bạn đang duy trì nhịp học đều đặn." else "You are keeping a steady learning rhythm."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Text(
                        if (lang == "vi") "$streakDays ngày" else "$streakDays days",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DashboardMetric(completed.toString(), "bài", stringResource(R.string.product_lessons), performanceLabel, Icons.AutoMirrored.Filled.MenuBook, StudySpaceTokens.primary, StudySpaceTokens.primary50, performanceTint, performanceSurface, Modifier.weight(1f))
                DashboardMetric(history.size.toString(), "quiz", stringResource(R.string.product_quizzes), performanceLabel, Icons.Default.CheckCircle, StudySpaceTokens.accent600, StudySpaceTokens.accent50, performanceTint, performanceSurface, Modifier.weight(1f))
                DashboardMetric(minutes.sum().toInt().toString(), "phút", stringResource(R.string.product_minutes), performanceLabel, Icons.Default.Timer, StudySpaceTokens.success, Color(0xFFECFDF5), performanceTint, performanceSurface, Modifier.weight(1f))
            }
        }
        item {
            OutlinedButton(onClick = { study.navigateTo("assistant") }, modifier = Modifier.fillMaxWidth().height(StudySpaceTokens.buttonHeight), shape = StudySpaceTokens.buttonShape) {
                Icon(Icons.Default.AutoAwesome, null, tint = StudySpaceTokens.primary)
                Spacer(Modifier.width(8.dp))
                Text(if (lang == "vi") "Mở trợ lý học tập AI" else "Open AI study assistant")
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
            }
        }
        item {
            LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(badges, key = { it.id }) { badge ->
                    MilestoneBadge(badgeLabels[badge.id].orEmpty(), badge.achieved)
                }
            }
        }
        item {
            val done = tasks.count { it.isCompleted }
            Surface(shape = StudySpaceTokens.cardShape, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, StudySpaceTokens.border)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (lang == "vi") "MỤC TIÊU TUẦN" else "WEEKLY GOALS", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = StudySpaceTokens.primary, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { study.navigateTo("goals") }) { Text(if (lang == "vi") "Chỉnh sửa" else "Edit") }
                    }
                    DashboardGoal(if (lang == "vi") "Thời gian học" else "Study time", minutes.sum().toInt(), minuteGoal, if (lang == "vi") "phút" else "min", StudySpaceTokens.primary)
                    DashboardGoal(if (lang == "vi") "Quiz đã nộp" else "Quizzes submitted", weeklyQuizzes, quizGoal, if (lang == "vi") "bài" else "quizzes", StudySpaceTokens.accent600)
                    Text(stringResource(R.string.product_plan_summary, done, tasks.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (online.signedIn) item {
            Surface(shape = StudySpaceTokens.cardShape, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, StudySpaceTokens.border)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (lang == "vi") "ĐIỂM QUIZ 6 TUẦN" else "QUIZ SCORES · 6 WEEKS", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = StudySpaceTokens.primary, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { study.navigateTo("reports") }) { Text(if (lang == "vi") "Chi tiết" else "Details") }
                    }
                    if (scoreTrend.any { it != null }) ScoreTrendChart(scoreTrend, lang, chartHeight = 112.dp)
                    else Text(if (lang == "vi") "Làm quiz để bắt đầu theo dõi xu hướng điểm." else "Take a quiz to start tracking your score trend.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (online.signedIn && mastery.isNotEmpty()) item {
            Surface(shape = StudySpaceTokens.cardShape, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, StudySpaceTokens.border)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (lang == "vi") "MỨC NẮM VỮNG THEO CHƯƠNG" else "MASTERY BY CHAPTER", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = StudySpaceTokens.primary, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { study.navigateTo("reports") }) { Text(if (lang == "vi") "Chi tiết" else "Details") }
                    }
                    MasteryRadar(mastery.take(6), lang, chartHeight = 165.dp)
                }
            }
        }
        if (online.signedIn) item {
            Surface(shape = StudySpaceTokens.cardShape, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, StudySpaceTokens.border)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (lang == "vi") "QUIZ GẦN ĐÂY" else "RECENT QUIZZES", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = StudySpaceTokens.primary, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { study.navigateTo("reports") }) { Text(if (lang == "vi") "Xem tất cả" else "View all") }
                    }
                    if (history.isEmpty()) {
                        Text(
                            if (lang == "vi") "Kết quả quiz bạn đã nộp sẽ xuất hiện ở đây." else "Your submitted quiz results will appear here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        history.take(3).forEachIndexed { index, attempt ->
                            val total = attempt.optInt("total")
                            val score = attempt.optInt("score")
                            val percent = if (total > 0) score * 100 / total else 0
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(38.dp).background(StudySpaceTokens.primary.copy(alpha = .09f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("$percent%", style = MaterialTheme.typography.labelMedium, color = StudySpaceTokens.primary, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(attempt.optString("chapter_title", if (lang == "vi") "Bài quiz" else "Quiz"), maxLines = 1, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        SimpleDateFormat("dd/MM · HH:mm", Locale.getDefault()).format(Date(attempt.optLong("submitted_at"))),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text("$score/$total", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            }
                            if (index < minOf(history.size, 3) - 1) HorizontalDivider(color = StudySpaceTokens.border)
                        }
                    }
                }
            }
        }
        if (!online.signedIn) item {
            Text(stringResource(R.string.product_link_note))
            Button(onClick = onConnect, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text(stringResource(R.string.product_link)) }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { study.navigateTo("goals") }, modifier = Modifier.weight(1f), shape = StudySpaceTokens.buttonShape) { Icon(Icons.Default.Insights, null); Spacer(Modifier.width(6.dp)); Text("Mục tiêu tuần", maxLines = 1) }
                OutlinedButton(onClick = { study.navigateTo("reports") }, modifier = Modifier.weight(1f), shape = StudySpaceTokens.buttonShape) { Icon(Icons.Default.BarChart, null); Spacer(Modifier.width(6.dp)); Text("Báo cáo", maxLines = 1) }
            }
        }
        item { SectionTitle(stringResource(R.string.product_today_plan)) }
        val active = tasks.filter { !it.isCompleted }.take(3)
        if (active.isEmpty()) item {
            PlanEmptyState(
                completed = false,
                compact = true,
                onAdd = { study.navigateTo("planner") },
                onBrowse = { study.navigateTo("learning") }
            )
        }
        items(active, key = { it.id }) { task ->
            ListItem(headlineContent = { Text(task.title) }, supportingContent = { Text(stringResource(R.string.product_task_meta, task.targetMinutes, task.category)) }, trailingContent = { IconButton(onClick = { study.selectTaskToFocus(task); study.navigateTo("focus") }) { Icon(Icons.Default.PlayArrow, stringResource(R.string.product_focus)) } }, modifier = Modifier.clickable { study.navigateTo("planner") })
        }
        if (online.signedIn) {
            item { SectionTitle(stringResource(R.string.product_weak)) }
            val weak = online.progress?.optJSONArray("mastery")?.objects().orEmpty().filter { !it.isNull("percentage") && it.getDouble("percentage") < 60 }
            if (weak.isEmpty()) item {
                StudyEmptyState(
                    title = stringResource(R.string.product_no_weak_title),
                    body = stringResource(R.string.product_no_weak),
                    icon = Icons.Default.CheckCircle,
                    compact = true
                )
            }
            items(weak) { chapter ->
                ListItem(headlineContent = { Text(chapter.getString("title")) }, supportingContent = { Text(stringResource(R.string.learning_weak, chapter.getDouble("percentage").toInt())) }, trailingContent = { IconButton(onClick = { online.startQuiz(chapter.getLong("id"), true); study.navigateTo("learning") }) { Icon(Icons.Default.Replay, stringResource(R.string.learning_review)) } })
            }
        }
        item {
            SectionTitle(stringResource(R.string.product_week))
            com.example.WeeklyProgressChart(minutes, lang) { study.navigateTo("focus") }
            Text(stringResource(R.string.product_week_completed, weekly.completedPlans), style = MaterialTheme.typography.bodyMedium)
        }
    }
    SnackbarHost(badgeSnackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp))
    }
}

@Composable fun StudyPlanScreen(study: StudyViewModel, online: LearningViewModel) {
    val lang by study.language
    val tasks by study.allTasks.collectAsState()
    val aiPlanSaving by study.aiPlanSaving
    var completed by rememberSaveable { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var showAiPlanner by rememberSaveable { mutableStateOf(false) }
    var aiCategory by rememberSaveable { mutableStateOf("Lập trình") }
    var aiGoal by rememberSaveable { mutableStateOf("") }
    var aiDailyMinutes by rememberSaveable { mutableStateOf("60") }
    var aiExamDate by rememberSaveable {
        mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 14) }.time))
    }
    var deleting by remember { mutableStateOf<StudyTask?>(null) }
    var pendingCelebrationId by remember { mutableStateOf<Int?>(null) }
    var showPlanCelebration by remember { mutableStateOf(false) }
    var pendingReminderAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showReminderPermissionRationale by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val planSnackbar = remember { SnackbarHostState() }
    val planSnackbarScope = rememberCoroutineScope()
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pendingReminderAction
        pendingReminderAction = null
        action?.invoke()
        if (!granted) study.showPushNotification(context.getString(R.string.product_reminder_permission_denied))
    }
    fun withReminderPermission(action: () -> Unit): () -> Unit = {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) action() else {
            pendingReminderAction = action
            showReminderPermissionRationale = true
        }
    }
    LaunchedEffect(tasks, pendingCelebrationId) {
        val taskId = pendingCelebrationId ?: return@LaunchedEffect
        if (tasks.any { it.id == taskId && it.isCompleted }) {
            showPlanCelebration = true
            delay(1800)
            showPlanCelebration = false
            pendingCelebrationId = null
        }
    }
    if (showReminderPermissionRationale) AlertDialog(
        onDismissRequest = {
            showReminderPermissionRationale = false
            pendingReminderAction?.invoke()
            pendingReminderAction = null
            study.showPushNotification(context.getString(R.string.product_reminder_permission_denied))
        },
        title = { Text(stringResource(R.string.product_reminder_permission_title)) },
        text = { Text(stringResource(R.string.product_reminder_permission_body)) },
        confirmButton = { TextButton(onClick = {
            showReminderPermissionRationale = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }) { Text(stringResource(R.string.product_reminder_permission_allow)) } },
        dismissButton = { TextButton(onClick = {
            showReminderPermissionRationale = false
            pendingReminderAction?.invoke()
            pendingReminderAction = null
            study.showPushNotification(context.getString(R.string.product_reminder_permission_denied))
        }) { Text(stringResource(R.string.product_reminder_permission_later)) } }
    )
    if (creating) TaskEditor(onDismiss = { creating = false }) { title, subject, minutes, dueAt ->
        creating = false
        withReminderPermission {
            study.createTask(title, subject, "", minutes, dueAt) {
                planSnackbarScope.launch { planSnackbar.showSnackbar(context.getString(R.string.product_plan_saved)) }
            }
        }()
    }
    deleting?.let { task -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.product_delete)) }, text = { Text(stringResource(R.string.product_delete_confirm,task.title)) }, confirmButton = { TextButton(onClick = { study.deleteTask(task); deleting = null }) { Text(stringResource(R.string.product_delete)) } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.product_cancel)) } }) }
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().padding(horizontal = StudySpaceTokens.spaceMedium)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.product_plan), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { showAiPlanner = !showAiPlanner }) { Icon(Icons.Default.AutoAwesome, stringResource(R.string.product_ai_plan)) }
            IconButton(onClick = { creating = true }) { Icon(Icons.Default.Add, stringResource(R.string.product_add_task)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val chipColors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
            FilterChip(
                selected = !completed,
                onClick = { completed = false },
                label = { Text(stringResource(R.string.product_active)) },
                shape = MaterialTheme.shapes.small,
                colors = chipColors
            )
            FilterChip(
                selected = completed,
                onClick = { completed = true },
                label = { Text(stringResource(R.string.product_completed)) },
                shape = MaterialTheme.shapes.small,
                colors = chipColors
            )
        }
        val visible = tasks.filter { it.isCompleted == completed }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(top = 16.dp, bottom = 128.dp)) {
            if (showAiPlanner) item(key = "ai-plan-form") {
                OutlinedCard(modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.product_ai_plan), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.product_ai_plan_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        online.aiStudyPlanProvider?.let { AiProviderBadge(it, lang) }
                        OutlinedTextField(aiCategory, { aiCategory = it.take(40) }, label = { Text(stringResource(R.string.product_subject)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(aiGoal, { aiGoal = it.take(160) }, label = { Text(stringResource(R.string.product_ai_plan_goal)) }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 3)
                        OutlinedTextField(aiExamDate, { aiExamDate = it.take(10) }, label = { Text(stringResource(R.string.product_ai_plan_exam_date)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(aiDailyMinutes, { aiDailyMinutes = it.filter(Char::isDigit).take(3) }, label = { Text(stringResource(R.string.product_ai_plan_daily_minutes)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        online.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        Button(
                            onClick = { online.generateStudyPlan(aiCategory, aiGoal, aiExamDate, aiDailyMinutes.toIntOrNull() ?: 0) },
                            enabled = !online.busy && online.signedIn && aiCategory.isNotBlank() && aiGoal.isNotBlank() && (aiDailyMinutes.toIntOrNull() ?: 0) in 10..360,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (online.busy) CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            else Text(stringResource(if (online.signedIn) R.string.product_ai_plan_generate else R.string.product_ai_signin_button))
                        }
                        if (online.aiStudyPlanFallback) Text(stringResource(R.string.product_ai_plan_fallback), color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
                        online.aiStudyPlan.forEach { suggested ->
                            ListItem(
                                leadingContent = { Icon(Icons.AutoMirrored.Filled.EventNote, null, tint = MaterialTheme.colorScheme.primary) },
                                headlineContent = { Text(suggested.title, style = MaterialTheme.typography.titleSmall) },
                                supportingContent = { Text("${java.text.DateFormat.getDateInstance().format(Date(suggested.dueDate))} · ${suggested.targetMinutes} phút\n${suggested.description}", maxLines = 4) }
                            )
                        }
                        if (online.aiStudyPlan.isNotEmpty()) {
                            Button(onClick = withReminderPermission {
                                study.saveGeneratedStudyPlan(online.aiStudyPlan) {
                                    online.clearStudyPlan()
                                    showAiPlanner = false
                                    planSnackbarScope.launch { planSnackbar.showSnackbar(context.getString(R.string.product_plan_saved)) }
                                }
                            }, enabled = !aiPlanSaving, modifier = Modifier.fillMaxWidth()) {
                                if (aiPlanSaving) CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                else Text(stringResource(R.string.product_ai_plan_save))
                            }
                            TextButton(onClick = online::clearStudyPlan, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.product_cancel)) }
                        }
                    }
                }
            }
            item(key = "plan-celebration") {
                AnimatedVisibility(
                    visible = showPlanCelebration,
                    enter = fadeIn() + scaleIn(initialScale = 0.94f),
                    exit = fadeOut() + scaleOut(targetScale = 0.96f)
                ) {
                    CelebrationNotice(if (study.language.value == "vi") "Tốt lắm! Kế hoạch đã hoàn thành." else "Nice work! Plan completed.")
                }
            }
            if (visible.isEmpty()) item {
                PlanEmptyState(
                    completed = completed,
                    onAdd = { creating = true },
                    onBrowse = { study.navigateTo("learning") }
                )
            }
            items(visible,key = { it.id }) { task ->
                OutlinedCard(
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                        1.dp,
                        if (completed) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    ),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = if (completed) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(task.title, style = MaterialTheme.typography.titleMedium, color = if (completed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(stringResource(R.string.product_task_meta,task.targetMinutes,task.category), style = MaterialTheme.typography.bodySmall, color = if (completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                        Text(stringResource(R.string.product_task_due, java.text.DateFormat.getDateTimeInstance().format(Date(task.dueDate))), style = MaterialTheme.typography.labelSmall, color = if (completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            val lessonId = task.description.removePrefix("studyspace://lesson/").toLongOrNull()
                            if (lessonId != null && online.signedIn) IconButton(onClick = { online.findLesson(lessonId); study.navigateTo("learning") }) { Icon(Icons.AutoMirrored.Filled.MenuBook,stringResource(R.string.product_open_lesson)) }
                            if (!completed) {
                                IconButton(onClick = { study.selectTaskToFocus(task); study.navigateTo("focus") }) { Icon(Icons.Default.PlayArrow,stringResource(R.string.product_focus)) }
                                IconButton(onClick = { pendingCelebrationId = task.id; study.completePlannedTask(task) }) { Icon(Icons.Default.CheckCircle,stringResource(R.string.product_mark_done), tint = MaterialTheme.colorScheme.primary) }
                            }
                            IconButton(onClick = { deleting = task }) { Icon(Icons.Default.Delete,stringResource(R.string.product_delete)) }
                        }
                    }
                }
            }
        }
    }
    SnackbarHost(planSnackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp))
    }
}

@Composable private fun TaskEditor(onDismiss: () -> Unit, save: (String,String,Int,Long) -> Unit) {
    var title by remember { mutableStateOf("") }; var subject by remember { mutableStateOf("") }; var minutes by remember { mutableStateOf("25") }
    var dueAt by rememberSaveable { mutableLongStateOf(System.currentTimeMillis() + 86400000L * 3) }
    val context = LocalContext.current
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.product_add_task)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(title,{ title=it },label = { Text(stringResource(R.string.product_task_title)) },maxLines=3)
            OutlinedTextField(subject,{subject=it},label = { Text(stringResource(R.string.product_subject)) },singleLine=true)
            OutlinedTextField(minutes,{minutes=it.filter(Char::isDigit).take(3)},label = { Text(stringResource(R.string.product_duration)) },singleLine=true)
            OutlinedButton(onClick = {
                val initial = Calendar.getInstance().apply { timeInMillis = dueAt }
                DatePickerDialog(context, { _, year, month, day ->
                    val selected = Calendar.getInstance().apply {
                        timeInMillis = dueAt
                        set(Calendar.YEAR, year); set(Calendar.MONTH, month); set(Calendar.DAY_OF_MONTH, day)
                    }
                    TimePickerDialog(context, { _, hour, minute ->
                        selected.set(Calendar.HOUR_OF_DAY, hour); selected.set(Calendar.MINUTE, minute)
                        selected.set(Calendar.SECOND, 0); selected.set(Calendar.MILLISECOND, 0)
                        dueAt = selected.timeInMillis
                    }, initial.get(Calendar.HOUR_OF_DAY), initial.get(Calendar.MINUTE), true).show()
                }, initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH)).apply {
                    datePicker.minDate = System.currentTimeMillis()
                }.show()
            }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.product_task_due, java.text.DateFormat.getDateTimeInstance().format(Date(dueAt))))
            }
        }
    }, confirmButton = { TextButton(onClick = {save(title.trim(),subject.trim(),minutes.toInt(),dueAt)},enabled = title.isNotBlank() && (minutes.toIntOrNull() ?: 0) in 5..240 && dueAt > System.currentTimeMillis()) { Text(stringResource(R.string.product_save)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.product_cancel)) } })
}

@Composable fun ProductAccount(study: StudyViewModel, online: LearningViewModel, onConnect: () -> Unit) {
    val user by study.currentUser; val dark by study.isDarkMode; val biometrics by study.isBiometricsEnabled
    val context = LocalContext.current; val uri = LocalUriHandler.current
    var confirm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var displayName by remember(user?.username) { mutableStateOf(user?.displayName.orEmpty()) }
    if (editing) AlertDialog(onDismissRequest={editing=false}, title={Text(stringResource(R.string.product_account_details))}, text={OutlinedTextField(displayName,{displayName=it.take(120)},label={Text(stringResource(R.string.product_display_name))})}, confirmButton={TextButton(onClick={
        if(online.signedIn) online.updateDisplayName(displayName){study.changeDisplayName(displayName);editing=false}
        else {study.changeDisplayName(displayName);editing=false}
    },enabled=displayName.isNotBlank()&&!online.busy){Text(stringResource(R.string.product_save))}},dismissButton={TextButton(onClick={editing=false}){Text(stringResource(R.string.product_cancel))}})
    if (confirm) AlertDialog(onDismissRequest = {confirm=false},title = {Text(stringResource(R.string.product_signout))},text = {Text(stringResource(R.string.product_signout_confirm))},confirmButton = {TextButton(onClick = {online.logout();study.logout();confirm=false}){Text(stringResource(R.string.product_signout))}},dismissButton={TextButton(onClick={confirm=false}){Text(stringResource(R.string.product_cancel))}})
    val completeParts = listOf(user?.displayName?.isNotBlank() == true, online.signedIn, biometrics, study.allTasks.collectAsState().value.isNotEmpty())
    val completion = completeParts.count { it } * 25
    val lang by study.language
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = StudySpaceTokens.spaceMedium),
        verticalArrangement = Arrangement.spacedBy(StudySpaceTokens.spaceMedium),
        contentPadding = PaddingValues(top = 20.dp, bottom = 120.dp)
    ) {
        item {
            Text(if (lang == "vi") "Hồ sơ" else "Profile", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Surface(shape = StudySpaceTokens.cardShape, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, StudySpaceTokens.border)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(44.dp).background(StudySpaceTokens.primary, CircleShape), contentAlignment = Alignment.Center) {
                            Text(user?.displayName?.firstOrNull()?.uppercase() ?: "S", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text(user?.displayName.orEmpty(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(if (online.signedIn) stringResource(R.string.product_online, online.onlineUsername) else stringResource(R.string.product_signin_required), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (lang == "vi") "Hoàn thiện hồ sơ" else "Profile completion", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                        Text("$completion%", color = StudySpaceTokens.primary, fontWeight = FontWeight.Bold)
                    }
                    LinearProgressIndicator({ completion / 100f }, Modifier.fillMaxWidth())
                }
            }
        }
        item {
            TabRow(selectedTabIndex = selectedTab) {
                listOf("Cá nhân", "Mục tiêu", "Bảo mật").forEachIndexed { index, title ->
                    Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title, maxLines = 1) })
                }
            }
        }
        when (selectedTab) {
            0 -> {
                item { ProfileAction(Icons.Default.Edit, "Thông tin cá nhân", user?.displayName.orEmpty(), onClick = { editing = true }) }
                if (!online.signedIn) item { Button(onClick = onConnect, modifier = Modifier.fillMaxWidth().height(StudySpaceTokens.buttonHeight), shape = StudySpaceTokens.buttonShape) { Text(stringResource(R.string.product_link)) } }
                if (online.onlineRole == "ADMIN") item { ProfileAction(Icons.Default.AdminPanelSettings, stringResource(R.string.product_admin), "", onClick = { uri.openUri(online.endpoint) }) }
                if (online.onlineRole == "TEACHER") item {
                    ProfileAction(Icons.Default.School, stringResource(R.string.teacher_manage_content), "", onClick = {
                        online.openTeacherContent { link -> runCatching { uri.openUri(link) }.onFailure { study.showPushNotification(it.message ?: "Không mở được trang giảng viên.") } }
                    })
                }
                item { ProfileAction(Icons.Default.DarkMode, stringResource(R.string.product_appearance), stringResource(R.string.product_dark), trailing = { Switch(dark, { study.toggleDarkMode() }) }) }
                if (online.signedIn) item { ProfileAction(Icons.Default.Groups, stringResource(R.string.product_community), "", onClick = { study.navigateTo("groups") }) }
            }
            1 -> {
                item { ProfileAction(Icons.Default.Insights, "Mục tiêu tuần", "Phút học và số quiz", onClick = { study.navigateTo("goals") }) }
                item { Text("Đặt nhịp học có thể duy trì, rồi xem tiến độ theo tuần.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            else -> {
                item { ProfileAction(Icons.Default.Fingerprint, stringResource(R.string.product_biometric), "", trailing = { Switch(biometrics, { enabled ->
                    if (!enabled) study.toggleBiometrics() else (context as? com.example.MainActivity)?.authenticateWithBiometrics(onSuccess = { study.toggleBiometrics() }, onError = { study.showPushNotification(it) })
                }) }) }
                item { TextButton(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.product_signout), color = MaterialTheme.colorScheme.error) } }
            }
        }
    }
}

@Composable
private fun ProfileAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
    onClick: () -> Unit = {},
    trailing: (@Composable () -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(enabled = trailing == null, onClick = onClick),
        shape = StudySpaceTokens.controlShape,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(38.dp).background(StudySpaceTokens.primary50, StudySpaceTokens.controlShape), contentAlignment = Alignment.Center) { Icon(icon, null, tint = StudySpaceTokens.primary) }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            trailing?.invoke() ?: Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

package com.example.learning

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.ui.components.StudyEmptyState
import com.example.ui.theme.StudySpaceTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

@Composable
fun LearningScreen(owner: String, vm: LearningViewModel = viewModel(), study: com.example.ui.viewmodel.StudyViewModel, onConnect: () -> Unit) {
    LaunchedEffect(owner) { vm.bindOwner(owner) }
    val lang by study.language
    val hasBack = vm.course != null || vm.attempt != null || vm.showProgress
    BackHandler(hasBack && !vm.busy) { vm.back() }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (hasBack) IconButton(onClick = vm::back, enabled = !vm.busy) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.learning_back)) }
            Text(stringResource(R.string.learning_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (vm.signedIn && vm.attempt == null) {
                IconButton(onClick = vm::openProgress, enabled = !vm.busy) { Icon(Icons.Default.BarChart, stringResource(R.string.learning_progress)) }
                IconButton(onClick = vm::refresh, enabled = !vm.busy) { Icon(Icons.Default.Refresh, stringResource(R.string.learning_refresh)) }
            }
        }
        if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp)) }
        if (vm.offline && vm.signedIn) Text(stringResource(R.string.learning_offline), style = MaterialTheme.typography.bodySmall)
        when {
            !vm.signedIn -> Column(Modifier.padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.product_link_note))
                Button(onClick = onConnect) { Text(stringResource(R.string.product_link)) }
            }
            vm.attempt != null -> QuizScreen(vm, lang)
            vm.lesson != null -> LessonScreen(vm, study)
            vm.course != null -> CourseScreen(vm)
            vm.showProgress -> ProgressScreen(vm)
            else -> CourseCatalog(vm)
        }
    }
}

@Composable private fun CourseCatalog(vm: LearningViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(query, vm.courses) {
        if (query.isNotBlank()) vm.loadCatalogSearchDetails()
    }
    val normalizedQuery = query.trim()
    val filtered = vm.courses.mapNotNull { c ->
        val courseId = c.optLong("id")
        val detail = vm.catalogSearchDetails[courseId]
        val matchedSections = if (normalizedQuery.isBlank()) emptyList() else {
            detail?.optJSONArray("chapters")?.objects().orEmpty().flatMap { chapter ->
                val chapterTitle = chapter.optString("title")
                buildList {
                    if (chapterTitle.contains(normalizedQuery, true)) add("Chương: $chapterTitle")
                    chapter.optJSONArray("lessons")?.objects().orEmpty()
                        .filter { it.optString("title").contains(normalizedQuery, true) }
                        .forEach { add("${chapterTitle} · ${it.optString("title")}") }
                }
            }.distinct().take(3)
        }
        val courseMatches = normalizedQuery.isBlank() || c.optString("title").contains(normalizedQuery, true)
        val matchesQuery = courseMatches || matchedSections.isNotEmpty()
        val state = courseProgressState(c.optInt("completed_count"), c.optInt("lesson_count"))
        val matchesFilter = when(filter) {
            1 -> state == CourseProgressState.IN_PROGRESS
            2 -> state == CourseProgressState.COMPLETE
            else -> true
        }
        if (matchesQuery && matchesFilter) c to matchedSections else null
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(query, { query = it }, placeholder = { Text(stringResource(R.string.learning_search_hint)) }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(R.string.product_all, R.string.product_in_progress, R.string.product_completed).forEachIndexed { index, label ->
                FilterChip(selected = filter == index, onClick = { filter = index }, label = { Text(stringResource(label), style = MaterialTheme.typography.labelSmall) }, shape = MaterialTheme.shapes.small)
            }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(StudySpaceTokens.spaceMedium), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (normalizedQuery.isNotEmpty() && vm.catalogSearchLoading) item {
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(Modifier.weight(1f))
                        Text(stringResource(R.string.learning_searching), style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (vm.courses.isEmpty()) item {
                    StudyEmptyState(
                        title = stringResource(R.string.product_empty_courses_title),
                        body = stringResource(R.string.product_empty_courses_body),
                        icon = Icons.Default.School,
                        actionLabel = stringResource(R.string.product_refresh),
                        onAction = vm::refresh
                    )
                }
                else if (filtered.isEmpty() && !vm.catalogSearchLoading) item {
                    StudyEmptyState(
                        title = stringResource(R.string.product_empty_search_title),
                        body = stringResource(R.string.product_empty_search_body),
                        icon = Icons.Default.SearchOff,
                        actionLabel = stringResource(R.string.product_empty_search_cta),
                        onAction = { query = ""; filter = 0 },
                        compact = true
                    )
                }
                items(filtered, key = { it.first.getLong("id") }) { (c, matchedSections) ->
                    OutlinedCard(onClick = { vm.openCourse(c.getLong("id")) }, enabled = !vm.busy, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = MaterialTheme.colorScheme.primary)
                            Text(c.getString("title"), style = MaterialTheme.typography.titleLarge)
                            Text(c.getString("description"), style = MaterialTheme.typography.bodyMedium)
                            matchedSections.forEach { match ->
                                Text(match, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 2)
                            }
                            val total = c.getInt("lesson_count"); val done = c.getInt("completed_count")
                            val state = courseProgressState(done, total)
                            val stateLabel = stringResource(when (state) {
                                CourseProgressState.NOT_STARTED -> R.string.learning_status_not_started
                                CourseProgressState.IN_PROGRESS -> R.string.learning_status_in_progress
                                CourseProgressState.COMPLETE -> R.string.learning_status_completed
                            })
                            val stateColor = when (state) {
                                CourseProgressState.NOT_STARTED -> MaterialTheme.colorScheme.outline
                                CourseProgressState.IN_PROGRESS -> MaterialTheme.colorScheme.primary
                                CourseProgressState.COMPLETE -> MaterialTheme.colorScheme.tertiary
                            }
                            Surface(color = stateColor.copy(alpha = 0.12f), shape = RoundedCornerShape(50)) {
                                Text(stateLabel, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = stateColor, style = MaterialTheme.typography.labelMedium)
                            }
                            LinearProgressIndicator(progress = { if (total == 0) 0f else done.toFloat()/total }, modifier = Modifier.fillMaxWidth())
                            Text(stringResource(R.string.learning_course_count, done, total), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
    }
}

@Composable private fun CourseScreen(vm: LearningViewModel) {
    val course = vm.course!!
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Text(course.getString("title"), style = MaterialTheme.typography.headlineSmall) }
        item { Text(course.getString("description")) }
        course.getJSONArray("chapters").objects().forEach { chapter ->
            item { HorizontalDivider(Modifier.padding(top = 12.dp)); Text(chapter.getString("title"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp)) }
            items(chapter.getJSONArray("lessons").objects(), key = { "lesson:${it.getLong("id")}" }) { lesson ->
                val locked=lesson.optBoolean("locked",false)
                ListItem(headlineContent = { Text(lesson.getString("title")) }, leadingContent = { Icon(when { lesson.getBoolean("completed") -> Icons.Default.CheckCircle; locked -> Icons.Default.Lock; else -> Icons.AutoMirrored.Filled.Article }, null) }, supportingContent = when { lesson.getBoolean("completed") -> {{ Text(stringResource(R.string.learning_done)) }}; locked -> {{ Text(stringResource(R.string.learning_locked_until_previous)) }}; else -> null }, modifier = Modifier.clickable(enabled=!locked&&!vm.busy) { vm.openLesson(lesson) })
            }
            item {
                if (chapter.getInt("question_count") == 0) Text(stringResource(R.string.learning_no_quiz), style = MaterialTheme.typography.bodySmall)
                else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.startQuiz(chapter.getLong("id"), false) }, enabled = !vm.busy) { Text(stringResource(R.string.learning_quiz)) }
                    OutlinedButton(onClick = { vm.startQuiz(chapter.getLong("id"), true) }, enabled = !vm.busy) { Text(stringResource(R.string.learning_review)) }
                }
            }
        }
    }
}

@Composable private fun LessonScreen(vm: LearningViewModel, study: com.example.ui.viewmodel.StudyViewModel) {
    val lesson = vm.lesson!!; val uri = LocalUriHandler.current
    val lang by study.language
    var chatQuestion by rememberSaveable(lesson.getLong("id")) { mutableStateOf("") }
    val chatMessages = vm.lessonChatMessages
    val chatLoading = vm.lessonChatLoading
    LazyColumn(verticalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Text(lesson.getString("title"), style = MaterialTheme.typography.headlineSmall) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { study.planLesson(lesson.getLong("id"), lesson.getString("title"), vm.course!!.getString("title"), false) }) { Icon(Icons.AutoMirrored.Filled.EventNote, stringResource(R.string.product_add_plan)) }
                OutlinedButton(onClick = { study.planLesson(lesson.getLong("id"), lesson.getString("title"), vm.course!!.getString("title"), true) }) { Icon(Icons.Default.Timer,null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.product_focus_lesson)) }
            }
        }
        item { Text(lesson.getString("content"), style = MaterialTheme.typography.bodyLarge) }
        item {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.learning_lesson_chat), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Text(stringResource(R.string.learning_lesson_chat_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    chatMessages.forEach { message ->
                        Surface(
                            color = if (message.isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(stringResource(if (message.isUser) R.string.learning_lesson_chat_you else R.string.learning_lesson_chat_assistant), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                if (!message.isUser) message.provider?.let { AiProviderBadge(it, lang) }
                                Text(message.text, style = MaterialTheme.typography.bodyMedium)
                                if (message.offline) Text(stringResource(R.string.learning_lesson_chat_fallback), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                            }
                        }
                    }
                    if (chatLoading) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.learning_lesson_chat_loading), style = MaterialTheme.typography.bodySmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = chatQuestion,
                            onValueChange = { chatQuestion = it.take(500) },
                            placeholder = { Text(stringResource(R.string.learning_lesson_chat_hint)) },
                            modifier = Modifier.weight(1f),
                            maxLines = 3
                        )
                        IconButton(
                            onClick = { vm.askLesson(chatQuestion); chatQuestion = "" },
                            enabled = chatQuestion.isNotBlank() && !chatLoading
                        ) { Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.learning_lesson_chat_send), tint = MaterialTheme.colorScheme.primary) }
                    }
                }
            }
        }
        if (lesson.optString("material_url").startsWith("https://") || lesson.optString("material_url").startsWith("/materials/")) item { OutlinedButton(onClick = { runCatching { val link=lesson.getString("material_url"); uri.openUri(if(link.startsWith("/")) vm.endpoint+link else link) } }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.learning_material)) } }
        item { Button(onClick = { vm.completeLesson(study::completeLessonPlan) }, enabled = !vm.busy && !lesson.getBoolean("completed"), modifier = Modifier.fillMaxWidth()) { Text(stringResource(if (lesson.getBoolean("completed")) R.string.learning_done else R.string.learning_complete)) } }
    }
}

@Composable private fun QuizScreen(vm: LearningViewModel, lang: String) {
    val attempt = vm.attempt!!; val submitted = !attempt.isNull("submitted_at")
    val questions = attempt.getJSONArray("questions").objects()
    val attemptId = attempt.getLong("id")
    val totalScore = attempt.optInt("total")
    val score = attempt.optInt("score")
    val highScore = submitted && totalScore > 0 && score.toFloat() / totalScore >= 0.8f
    var showCelebration by remember(attemptId) { mutableStateOf(false) }
    var remaining by remember(attemptId) { mutableIntStateOf(vm.secondsRemaining()) }
    var confirm by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val quizSnackbar = remember { SnackbarHostState() }
    val quizSnackbarScope = rememberCoroutineScope()
    val submittedMessage = stringResource(R.string.learning_quiz_submitted)
    fun submitQuiz() {
        vm.submit { quizSnackbarScope.launch { quizSnackbar.showSnackbar(submittedMessage) } }
    }
    LaunchedEffect(attemptId, highScore) {
        if (highScore) {
            showCelebration = true
            delay(2200)
            showCelebration = false
        }
    }
    LaunchedEffect(submitted) { if (submitted) listState.scrollToItem(0) }
    LaunchedEffect(attempt.getLong("id"), submitted) {
        while (!submitted) { remaining = vm.secondsRemaining(); delay(1000) }
    }
    LaunchedEffect(remaining == 0, submitted) {
        if (remaining == 0 && !submitted) {
            while (vm.busy) delay(100)
            submitQuiz()
        }
    }
    LaunchedEffect(vm.answers, submitted) {
        if (!submitted && vm.answers.isNotEmpty() && remaining > 0) { delay(600); vm.saveAnswers() }
    }
    val unanswered = questions.filter { it.isNull("selected_option") }.joinToString(", ") { (it.getInt("position") + 1).toString() }.ifBlank { stringResource(R.string.learning_none) }
    val marked = questions.filter { it.getLong("question_id") in vm.markedQuestionIds }.joinToString(", ") { (it.getInt("position") + 1).toString() }.ifBlank { stringResource(R.string.learning_none) }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text(stringResource(R.string.learning_submit)) }, text = { Text(stringResource(R.string.learning_submit_review, unanswered, marked)) }, confirmButton = { TextButton(onClick = { confirm = false; submitQuiz() }) { Text(stringResource(R.string.learning_submit)) } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.learning_cancel)) } })
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            if (submitted) {
                Text(stringResource(R.string.learning_result, score, totalScore), style = MaterialTheme.typography.headlineSmall)
                AnimatedVisibility(
                    visible = showCelebration,
                    enter = fadeIn() + scaleIn(initialScale = 0.92f),
                    exit = fadeOut() + scaleOut(targetScale = 0.96f)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                            Text(stringResource(R.string.learning_celebration), color = MaterialTheme.colorScheme.onTertiaryContainer, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            else {
                Text(stringResource(R.string.learning_timer, remaining / 60, remaining % 60), style = MaterialTheme.typography.titleLarge)
                vm.reviewQuizProvider?.let { AiProviderBadge(it, lang) }
                Text(stringResource(R.string.learning_answered, vm.answers.size, questions.size))
                if (remaining in 1..60) Text(stringResource(R.string.learning_quiz_warning), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                if (remaining == 0) Text(stringResource(R.string.learning_expired), color = MaterialTheme.colorScheme.error)
            }
        }
        if (!submitted) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            questions.forEachIndexed { index, question ->
                val id = question.getLong("question_id")
                val answered = !question.isNull("selected_option") || vm.answers.containsKey(id.toString())
                val marked = id in vm.markedQuestionIds
                OutlinedButton(onClick = { quizSnackbarScope.launch { listState.animateScrollToItem(index) } }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)) {
                    Text("${index + 1}${if (marked) " ★" else if (answered) " ✓" else ""}")
                }
            }
        }
        LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
        items(questions, key = { it.getLong("question_id") }) { q ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${q.getInt("position") + 1}. ${q.getString("prompt")}", fontWeight = FontWeight.SemiBold)
                val options = listOf("option_a", "option_b", "option_c", "option_d")
                options.forEachIndexed { index, key ->
                    val selected = if (submitted) q.optInt("selected_option", -1) == index else vm.answers[q.getLong("question_id").toString()] == index
                    Row(Modifier.fillMaxWidth().selectable(selected, enabled = !submitted && remaining > 0 && !vm.busy, role = Role.RadioButton, onClick = { vm.choose(q.getLong("question_id").toString(), index) }).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected, onClick = null, enabled = !submitted && remaining > 0)
                        Text("${'A' + index}. ${q.getString(key)}", modifier = Modifier.padding(start = 8.dp))
                    }
                }
                if (!submitted) TextButton(onClick = { vm.toggleQuestionMark(q.getLong("question_id")) }, enabled = remaining > 0) {
                    Icon(if (q.getLong("question_id") in vm.markedQuestionIds) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(if (q.getLong("question_id") in vm.markedQuestionIds) R.string.learning_unmark_review else R.string.learning_mark_review))
                }
                if (submitted) {
                    val correctOption = q.getInt("correct_option")
                    val selectedOption = q.optInt("selected_option", -1)
                    val questionId = q.getLong("question_id").toString()
                    Text(stringResource(R.string.learning_correct, ('A' + correctOption).toString()), color = MaterialTheme.colorScheme.primary)
                    vm.aiExplanationProviders[questionId]?.let { AiProviderBadge(it, lang) }
                    Text(vm.aiExplanations[questionId] ?: q.getString("explanation"), style = MaterialTheme.typography.bodyMedium)
                    if (selectedOption >= 0 && selectedOption != correctOption) {
                        TextButton(
                            onClick = { vm.explainWrongAnswer(q) },
                            enabled = vm.aiExplanationLoadingId == null && questionId !in vm.aiExplanations && !vm.busy
                        ) {
                            if (vm.aiExplanationLoadingId == questionId) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(if (questionId in vm.aiExplanations) R.string.learning_ai_explained else R.string.learning_ai_explain))
                        }
                        if (vm.aiExplanationFallbackId == questionId) {
                            Text(stringResource(R.string.learning_ai_explain_fallback), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                HorizontalDivider()
            }
        }
        if (!submitted) item {
            Button(onClick = { confirm = true }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.learning_submit)) }
            Text(stringResource(R.string.learning_leave_quiz), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
        }
        }
    }
    SnackbarHost(quizSnackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
    }
}

private fun masteryColor(score: Int): Color {
    val value = score.coerceIn(0, 100)
    val weak = Color(0xFFE57373)
    val average = Color(0xFFD4AA32)
    val strong = Color(0xFF00796B)
    return if (value <= 50) lerp(weak, average, value / 50f) else lerp(average, strong, (value - 50) / 50f)
}

@Composable private fun ProgressScreen(vm: LearningViewModel) {
    val data = vm.progress ?: return
    val weakestChapter = data.getJSONArray("mastery").objects()
        .filterNot { it.isNull("percentage") }
        .minByOrNull { it.getDouble("percentage") }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Text(stringResource(R.string.learning_mastery), style = MaterialTheme.typography.titleLarge) }
        if (weakestChapter != null) item {
            Button(onClick = vm::generateWeakChapterQuiz, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
                if (vm.busy) CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                else Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.learning_ai_review_quiz))
            }
        }
        items(data.getJSONArray("mastery").objects()) { chapter ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(chapter.getString("title"), fontWeight = FontWeight.SemiBold)
                if (chapter.isNull("percentage")) Text(stringResource(R.string.learning_no_data), style = MaterialTheme.typography.bodySmall)
                else {
                    val score = chapter.getDouble("percentage").toInt()
                    val statusColor = masteryColor(score)
                    LinearProgressIndicator(
                        progress = { score / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = statusColor,
                        trackColor = statusColor.copy(alpha = 0.2f)
                    )
                    Text(stringResource(if (score < 60) R.string.learning_weak else R.string.learning_score, score), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { HorizontalDivider(); Text(stringResource(R.string.learning_history), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp)) }
        val history = data.getJSONArray("history").objects()
        if (history.isEmpty()) item {
            StudyEmptyState(
                title = stringResource(R.string.product_empty_history_title),
                body = stringResource(R.string.product_empty_history_body),
                icon = Icons.Default.History,
                compact = true
            )
        }
        items(history, key = { it.getLong("id") }) { a ->
            OutlinedCard(onClick = { vm.openAttempt(a.getLong("id")) }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(a.getString("chapter_title"), fontWeight = FontWeight.SemiBold)
                    Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(a.getLong("started_at"))), style = MaterialTheme.typography.bodySmall)
                    Text(if (a.isNull("submitted_at")) stringResource(R.string.learning_pending) else stringResource(R.string.learning_history_score, a.getInt("score"), a.getInt("total")))
                }
            }
        }
    }
}

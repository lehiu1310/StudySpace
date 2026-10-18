package com.example.learning

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.viewmodel.StudyViewModel

@Composable
fun StudyOnboardingScreen(study: StudyViewModel, online: LearningViewModel) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var selectedCourseId by rememberSaveable { mutableLongStateOf(0L) }
    var subject by rememberSaveable { mutableStateOf("") }
    var dailyMinutes by rememberSaveable { mutableIntStateOf(60) }
    var selectedLessonId by rememberSaveable { mutableLongStateOf(0L) }
    var selectedLessonTitle by rememberSaveable { mutableStateOf("") }
    var selectedCourseTitle by rememberSaveable { mutableStateOf("") }
    val saving by study.onboardingSaving
    val selectedCourse = online.course?.takeIf { it.optLong("id") == selectedCourseId }
    val selectableLessons = selectedCourse?.optJSONArray("chapters")?.objects().orEmpty().flatMap { chapter ->
        chapter.getJSONArray("lessons").objects().filterNot { it.optBoolean("locked") }
            .map { chapter.optString("title") to it }
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxWidth().widthIn(max = 560.dp).verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.material3.Icon(Icons.Default.School, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                Column {
                    Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.onboarding_step, step + 1), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LinearProgressIndicator(progress = { (step + 1) / 3f }, modifier = Modifier.fillMaxWidth())

            when (step) {
                0 -> {
                    Text(stringResource(R.string.onboarding_subject_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.onboarding_subject_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (online.courses.isEmpty()) {
                        Text(stringResource(R.string.onboarding_courses_loading), style = MaterialTheme.typography.bodyMedium)
                        if (!online.busy) OutlinedButton(onClick = online::refresh) { Text(stringResource(R.string.product_refresh)) }
                    } else {
                        online.courses.forEach { course ->
                            val id = course.optLong("id")
                            FilterChip(
                                selected = selectedCourseId == id,
                                onClick = {
                                    selectedCourseId = id
                                    subject = course.optString("title")
                                    selectedCourseTitle = subject
                                    selectedLessonId = 0L
                                    selectedLessonTitle = ""
                                    online.openCourse(id)
                                },
                                enabled = !online.busy,
                                label = { Text(course.optString("title")) },
                                leadingIcon = if (selectedCourseId == id) ({ androidx.compose.material3.Icon(Icons.Default.CheckCircle, null) }) else null
                            )
                        }
                    }
                }
                1 -> {
                    Text(stringResource(R.string.onboarding_daily_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.onboarding_daily_value, dailyMinutes), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    Slider(
                        value = dailyMinutes.toFloat(),
                        onValueChange = { dailyMinutes = ((it.toInt() - 15 + 7) / 15 * 15 + 15).coerceIn(15, 180) },
                        valueRange = 15f..180f,
                        steps = 10
                    )
                    Text(stringResource(R.string.onboarding_daily_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    Text(stringResource(R.string.onboarding_first_lesson_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.onboarding_first_lesson_body, selectedCourseTitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    when {
                        online.busy || selectedCourse == null -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        selectableLessons.isEmpty() -> Text(stringResource(R.string.onboarding_no_lessons), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else -> Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            selectableLessons.forEach { (chapterTitle, lesson) ->
                                val lessonId = lesson.optLong("id")
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selectedLessonId == lessonId,
                                        onClick = {
                                            selectedLessonId = lessonId
                                            selectedLessonTitle = lesson.optString("title")
                                        }
                                    )
                                    androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(lesson.optString("title"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                        Text(chapterTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            online.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { study.completeOnboarding(subject, dailyMinutes) },
                    enabled = !saving
                ) { Text(stringResource(R.string.onboarding_skip)) }
                if (step > 0) TextButton(onClick = { step-- }, enabled = !saving) { Text(stringResource(R.string.onboarding_back)) }
                if (step < 2) {
                    Button(onClick = { step++ }, enabled = !saving && (step != 0 || selectedCourseId > 0L)) {
                        Text(stringResource(R.string.onboarding_next))
                    }
                } else {
                    Button(
                        onClick = {
                            study.completeOnboarding(subject, dailyMinutes, selectedLessonId.takeIf { it > 0L }, selectedLessonTitle, selectedCourseTitle)
                        },
                        enabled = !saving && selectedCourse != null && (selectedLessonId > 0L || selectableLessons.isEmpty())
                    ) {
                        if (saving) CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        else Text(stringResource(R.string.onboarding_finish))
                    }
                }
            }
        }
    }
}

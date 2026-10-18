package com.example.learning

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.StudyEmptyState
import kotlinx.coroutines.delay

@Composable fun CourseChatScreen(vm: LearningViewModel) {
    var classesTab by remember { mutableStateOf(true) }
    LaunchedEffect(vm.onlineRole) { vm.loadStudyClasses() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp, vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            if(classesTab) Button(onClick={classesTab=true},modifier=Modifier.weight(1f)){Text(stringResource(R.string.classes_tab))}
            else OutlinedButton(onClick={classesTab=true},modifier=Modifier.weight(1f)){Text(stringResource(R.string.classes_tab))}
            if(!classesTab) Button(onClick={classesTab=false},modifier=Modifier.weight(1f)){Text(stringResource(R.string.discussions_tab))}
            else OutlinedButton(onClick={classesTab=false},modifier=Modifier.weight(1f)){Text(stringResource(R.string.discussions_tab))}
        }
        if(classesTab) StudyClassesPanel(vm) else CourseDiscussionPanel(vm)
    }
}

@Composable private fun StudyClassesPanel(vm: LearningViewModel) {
    var code by remember { mutableStateOf("") }
    var className by remember { mutableStateOf("") }
    var courseIndex by remember { mutableIntStateOf(0) }
    val report=vm.classReport
    Column(Modifier.fillMaxSize().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(stringResource(if(vm.onlineRole=="TEACHER") R.string.my_teaching_classes else R.string.my_enrolled_classes),style=MaterialTheme.typography.titleLarge)
        vm.error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        if(report!=null) {
            val info=report.getJSONObject("class")
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(info.getString("name"),style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
                TextButton(onClick=vm::closeClassReport){Text(stringResource(R.string.learning_back))}
            }
            Text(stringResource(R.string.class_course,info.getString("course_title")))
            val summary=report.getJSONObject("summary")
            LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                item(key="class-summary") {
                    OutlinedCard(Modifier.fillMaxWidth(),shape=RoundedCornerShape(8.dp)) {
                        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.class_summary),style=MaterialTheme.typography.titleSmall)
                            Text(stringResource(R.string.class_summary_values,summary.getInt("student_count"),summary.getInt("quiz_attempts"),"%.0f%%".format(summary.getDouble("lesson_completion_percent")),if(summary.isNull("average_score")) "--" else "%.0f%%".format(summary.getDouble("average_score"))))
                        }
                    }
                }
                item(key="chapter-heading") { Text(stringResource(R.string.class_chapter_report),style=MaterialTheme.typography.titleMedium) }
                items(report.getJSONArray("chapters").objects(),key={"chapter-${it.getLong("id")}"}) { chapter ->
                    val studentCount=summary.getInt("student_count")
                    val total=chapter.getInt("lesson_total")*studentCount
                    OutlinedCard(Modifier.fillMaxWidth(),shape=RoundedCornerShape(8.dp)) {
                        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text(chapter.getString("title"),fontWeight=FontWeight.SemiBold)
                            Text(stringResource(R.string.class_chapter_values,chapter.getInt("lesson_completed"),total,chapter.getInt("quiz_attempts"),if(chapter.isNull("average_score")) "--" else "%.0f%%".format(chapter.getDouble("average_score"))))
                        }
                    }
                }
                item(key="question-heading") { Text(stringResource(R.string.class_weak_questions),style=MaterialTheme.typography.titleMedium) }
                if(report.getJSONArray("questions").length()==0) item(key="question-empty") {
                    StudyEmptyState(title = stringResource(R.string.product_empty_questions_title), body = stringResource(R.string.class_no_question_data), icon = Icons.Default.Insights, compact = true)
                }
                items(report.getJSONArray("questions").objects(),key={"question-${it.getLong("question_id")}"}) { question ->
                    OutlinedCard(Modifier.fillMaxWidth(),shape=RoundedCornerShape(8.dp)) {
                        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text(question.getString("prompt"),maxLines=3,overflow=TextOverflow.Ellipsis)
                            Text(stringResource(R.string.class_question_wrong,question.getInt("wrong"),question.getInt("attempts"),"%.0f%%".format(question.getDouble("wrong_percent"))),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item(key="students-heading") { Text(stringResource(R.string.class_student_progress),style=MaterialTheme.typography.titleMedium) }
                items(report.getJSONArray("students").objects(),key={"student-${it.getLong("id")}"}) { student ->
                    OutlinedCard(Modifier.fillMaxWidth(),shape=RoundedCornerShape(8.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(student.getString("display_name"),fontWeight=FontWeight.SemiBold)
                            val total=student.getInt("lesson_total");val done=student.getInt("lesson_completed")
                            Text(stringResource(R.string.class_student_summary,done,total,student.getInt("quiz_attempts"),if(student.isNull("average_score")) "--" else "%.0f%%".format(student.getDouble("average_score"))))
                        }
                    }
                }
                if(report.getJSONArray("students").length()==0) item(key="student-empty") {
                    StudyEmptyState(title = stringResource(R.string.product_empty_students_title), body = stringResource(R.string.class_no_students), icon = Icons.Default.Groups, compact = true)
                }
            }
        } else {
            if(vm.onlineRole=="TEACHER") {
                if(vm.courses.isNotEmpty()) {
                    val selected=vm.courses[courseIndex.coerceIn(0,vm.courses.lastIndex)]
                    OutlinedButton(onClick={courseIndex=(courseIndex+1)%vm.courses.size},modifier=Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.class_course,selected.getString("title")),maxLines=1,overflow=TextOverflow.Ellipsis)
                    }
                    OutlinedTextField(className,{className=it.take(120)},label={Text(stringResource(R.string.class_name))},singleLine=true,modifier=Modifier.fillMaxWidth())
                    Button(onClick={if(className.isNotBlank()) vm.createStudyClass(selected.getLong("id"),className)},enabled=className.isNotBlank()&&!vm.busy,modifier=Modifier.fillMaxWidth()) { Text(stringResource(R.string.class_create)) }
                } else StudyEmptyState(
                    title = stringResource(R.string.product_empty_courses_title),
                    body = stringResource(R.string.class_no_courses),
                    icon = Icons.Default.School,
                    compact = true
                )
            } else {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(code,{code=it.filter(Char::isLetterOrDigit).take(12)},label={Text(stringResource(R.string.class_join_code))},singleLine=true,modifier=Modifier.weight(1f))
                    Button(onClick={vm.joinStudyClass(code);code=""},enabled=code.length>=6&&!vm.busy){Text(stringResource(R.string.class_join))}
                }
            }
            if(vm.studyClasses.isEmpty()) StudyEmptyState(
                title = stringResource(R.string.product_empty_class_title),
                body = stringResource(if(vm.onlineRole=="TEACHER") R.string.class_empty_teacher else R.string.class_empty_student),
                icon = Icons.Default.Groups,
                compact = true
            )
            LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                items(vm.studyClasses,key={it.getLong("id")}) { item ->
                    val teacher=vm.onlineRole=="TEACHER"
                    OutlinedCard(onClick={if(teacher) vm.openClassReport(item.getLong("id"))},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(8.dp)) {
                        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text(item.getString("name"),style=MaterialTheme.typography.titleMedium)
                            Text(item.optString(if(teacher) "course_title" else "course_title"),style=MaterialTheme.typography.bodyMedium)
                            if(teacher) Text(stringResource(R.string.class_join_code_display,item.getString("join_code"),item.optInt("student_count")),color=MaterialTheme.colorScheme.primary)
                            else Text(stringResource(R.string.class_teacher_name,item.optString("teacher_name")),style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun CourseDiscussionPanel(vm: LearningViewModel) {
    val selected = vm.discussionCourse
    var message by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    BackHandler(selected != null) { vm.leaveDiscussion() }
    DisposableEffect(Unit) { onDispose { vm.leaveDiscussion() } }
    LaunchedEffect(selected?.optLong("id")) {
        if (selected != null) while (true) { vm.refreshDiscussion(); delay(5000) }
    }
    val messages = vm.discussion?.optJSONArray("messages")?.objects().orEmpty()
    LaunchedEffect(messages.size) { if(messages.isNotEmpty()) list.animateScrollToItem(messages.lastIndex) }
    Column(Modifier.fillMaxSize().imePadding().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            if(selected!=null) IconButton(onClick=vm::leaveDiscussion){Icon(Icons.AutoMirrored.Filled.ArrowBack,stringResource(R.string.learning_back))}
            Text(selected?.getString("title") ?: stringResource(R.string.product_community),style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
        }
        vm.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        if(selected==null) LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if (vm.courses.isEmpty()) item {
                StudyEmptyState(
                    title = stringResource(R.string.product_empty_courses_title),
                    body = stringResource(R.string.product_empty_courses_body),
                    icon = Icons.Default.Forum,
                    actionLabel = stringResource(R.string.product_refresh),
                    onAction = vm::refresh
                )
            }
            items(vm.courses,key={it.getLong("id")}) {course ->
                OutlinedCard(onClick={vm.openDiscussion(course)},shape=RoundedCornerShape(8.dp),modifier=Modifier.fillMaxWidth()) {Text(course.getString("title"),modifier=Modifier.padding(18.dp),style=MaterialTheme.typography.titleMedium)}
            }
        } else {
            val otherOnline = vm.discussion?.optJSONArray("online")?.objects().orEmpty()
                .count { it.optString("username") != vm.onlineUsername }
            if (otherOnline > 0) Text(stringResource(R.string.product_online_count, otherOnline),style=MaterialTheme.typography.bodySmall)
            LazyColumn(state=list,modifier=Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                if(messages.isEmpty()) item {
                    StudyEmptyState(
                        title = stringResource(R.string.product_empty_chat_title),
                        body = stringResource(R.string.product_chat_empty),
                        icon = Icons.Default.ChatBubbleOutline,
                        compact = true
                    )
                }
                items(messages,key={it.getLong("id")}) {item ->
                    val mine=item.getString("username")==vm.onlineUsername
                    val rawName=item.optString("display_name").ifBlank { item.getString("username") }
                    val senderName=if(rawName.matches(Regex("(?i).+@(admin|teacher|student)"))) when(item.optString("role")) {
                        "ADMIN" -> "Quản trị viên"
                        "TEACHER" -> "Giảng viên"
                        else -> "Học viên"
                    } else rawName
                    Column(Modifier.fillMaxWidth(),horizontalAlignment=if(mine) Alignment.End else Alignment.Start) {
                        Text(senderName,style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.Bold)
                        Surface(color=if(mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,shape=RoundedCornerShape(8.dp)) {
                            Text(item.getString("body"),modifier=Modifier.padding(12.dp).widthIn(max=280.dp))
                        }
                        Text(java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(item.getLong("sent_at"))),style=MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Row(verticalAlignment=Alignment.CenterVertically) {
                OutlinedTextField(message,{message=it.take(2000)},placeholder={Text(stringResource(R.string.product_chat_input))},modifier=Modifier.weight(1f),maxLines=4)
                IconButton(onClick={vm.sendDiscussion(message){message=""}},enabled=message.isNotBlank()&&!vm.busy){Icon(Icons.AutoMirrored.Filled.Send,stringResource(R.string.product_send))}
            }
        }
    }
}

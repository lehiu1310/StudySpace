package com.example.learning

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

data class LessonChatMessage(val isUser: Boolean, val text: String, val offline: Boolean = false, val provider: String? = null)

class LearningViewModel(app: Application) : AndroidViewModel(app) {
    private val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
    private val aiClient = OkHttpClient.Builder().callTimeout(35, TimeUnit.SECONDS).build()
    private val prefs = app.getSharedPreferences("learning_drafts", 0)
    private var token = ""
    private var accountId = 0L
    private var owner = ""
    var endpoint by mutableStateOf(if (BuildConfig.DEBUG) "http://10.0.2.2:8080" else BuildConfig.DEFAULT_API_BASE_URL)
        private set
    var signedIn by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var courses by mutableStateOf(emptyList<JSONObject>())
        private set
    var catalogSearchDetails by mutableStateOf<Map<Long, JSONObject>>(emptyMap())
        private set
    var catalogSearchLoading by mutableStateOf(false)
        private set
    private val catalogSearchAttempted = mutableSetOf<Long>()
    var course by mutableStateOf<JSONObject?>(null)
        private set
    var lesson by mutableStateOf<JSONObject?>(null)
        private set
    var attempt by mutableStateOf<JSONObject?>(null)
        private set
    var aiExplanations by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var aiExplanationProviders by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var aiExplanationLoadingId by mutableStateOf<String?>(null)
        private set
    var aiExplanationFallbackId by mutableStateOf<String?>(null)
        private set
    var reviewQuizProvider by mutableStateOf<String?>(null)
        private set
    var aiStudyPlan by mutableStateOf(emptyList<com.example.data.StudyTask>())
        private set
    var aiStudyPlanFallback by mutableStateOf(false)
        private set
    var aiStudyPlanProvider by mutableStateOf<String?>(null)
        private set
    var aiChallengeProvider by mutableStateOf<String?>(null)
        private set
    var lessonChatMessages by mutableStateOf(emptyList<LessonChatMessage>())
        private set
    var lessonChatLoading by mutableStateOf(false)
        private set
    var answers by mutableStateOf<Map<String, Int>>(emptyMap())
        private set
    var markedQuestionIds by mutableStateOf<Set<Long>>(emptySet())
        private set
    var progress by mutableStateOf<JSONObject?>(null)
        private set
    var showProgress by mutableStateOf(false)
        private set
    var offline by mutableStateOf(false)
        private set
    var discussion by mutableStateOf<JSONObject?>(null)
        private set
    var discussionCourse by mutableStateOf<JSONObject?>(null)
        private set
    var onlineUsername by mutableStateOf("")
        private set
    var onlineRole by mutableStateOf("STUDENT")
        private set
    var studyClasses by mutableStateOf(emptyList<JSONObject>())
        private set
    var announcements by mutableStateOf(emptyList<JSONObject>())
        private set
    var announcementsLoading by mutableStateOf(false)
        private set
    var announcementsError by mutableStateOf<String?>(null)
        private set
    var classReport by mutableStateOf<JSONObject?>(null)
        private set
    private var expiresAt = 0L
    private val vault = app.getSharedPreferences("online_session_vault", 0)
    private val accountState = app.getSharedPreferences("online_account_state", 0)
    private val personalSyncPrefs = app.getSharedPreferences("personal_study_sync", 0)
    private val personalStore by lazy { PersonalStudyStore(com.example.data.StudyDatabase.getDatabase(app)) }
    var personalSyncStatus by mutableStateOf<String?>(null)
        private set
    data class SyncChoice(val localPlans: Int, val localSessions: Int, val cloudPlans: Int, val cloudSessions: Int, val version: Long, val cloudUpdatedAt: Long)
    var personalSyncChoice by mutableStateOf<SyncChoice?>(null)
        private set
    private data class PendingSync(val owner: String, val scope: String, val localHash: String, val remote: JSONObject)
    private var pendingSync: PendingSync? = null
    private var generation = 0
    private var lessonChatGeneration = 0
    private val quizClock = QuizClock()
    fun secondsRemaining(): Int = quizClock.remainingSeconds(android.os.SystemClock.elapsedRealtime())

    fun bindOwner(value: String) {
        if (owner != value) {
            generation++; reset(); owner = value
            if (value.isNotEmpty()) restoreSession()
        }
    }
    fun adoptOwner(value: String) { owner = value; persistSession() }
    fun restoreRememberedAccount(onRestored: (String, String) -> Unit) {
        val legacyOwner = getApplication<Application>()
            .getSharedPreferences("edupomodoro_prefs", 0)
            .getString("last_username", null)
            ?.takeIf(vault::contains)
        val savedOwner = accountState.getString("last_owner", null) ?: legacyOwner ?: return
        accountState.edit().putString("last_owner", savedOwner).apply()
        owner = savedOwner
        restoreSession()
        if (signedIn) onRestored(savedOwner, onlineUsername)
        else accountState.edit().remove("last_owner").apply()
    }
    private fun persistSession() {
        if (owner.isBlank() || token.isBlank()) return
        accountState.edit().putString("last_owner", owner).apply()
        val data = JSONObject().put("token", token).put("accountId", accountId).put("username", onlineUsername).put("role",onlineRole).put("endpoint", endpoint).put("expiresAt", expiresAt)
        val encrypted = com.example.util.SecurityUtils.encryptAes(data.toString())
        if (!encrypted.startsWith("KS1:")) {
            error = "Không thể lưu phiên an toàn. Bạn sẽ cần đăng nhập lại khi mở ứng dụng."
            return
        }
        vault.edit().putString(owner, encrypted).apply()
        PersonalStudySyncWorker.schedulePeriodic(getApplication<Application>())
    }
    private fun restoreSession() {
        runCatching {
            val encrypted = vault.getString(owner, null) ?: return
            check(encrypted.startsWith("KS1:"))
            val data = JSONObject(com.example.util.SecurityUtils.decryptAes(encrypted))
            if (data.getLong("expiresAt") <= System.currentTimeMillis()) {
                vault.edit().remove(owner).apply()
                accountState.edit().remove("last_owner").apply()
                return
            }
            token = data.getString("token"); accountId = data.getLong("accountId")
            endpoint = data.getString("endpoint"); onlineUsername = data.getString("username")
            onlineRole = data.optString("role","STUDENT")
            expiresAt = data.getLong("expiresAt"); signedIn = true
            refresh()
        }.onFailure { vault.edit().remove(owner).apply(); accountState.edit().remove("last_owner").apply(); reset() }
    }
    private fun reset() {
        lessonChatGeneration++
        token = ""; accountId = 0; signedIn = false; courses = emptyList(); catalogSearchDetails = emptyMap(); catalogSearchAttempted.clear(); catalogSearchLoading = false; course = null
        lesson = null; attempt = null; progress = null; answers = emptyMap(); markedQuestionIds = emptySet(); showProgress = false; error = null; busy = false
        aiExplanations = emptyMap(); aiExplanationProviders = emptyMap(); aiExplanationLoadingId = null; aiExplanationFallbackId = null
        reviewQuizProvider = null
        aiStudyPlan = emptyList(); aiStudyPlanFallback = false; aiStudyPlanProvider = null; aiChallengeProvider = null
        lessonChatMessages = emptyList(); lessonChatLoading = false
        discussion = null; discussionCourse = null; onlineUsername = ""
        studyClasses = emptyList(); announcements = emptyList(); announcementsLoading = false; announcementsError = null; classReport = null
        onlineRole = "STUDENT"; expiresAt = 0L; offline = false
        personalSyncStatus = null; personalSyncChoice = null; pendingSync = null
    }
    private fun task(block: suspend () -> Unit) {
        if (busy) return
        val expected = generation
        viewModelScope.launch {
            busy = true; error = null
            try { block() } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (expected == generation) {
                    if (e is SessionExpired) {
                        vault.edit().remove(owner).apply()
                        accountState.edit().remove("last_owner").apply()
                        generation++; reset()
                    }
                    error = e.message ?: "Không kết nối được máy chủ."
                }
            }
            finally { if (expected == generation) busy = false }
        }
    }
    private suspend fun request(path: String, method: String = "GET", body: JSONObject? = null, requestClient: OkHttpClient = client): String {
        val expected = generation
        val base = endpoint; val session = token
        val result = withContext(Dispatchers.IO) {
            val builder = Request.Builder().url("$base/api/$path")
            if (session.isNotEmpty()) builder.header("Authorization", "Bearer $session")
            if (method != "GET") builder.method(method, (body?.toString() ?: "{}").toRequestBody("application/json".toMediaType()))
            requestClient.newCall(builder.build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    if (response.code == 401) throw SessionExpired()
                    throw IllegalStateException(runCatching { JSONObject(text).getString("error") }.getOrDefault("Máy chủ trả lỗi ${response.code}."))
                }
                text
            }
        }
        if (expected != generation) throw CancellationException()
        offline = false
        return result
    }
    private class SessionExpired : Exception("Tên đăng nhập/mật khẩu không đúng hoặc phiên đã hết hạn. Hãy đăng nhập lại.")
    fun authenticate(url: String, username: String, password: String, register: Boolean, onAuthenticated: ((String, Long) -> Unit)? = null) = task {
        val uri = java.net.URI(url.trim().trimEnd('/'))
        require(uri.scheme == "https" || (BuildConfig.DEBUG && uri.scheme == "http" && uri.host in listOf("10.0.2.2", "localhost", "127.0.0.1"))) { "Máy chủ phải dùng HTTPS; bản debug hỗ trợ localhost." }
        require(uri.userInfo == null && uri.query == null && uri.fragment == null && uri.host != null) { "Địa chỉ máy chủ không hợp lệ." }
        endpoint = url.trim().trimEnd('/')
        val result = JSONObject(request("auth/${if (register) "register" else "login"}", "POST", JSONObject().put("username", username.trim()).put("password", password)))
        token = result.getString("token"); accountId = result.getJSONObject("account").getLong("id"); signedIn = true
        expiresAt = result.getLong("expiresAt"); onlineUsername = result.getJSONObject("account").getString("username")
        onlineRole = result.getJSONObject("account").getString("role")
        if (onAuthenticated != null) onAuthenticated(onlineUsername, accountId) else persistSession()
        loadCourses()
        progress = JSONObject(cached("progress"))
        loadAnnouncements()
    }
    private fun key(suffix: String) = "$endpoint:$owner:$accountId:$suffix"
    private suspend fun cached(path: String): String {
        return try { request(path).also { prefs.edit().putString(key(path), it).apply() } }
        catch (e: SessionExpired) { signedIn = false; token = ""; throw e }
        catch (e: java.io.IOException) { prefs.getString(key(path), null)?.also { offline = true } ?: throw e }
    }
    private suspend fun loadCourses() { courses = JSONArray(cached("courses")).objects() }
    fun loadCatalogSearchDetails() {
        if (!signedIn || catalogSearchLoading) return
        val pending = courses.filter { it.optLong("id") !in catalogSearchAttempted }
        if (pending.isEmpty()) return
        catalogSearchLoading = true
        val expectedGeneration = generation
        viewModelScope.launch {
            try {
                val loaded = coroutineScope {
                    pending.map { item ->
                        val id = item.optLong("id")
                        async {
                            try {
                                id to JSONObject(cached("courses/$id"))
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                id to null
                            }
                        }
                    }.awaitAll()
                }
                if (expectedGeneration == generation) {
                    catalogSearchAttempted.addAll(pending.map { it.optLong("id") })
                    catalogSearchDetails = catalogSearchDetails + loaded.filter { it.second != null }.associate { it.first to it.second!! }
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                if (expectedGeneration == generation) catalogSearchLoading = false
            }
        }
    }
    fun refresh() = task { loadCourses(); progress = JSONObject(cached("progress")); loadAnnouncements() }

    fun refreshAnnouncements() = task { loadAnnouncements() }

    private suspend fun loadAnnouncements() {
        announcementsLoading = true
        announcementsError = null
        try {
            announcements = JSONArray(cached("classes/announcements")).objects()
        } catch (e: CancellationException) {
            throw e
        } catch (e: SessionExpired) {
            throw e
        } catch (e: Exception) {
            announcementsError = getApplication<Application>().getString(com.example.R.string.product_announcements_load_error)
            if (e is java.io.IOException) offline = true
        } finally {
            announcementsLoading = false
        }
    }

    private fun personalScope(): String = AccountScope.localId(endpoint, accountId) + ":" + owner
    private fun recordPersonalSync(scope: String, version: Long, hash: String) {
        personalSyncPrefs.edit().putLong("$scope:version", version).putString("$scope:hash", hash).apply()
        personalSyncStatus = getApplication<Application>().getString(com.example.R.string.personal_sync_done)
        personalSyncChoice = null; pendingSync = null
    }
    fun dismissPersonalSyncChoice() { personalSyncChoice = null; pendingSync = null }
    fun syncPersonalStudy() = task {
        check(signedIn && owner.isNotBlank())
        val expectedGeneration = generation
        personalSyncStatus = null
        val localOwner = owner
        val scope = personalScope()
        val local = personalStore.capture(localOwner)
        if (expectedGeneration != generation) throw CancellationException()
        val hash = PersonalStudyStore.hash(local)
        val remote = JSONObject(request("personal-study"))
        val version = remote.getLong("version")
        val cloud = PersonalStudyStore.normalize(remote.getJSONObject("data"))
        when (PersonalSyncPolicy.decide(hash, PersonalStudyStore.hash(cloud), personalSyncPrefs.getString("$scope:hash", null), version, personalSyncPrefs.getLong("$scope:version",0))) {
            PersonalSyncDecision.CURRENT -> recordPersonalSync(scope, version, hash)
            PersonalSyncDecision.UPLOAD -> uploadPersonalStudy(scope, version, local, hash)
            PersonalSyncDecision.CHOOSE -> {
                pendingSync = PendingSync(localOwner,scope,hash,remote)
                personalSyncChoice = SyncChoice(local.getJSONArray("plans").length(),local.getJSONArray("sessions").length(),
                    cloud.getJSONArray("plans").length(),cloud.getJSONArray("sessions").length(),version,remote.getLong("updatedAt"))
            }
        }
    }
    private suspend fun uploadPersonalStudy(scope: String, version: Long, data: JSONObject, hash: String) {
        val response = JSONObject(request("personal-study", "PUT", JSONObject().put("expectedVersion",version)
            .put("requestId",java.util.UUID.randomUUID().toString()).put("data",data)))
        recordPersonalSync(scope, response.getLong("version"), hash)
    }
    fun resolvePersonalSync(keepDevice: Boolean, canRestore: () -> Boolean, onRestored: () -> Unit) = task {
        val expectedGeneration = generation
        val pending = pendingSync ?: return@task
        check(pending.owner == owner && pending.scope == personalScope()) { "Tài khoản đã thay đổi. Hãy đồng bộ lại." }
        val local = personalStore.capture(pending.owner)
        if (expectedGeneration != generation) throw CancellationException()
        check(PersonalStudyStore.hash(local) == pending.localHash) { "Dữ liệu trên thiết bị vừa thay đổi. Hãy đóng hộp thoại và đồng bộ lại." }
        val version = pending.remote.getLong("version")
        if (keepDevice) {
            uploadPersonalStudy(pending.scope, version, local, pending.localHash)
        } else {
            val fresh = JSONObject(request("personal-study"))
            check(fresh.getLong("version") == version) { "Máy chủ có phiên bản mới. Hãy đóng hộp thoại và đồng bộ lại." }
            check(canRestore()) { "Hãy dừng phiên tập trung trước khi nhận dữ liệu từ máy chủ." }
            val restored = personalStore.replace(pending.owner, fresh.getJSONObject("data"), pending.localHash)
            restored.oldTaskIds.forEach { com.example.util.StudyReminderWorker.cancel(getApplication(), it) }
            restored.tasks.forEach { com.example.util.StudyReminderWorker.schedule(getApplication(), it) }
            // The transaction captures this hash before any subsequent local edit can occur.
            val appliedHash = restored.appliedHash
            personalSyncPrefs.edit().putLong("${pending.scope}:version",version).putString("${pending.scope}:hash",appliedHash).apply()
            if (owner == pending.owner && personalScope() == pending.scope) {
                recordPersonalSync(pending.scope,version,appliedHash)
                onRestored()
            }
        }
    }
    fun openCourse(id: Long) = task { course = JSONObject(cached("courses/$id")); clearLessonChat(); lesson = null; showProgress = false }
    private fun clearLessonChat() {
        lessonChatGeneration++
        lessonChatMessages = emptyList()
        lessonChatLoading = false
    }
    fun openLesson(value: JSONObject) {
        clearLessonChat()
        lesson = value
    }
    fun askLesson(question: String) {
        val currentLesson = lesson ?: return
        val prompt = question.trim().take(500)
        if (prompt.isBlank() || lessonChatLoading) return
        val chatTurn = ++lessonChatGeneration
        lessonChatMessages = lessonChatMessages + LessonChatMessage(true, prompt)
        lessonChatLoading = true
        viewModelScope.launch {
            try {
                val context = "Bài học: ${currentLesson.optString("title")}\n${currentLesson.optString("content")}".take(11_500)
                val response = JSONObject(request("ai/chat", "POST", JSONObject()
                    .put("question", prompt).put("lessonContext", context), aiClient))
                val answer = response.optString("answer").trim()
                check(answer.isNotBlank()) { "AI chưa trả lời được câu hỏi này." }
                if (chatTurn == lessonChatGeneration) lessonChatMessages = lessonChatMessages + LessonChatMessage(false, answer, provider = response.optString("provider").takeIf { it.isNotBlank() })
            } catch (e: CancellationException) {
                throw e
            } catch (e: SessionExpired) {
                if (chatTurn == lessonChatGeneration) {
                    vault.edit().remove(owner).apply()
                    accountState.edit().remove("last_owner").apply()
                    generation++
                    reset()
                }
            } catch (e: Exception) {
                if (chatTurn == lessonChatGeneration) {
                    lessonChatMessages = lessonChatMessages + LessonChatMessage(
                        false, offlineLessonAnswer(prompt, currentLesson.optString("content")), offline = true, provider = "offline"
                    )
                    if (e is java.io.IOException) offline = true
                }
            } finally {
                if (chatTurn == lessonChatGeneration) lessonChatLoading = false
            }
        }
    }
    fun updateDisplayName(name: String, onSaved: () -> Unit) = task {
        request("auth/me", "PUT", JSONObject().put("displayName",name.trim()))
        onSaved()
    }
    fun openTeacherContent(onUrlReady: (String) -> Unit) = task {
        check(onlineRole=="TEACHER") { "Chức năng này dành cho giảng viên." }
        val response=JSONObject(request("auth/portal-ticket","POST"))
        onUrlReady("$endpoint/teacher.html#ticket=${response.getString("ticket")}")
    }
    fun generateAIChallenge(profile: com.example.util.StudySkillRequest, fallback: com.example.util.StudyChallenge, onComplete: (com.example.util.StudyChallenge) -> Unit) {
        if (busy) { aiChallengeProvider = "offline"; onComplete(fallback); return }
        val expected = generation
        viewModelScope.launch {
            busy = true
            error = null
            aiChallengeProvider = null
            var result = fallback
            try {
                val payload = JSONObject()
                    .put("category", profile.category).put("goal", profile.goal).put("level", profile.level)
                    .put("weakness", profile.weakness).put("learningStyle", profile.learningStyle)
                    .put("timeBudgetMinutes", profile.timeBudgetMinutes).put("deadline", profile.deadline)
                    .put("weeklyFocusMinutes", profile.weeklyFocusMinutes).put("openTaskCount", profile.openTaskCount)
                    .put("completedTaskCount", profile.completedTaskCount).put("focusSessionCount", profile.focusSessionCount)
                    .put("recentTaskTitles", JSONArray(profile.recentTaskTitles.take(5)))
                val data = JSONObject(request("ai/challenge", "POST", payload))
                aiChallengeProvider = data.optString("provider").takeIf { it.isNotBlank() }
                fun strings(name: String): List<String> {
                    val values = data.optJSONArray(name) ?: return emptyList()
                    return (0 until values.length()).map { values.optString(it) }.filter { it.isNotBlank() }
                }
                result = com.example.util.StudyChallenge(
                    title = data.optString("title"), diagnosis = data.optString("diagnosis"), tip = data.optString("tip"),
                    microPlan = strings("microPlan"), exercise = data.optString("exercise"),
                    answerChecklist = strings("answerChecklist"),
                    estimatedMinutes = data.optInt("estimatedMinutes", profile.timeBudgetMinutes).coerceIn(10, 120),
                    difficulty = data.optString("difficulty", profile.level), nextAction = data.optString("nextAction")
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                aiChallengeProvider = "offline"
                if (expected == generation) error = e.message ?: "Đang dùng bài luyện ngoại tuyến."
            } finally {
                if (expected == generation) busy = false
            }
            if (expected == generation) onComplete(result)
        }
    }
    fun findLesson(id: Long) = task {
        val location = JSONObject(request("lessons/$id/location"))
        course = JSONObject(cached("courses/${location.getLong("course_id")}"))
        clearLessonChat()
        lesson = course!!.getJSONArray("chapters").objects().flatMap { it.getJSONArray("lessons").objects() }.firstOrNull { it.getLong("id") == id }
        attempt = null; showProgress = false
    }
    fun openDiscussion(value: JSONObject) { discussionCourse = value; discussion = null; refreshDiscussion() }
    fun refreshDiscussion() = task {
        val selected = discussionCourse ?: return@task
        val data = JSONObject(request("courses/${selected.getLong("id")}/discussion"))
        if (discussionCourse?.getLong("id") == selected.getLong("id")) discussion = data
    }
    fun sendDiscussion(body: String, onSent: () -> Unit) = task {
        val selected = discussionCourse ?: return@task
        request("courses/${selected.getLong("id")}/discussion", "POST", JSONObject().put("body", body.trim()))
        discussion = JSONObject(request("courses/${selected.getLong("id")}/discussion"))
        onSent()
    }
    fun leaveDiscussion() {
        val selected = discussionCourse ?: return
        discussionCourse = null; discussion = null
        viewModelScope.launch { runCatching { request("courses/${selected.getLong("id")}/discussion", "DELETE") } }
    }
    fun loadStudyClasses() = task {
        classReport = null
        val path=if(onlineRole=="TEACHER") "teacher/classes" else "classes/mine"
        studyClasses=JSONArray(request(path)).objects()
    }
    fun createStudyClass(courseId: Long,name: String) = task {
        request("teacher/classes","POST",JSONObject().put("courseId",courseId).put("name",name.trim()))
        studyClasses=JSONArray(request("teacher/classes")).objects()
    }
    fun joinStudyClass(code: String) = task {
        request("classes/join","POST",JSONObject().put("code",code.trim()))
        studyClasses=JSONArray(request("classes/mine")).objects()
    }
    fun openClassReport(id: Long) = task { classReport=JSONObject(request("teacher/classes/$id/report")) }
    fun closeClassReport() { classReport=null }
    fun archiveStudyClass(id: Long) = task {
        request("teacher/classes/$id","DELETE")
        classReport=null
        studyClasses=JSONArray(request("teacher/classes")).objects()
    }
    fun prepareProgressReportExport(onReady: (Boolean) -> Unit) = task {
        val report = JSONObject(cached("progress"))
        try {
            ProgressReportContent.buildLines(
                report = report,
                username = onlineUsername,
                exportedAt = java.text.DateFormat.getDateTimeInstance().format(java.util.Date())
            )
            onReady(true)
        } catch (e: IllegalArgumentException) {
            error = e.message ?: "Chưa có dữ liệu học tập hoặc kết quả quiz để xuất báo cáo."
            onReady(false)
        }
    }
    fun exportReport(target: android.net.Uri) = task {
        try {
            val report = JSONObject(cached("progress"))
            val lines = ProgressReportContent.buildLines(
                report = report,
                username = onlineUsername,
                exportedAt = java.text.DateFormat.getDateTimeInstance().format(java.util.Date())
            )
            withContext(Dispatchers.IO) {
                val output = getApplication<Application>().contentResolver.openOutputStream(target, "w")
                    ?: error("Không mở được tệp báo cáo.")
                output.use { ProgressPdfExporter.write(it, lines) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("StudySpacePdfExport", "Failed to create or save progress PDF", e)
            throw IllegalStateException(
                e.message?.takeIf { it == "Chưa có dữ liệu học tập hoặc kết quả quiz để xuất báo cáo." }
                    ?: "Không thể tạo hoặc lưu báo cáo PDF. Hãy thử lại.",
                e
            )
        }
        android.widget.Toast.makeText(getApplication(),com.example.R.string.product_saved,android.widget.Toast.LENGTH_LONG).show()
    }
    fun completeLesson(onCompleted: (Long) -> Unit = {}) = task {
        val current = lesson ?: return@task
        request("lessons/${current.getLong("id")}/complete", "PUT")
        onCompleted(current.getLong("id"))
        val refreshed = JSONObject(cached("courses/${course!!.getLong("id")}"))
        course = refreshed
        lesson = refreshed.getJSONArray("chapters").objects().flatMap { it.getJSONArray("lessons").objects() }.first { it.getLong("id") == current.getLong("id") }
        loadCourses()
    }
    fun startQuiz(chapter: Long, review: Boolean) = task {
        reviewQuizProvider = null
        restoreAttempt(JSONObject(request("chapters/$chapter/attempts?review=$review", "POST")))
    }
    fun generateWeakChapterQuiz() = task {
        reviewQuizProvider = null
        val fallbackChapterId = progress?.optJSONArray("mastery")?.objects()
            ?.filterNot { it.isNull("percentage") }
            ?.minByOrNull { it.getDouble("percentage") }
            ?.optLong("id")
            ?.takeIf { it > 0L }
        try {
            val result = JSONObject(request("ai/review-quiz", "POST", JSONObject(), aiClient))
            reviewQuizProvider = result.optString("provider").takeIf { it.isNotBlank() }
            restoreAttempt(result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SessionExpired) {
            throw e
        } catch (aiError: Exception) {
            val chapterId = fallbackChapterId ?: throw aiError
            val fallback = try {
                JSONObject(request("chapters/$chapterId/attempts?review=true", "POST"))
            } catch (fallbackError: Exception) {
                if (fallbackError is CancellationException) throw fallbackError
                if (fallbackError is SessionExpired) throw fallbackError
                throw IllegalStateException(getApplication<Application>().getString(com.example.R.string.learning_ai_quiz_offline))
            }
            reviewQuizProvider = "offline"
            restoreAttempt(fallback)
            error = getApplication<Application>().getString(com.example.R.string.learning_ai_quiz_fallback)
        }
    }
    fun generateStudyPlan(category: String, goal: String, examDate: String, dailyMinutes: Int) = task {
        aiStudyPlanProvider = null
        check(signedIn) { getApplication<Application>().getString(com.example.R.string.product_ai_plan_signin_required) }
        require(category.isNotBlank() && category.length <= 40) { getApplication<Application>().getString(com.example.R.string.product_ai_plan_invalid) }
        require(goal.isNotBlank() && goal.length <= 160) { getApplication<Application>().getString(com.example.R.string.product_ai_plan_invalid) }
        require(dailyMinutes in 10..360) { getApplication<Application>().getString(com.example.R.string.product_ai_plan_invalid) }
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        val date = runCatching { parser.parse(examDate)?.takeIf { parser.format(it) == examDate } }.getOrNull()
            ?: throw IllegalArgumentException(getApplication<Application>().getString(com.example.R.string.product_ai_plan_invalid_date))
        val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val exam = Calendar.getInstance().apply { time = date; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val daysUntilExam = ((exam.timeInMillis - today.timeInMillis) / 86_400_000L).toInt()
        require(daysUntilExam in 0..365) { getApplication<Application>().getString(com.example.R.string.product_ai_plan_invalid_date) }

        try {
            val result = JSONObject(request("ai/study-plan", "POST", JSONObject()
                .put("category", category.trim()).put("goal", goal.trim())
                .put("examDate", examDate).put("dailyMinutes", dailyMinutes), aiClient))
            aiStudyPlanProvider = result.optString("provider").takeIf { it.isNotBlank() }
            val items = result.optJSONArray("tasks") ?: throw IllegalStateException("AI trả về kế hoạch không hợp lệ.")
            require(items.length() in 1..14) { "AI trả về kế hoạch không hợp lệ." }
            aiStudyPlan = (0 until items.length()).map { index ->
                val item = items.getJSONObject(index)
                val offset = item.getInt("dayOffset")
                val duration = item.getInt("duration")
                require(offset in 0..daysUntilExam && duration in 5..dailyMinutes)
                val due = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }.timeInMillis
                com.example.data.StudyTask(
                    title = item.getString("title").trim().take(120), category = category.trim(),
                    description = "${item.getString("description").trim()}\nMục tiêu: ${goal.trim()} · Hạn thi: $examDate",
                    targetMinutes = duration, dueDate = due
                )
            }
            aiStudyPlanFallback = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: SessionExpired) {
            throw e
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            aiStudyPlanProvider = "offline"
            val fallback = com.example.util.GeminiAssistant.offlinePlan(category).take(minOf(3, daysUntilExam + 1))
            aiStudyPlan = fallback.mapIndexed { index, item ->
                val due = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, daysUntilExam * index / fallback.size.coerceAtLeast(1)) }.timeInMillis
                item.copy(
                    title = "${goal.trim().take(45)}: ${item.title}".take(120),
                    description = "${item.description}\nMục tiêu: ${goal.trim()} · Hạn thi: $examDate",
                    targetMinutes = item.targetMinutes.coerceAtMost(dailyMinutes), dueDate = due
                )
            }
            aiStudyPlanFallback = true
        }
    }
    fun clearStudyPlan() { aiStudyPlan = emptyList(); aiStudyPlanFallback = false; aiStudyPlanProvider = null }
    fun openAttempt(id: Long) = task { reviewQuizProvider = null; restoreAttempt(JSONObject(request("attempts/$id"))) }
    private fun restoreAttempt(value: JSONObject) {
        if (attempt?.optLong("id", -1L) != value.getLong("id")) {
            aiExplanations = emptyMap()
            aiExplanationProviders = emptyMap()
            aiExplanationFallbackId = null
        }
        attempt = value
        updateDeadline(value)
        val marksKey = key("marked:${value.getLong("id")}")
        markedQuestionIds = runCatching {
            val saved = JSONArray(prefs.getString(marksKey, "[]")!!)
            (0 until saved.length()).map { saved.getLong(it) }.toSet()
        }.getOrDefault(emptySet())
        val draft = JSONObject(prefs.getString(key("draft:${value.getLong("id")}"), "{}")!!)
        answers = value.getJSONArray("questions").objects().mapNotNull { q ->
            val id = q.getLong("question_id").toString()
            when { value.isNull("submitted_at") && draft.has(id) -> id to draft.getInt(id); !q.isNull("selected_option") -> id to q.getInt("selected_option"); else -> null }
        }.toMap()
    }
    private fun updateDeadline(value: JSONObject) {
        quizClock.sync(value.getLong("deadline"), value.getLong("server_now"), android.os.SystemClock.elapsedRealtime())
    }
    fun choose(question: String, option: Int) {
        if (attempt == null || !attempt!!.isNull("submitted_at")) return
        answers = answers + (question to option)
        prefs.edit().putString(key("draft:${attempt!!.getLong("id")}"), JSONObject(answers).toString()).apply()
    }
    fun toggleQuestionMark(questionId: Long) {
        if (attempt == null || !attempt!!.isNull("submitted_at")) return
        markedQuestionIds = if (questionId in markedQuestionIds) markedQuestionIds - questionId else markedQuestionIds + questionId
        val array = JSONArray().also { result -> markedQuestionIds.forEach(result::put) }
        prefs.edit().putString(key("marked:${attempt!!.getLong("id")}"), array.toString()).apply()
    }
    fun saveAnswers() = task { uploadAnswers() }
    private suspend fun uploadAnswers() {
        val value = attempt ?: return
        if (!value.isNull("submitted_at")) return
        attempt = JSONObject(request("attempts/${value.getLong("id")}/answers", "PUT", JSONObject().put("answers", JSONObject(answers))))
        updateDeadline(attempt!!)
    }
    fun submit(onSubmitted: () -> Unit = {}) = task {
        val current = attempt ?: return@task
        if (secondsRemaining() > 0) uploadAnswers()
        attempt = JSONObject(request("attempts/${current.getLong("id")}/submit", "POST"))
        prefs.edit().remove(key("draft:${current.getLong("id")}")).apply()
        prefs.edit().remove(key("marked:${current.getLong("id")}")).apply()
        markedQuestionIds = emptySet()
        onSubmitted()
    }
    fun explainWrongAnswer(question: JSONObject) {
        val questionId = question.optLong("question_id", -1L).toString()
        val selectedOption = question.optInt("selected_option", -1)
        val correctOption = question.optInt("correct_option", -1)
        if (questionId == "-1" || selectedOption !in 0..3 || correctOption !in 0..3 || selectedOption == correctOption ||
            questionId in aiExplanations || aiExplanationLoadingId != null || busy) return

        val expected = generation
        viewModelScope.launch {
            aiExplanationLoadingId = questionId
            aiExplanationFallbackId = null
            try {
                val options = listOf("option_a", "option_b", "option_c", "option_d")
                val response = JSONObject(request("ai/explain", "POST", JSONObject()
                    .put("question", question.optString("prompt"))
                    .put("selectedAnswer", question.optString(options[selectedOption]))
                    .put("correctAnswer", question.optString(options[correctOption]))
                    .put("selectedOption", selectedOption)
                    .put("correctOption", correctOption), aiClient))
                if (expected == generation) {
                    val explanation = response.optString("explanation").trim()
                    check(explanation.isNotBlank()) { getApplication<Application>().getString(com.example.R.string.learning_ai_explain_fallback) }
                    aiExplanations = aiExplanations + (questionId to explanation)
                    response.optString("provider").takeIf { it.isNotBlank() }?.let { provider ->
                        aiExplanationProviders = aiExplanationProviders + (questionId to provider)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (expected == generation) {
                    if (e is SessionExpired) {
                        vault.edit().remove(owner).apply()
                        accountState.edit().remove("last_owner").apply()
                        generation++
                        reset()
                    } else {
                        aiExplanationFallbackId = questionId
                        aiExplanationProviders = aiExplanationProviders + (questionId to "offline")
                        if (e is java.io.IOException) offline = true
                    }
                }
            } finally {
                if (expected == generation) aiExplanationLoadingId = null
            }
        }
    }
    fun openProgress() = task { progress = JSONObject(cached("progress")); showProgress = true; course = null; clearLessonChat(); lesson = null; attempt = null }
    fun back() {
        when {
            attempt != null -> attempt = null
            lesson != null -> { clearLessonChat(); lesson = null }
            course != null -> course = null
            else -> showProgress = false
        }
    }
    fun logout() {
        val oldToken = token
        val oldEndpoint = endpoint
        vault.edit().remove(owner).apply()
        if (accountState.getString("last_owner", null) == owner) accountState.edit().remove("last_owner").apply()
        generation++
        reset()
        if (oldToken.isBlank()) return
        // Revoke the captured session without blocking local logout or touching a new login.
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder().url("$oldEndpoint/api/auth/logout")
                    .header("Authorization", "Bearer $oldToken")
                    .post("{}".toRequestBody("application/json".toMediaType())).build()
                client.newCall(request).execute().use { }
            } catch (e: CancellationException) { throw e }
            catch (_: java.io.IOException) { /* Offline: server expiry still limits the old token. */ }
        }
    }
}

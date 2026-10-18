package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.os.SystemClock
import android.widget.Toast
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.util.GeminiAssistant
import com.example.util.StudyChallenge
import com.example.util.StudySkillRequest
import com.example.util.StudyReminderWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import androidx.room.withTransaction
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

private val Context.studySpaceOnboardingStore by preferencesDataStore(name = "studyspace_onboarding")

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
class StudyViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val SESSION_TIMEOUT_MS = 5 * 60 * 1000L
    }

    private val database = StudyDatabase.getDatabase(application)
    private val repository = StudyRepository(database.studyDao())
    private val prefs = application.getSharedPreferences("edupomodoro_prefs", Context.MODE_PRIVATE)
    private val appContext = application.applicationContext
    private val onboardingStore = appContext.studySpaceOnboardingStore
    private var lastBackgroundAtMs = 0L

    // --- User Session ---
    private val _currentUser = mutableStateOf<User?>(null)
    val currentUser: State<User?> = _currentUser

    private val _currentUsername = MutableStateFlow<String?>(null)
    val currentUsername: StateFlow<String?> = _currentUsername.asStateFlow()
    private val _onboardingVisible = mutableStateOf(false)
    val onboardingVisible: State<Boolean> = _onboardingVisible
    private val _onboardingSaving = mutableStateOf(false)
    val onboardingSaving: State<Boolean> = _onboardingSaving


    private val _isBiometricsEnabled = mutableStateOf(false)
    val isBiometricsEnabled: State<Boolean> = _isBiometricsEnabled

    private val _isAppLocked = mutableStateOf(false)
    val isAppLocked: State<Boolean> = _isAppLocked

    fun unlockAppWithBiometrics() {
        _isAppLocked.value = false
        val msg = if (_language.value == "vi") "Xác thực sinh trắc học thành công! \uD83D\uDD13" else "Biometric authorization success! \uD83D\uDD13"
        showPushNotification(msg)
    }

    fun lockApp() {
        if (_isBiometricsEnabled.value) {
            _isAppLocked.value = true
        }
    }

    fun onAppBackgrounded() {
        lastBackgroundAtMs = System.currentTimeMillis()
    }

    fun onAppForegrounded() {
        if (lastBackgroundAtMs == 0L) return
        val inactiveMs = System.currentTimeMillis() - lastBackgroundAtMs
        lastBackgroundAtMs = 0L

        val shouldLockSession = inactiveMs >= SESSION_TIMEOUT_MS &&
            _currentUser.value != null &&
            _isBiometricsEnabled.value

        if (shouldLockSession) {
            _isAppLocked.value = true
            val msg = if (_language.value == "vi") {
                "Phiên đã hết hạn, vui lòng xác thực lại."
            } else {
                "Session expired. Please authenticate again."
            }
            showPushNotification(msg)
        }
    }

    init {
        _isBiometricsEnabled.value = prefs.getBoolean("is_biometrics_enabled", false)
        viewModelScope.launch {
            _currentUsername.filterNotNull().flatMapLatest { user ->
                combine(database.studyDao().getTasksByUserId(user), database.studyDao().getSessionsByUserId(user)) { tasks, sessions -> tasks to sessions }
                    .drop(1)
                    .debounce(12_000)
            }.collect {
                val onlineOwner = appContext.getSharedPreferences("online_account_state", Context.MODE_PRIVATE).getString("last_owner", null)
                if (onlineOwner == _currentUsername.value) com.example.learning.PersonalStudySyncWorker.scheduleChanged(appContext)
            }
        }
    }

    // --- Authentication ---
    fun changeDisplayName(name: String) {
        val user = _currentUser.value ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            val updated = user.copy(displayName = name.trim().take(120))
            repository.insertUser(updated)
            if (_currentUser.value?.username == user.username) _currentUser.value = updated
        }
    }
    fun activateOnlineAccount(username: String, remoteId: Long, endpoint: String, password: String, displayName: String, linkCurrent: Boolean = false, beforeSwitch: (String) -> Unit) {
        viewModelScope.launch {
            val existing = repository.getUser(username)
            val scope = com.example.learning.AccountScope.localId(endpoint,remoteId)
            val bound = prefs.getString("binding:$scope",null)
            val ownsLegacy = existing != null && ((linkCurrent && _currentUser.value?.username == username) || withContext(Dispatchers.Default) { com.example.util.SecurityUtils.verifyPassword(password, existing.passwordHash) })
            if (ownsLegacy) {
                val legacyUser = existing ?: return@launch
                if (com.example.util.SecurityUtils.needsPasswordRehash(legacyUser.passwordHash)) {
                    val upgradedHash = withContext(Dispatchers.Default) { com.example.util.SecurityUtils.hashPassword(password) }
                    repository.upgradePasswordHash(username, legacyUser.passwordHash, upgradedHash)
                }
            }
            val localId = bound ?: if (ownsLegacy) username else scope
            val stored = repository.getUser(localId)
            val user = stored ?: User(username = localId, displayName = displayName.ifBlank { username }, passwordHash = withContext(Dispatchers.Default) { com.example.util.SecurityUtils.hashPassword(password) })
            if (stored == null) repository.insertUser(user)
            _onboardingVisible.value = !linkCurrent && !isOnboardingComplete(localId)
            beforeSwitch(localId)
            prefs.edit().putString("binding:$scope",localId).apply()
            _currentUser.value = user
            _currentUsername.value = localId
            prefs.edit().putString("last_username", localId).apply()
            _currentScreen.value = "dashboard"
            if (_isBiometricsEnabled.value) _isAppLocked.value = true
        }
    }

    fun restoreOnlineAccount(localId: String, remoteUsername: String) {
        viewModelScope.launch {
            val user = repository.getUser(localId) ?: User(
                username = localId,
                displayName = remoteUsername,
                passwordHash = ""
            ).also { repository.insertUser(it) }
            _onboardingVisible.value = !isOnboardingComplete(localId)
            _currentUser.value = user
            _currentUsername.value = localId
            prefs.edit().putString("last_username", localId).apply()
            _currentScreen.value = "dashboard"
            if (_isBiometricsEnabled.value) _isAppLocked.value = true
        }
    }

    private suspend fun isOnboardingComplete(username: String): Boolean = runCatching {
        onboardingStore.data.first()[booleanPreferencesKey("completed:$username")] == true
    }.getOrDefault(false)

    fun completeOnboarding(subject: String, dailyMinutes: Int, lessonId: Long? = null, lessonTitle: String = "", courseTitle: String = subject) {
        val userId = _currentUsername.value ?: return
        if (_onboardingSaving.value) return
        viewModelScope.launch {
            _onboardingSaving.value = true
            try {
                if (lessonId != null && lessonId > 0 && lessonTitle.isNotBlank()) {
                    val link = "studyspace://lesson/$lessonId"
                    val existing = database.studyDao().getTasksByUserId(userId).first().firstOrNull { it.description == link && !it.isCompleted }
                    if (existing == null) {
                        val task = StudyTask(userId = userId, title = lessonTitle, category = courseTitle, description = link, targetMinutes = 25)
                        val id = repository.insertTask(task).toInt()
                        StudyReminderWorker.schedule(appContext, task.copy(id = id))
                    }
                }
                onboardingStore.edit { values ->
                    values[booleanPreferencesKey("completed:$userId")] = true
                    values[stringPreferencesKey("subject:$userId")] = subject.trim().take(120)
                    values[intPreferencesKey("daily_minutes:$userId")] = dailyMinutes.coerceIn(15, 180)
                    lessonId?.let { values[longPreferencesKey("first_lesson:$userId")] = it }
                }
                _onboardingVisible.value = false
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                showPushNotification(if (_language.value == "vi") "Chưa lưu được thiết lập ban đầu. Hãy thử lại." else "Could not save your setup. Please try again.")
            } finally {
                _onboardingSaving.value = false
            }
        }
    }

    fun planLesson(lessonId: Long, title: String, courseTitle: String, startFocus: Boolean) {
        val user = _currentUsername.value ?: return
        viewModelScope.launch {
            val link = "studyspace://lesson/$lessonId"
            val existing = database.studyDao().getTasksByUserId(user).first().firstOrNull { it.description == link && !it.isCompleted }
            val task = existing ?: StudyTask(userId = user, title = title, category = courseTitle, description = link, targetMinutes = 25).let { it.copy(id = repository.insertTask(it).toInt()) }
            StudyReminderWorker.schedule(appContext, task)
            if (startFocus) { selectTaskToFocus(task); navigateTo("focus") } else { navigateTo("planner") }
        }
    }
    fun logout() {
        showPushNotification(if (_language.value == "vi") "Đã đăng xuất tài khoản." else "Logged out successfully.")
        pauseTimer()
        _currentUser.value = null
        _currentUsername.value = null
        _onboardingVisible.value = false
        prefs.edit().remove("last_username").apply()
        _currentScreen.value = "dashboard"
    }

    fun updateUserProfile(
        displayName: String,
        email: String,
        phoneNumber: String,
        school: String,
        major: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val user = _currentUser.value ?: return
        val trimmedDisplay = displayName.trim()
        val trimmedEmail = email.trim()
        val trimmedPhone = phoneNumber.trim()
        val trimmedSchool = school.trim()
        val trimmedMajor = major.trim()

        if (trimmedDisplay.isBlank() || trimmedEmail.isBlank() || trimmedPhone.isBlank()) {
            onResult(false, if (_language.value == "vi") "Tên, Email và Số điện thoại không được để trống!" else "Name, Email and Phone cannot be blank!")
            return
        }
        if (!trimmedEmail.contains("@") || !trimmedEmail.contains(".")) {
            onResult(false, if (_language.value == "vi") "Email không hợp lệ!" else "Invalid email address!")
            return
        }

        viewModelScope.launch {
            val updatedUser = user.copy(
                displayName = trimmedDisplay,
                email = trimmedEmail,
                phoneNumber = trimmedPhone,
                school = trimmedSchool,
                major = trimmedMajor
            )
            repository.insertUser(updatedUser)
            _currentUser.value = updatedUser
            onResult(true, if (_language.value == "vi") "Cập nhật tài khoản thành công!" else "Profile updated successfully!")
            showPushNotification(if (_language.value == "vi") "Đã cập nhật thông tin tài khoản." else "Your profile has been updated.")
        }
    }

    // --- Database Flow States ---
    val allTasks: StateFlow<List<StudyTask>> = _currentUsername
        .flatMapLatest { username ->
            if (username == null) flowOf(emptyList()) else repository.getTasksForUser(username)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allSessions: StateFlow<List<FocusSession>> = _currentUsername
        .flatMapLatest { username ->
            if (username == null) flowOf(emptyList()) else repository.getSessionsForUser(username)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allRewards: StateFlow<List<RewardLog>> = _currentUsername
        .flatMapLatest { username ->
            if (username == null) flowOf(emptyList()) else repository.getRewardsForUser(username)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val totalPoints: StateFlow<Int?> = _currentUsername
        .flatMapLatest { username ->
            if (username == null) flowOf(0) else repository.getTotalPointsForUser(username)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    // --- UI/UX Screen state (No navigation graph required) ---
    private val _currentScreen = mutableStateOf("dashboard") // dashboard, focus, planner, groups, profile
    val currentScreen: State<String> = _currentScreen

    fun navigateTo(screen: String) {
        _currentScreen.value = screen
    }

    // --- Multi-Language & Theme customization ---
    private val _isDarkMode = mutableStateOf(false) // default to light mode to showcase the Professional Polish theme as requested
    val isDarkMode: State<Boolean> = _isDarkMode

    fun toggleDarkMode() {
        _isDarkMode.value = !_isDarkMode.value
    }

    private val _selectedTheme = mutableStateOf("space_slate") // space_slate, emerald_forest, royal_gold
    val selectedTheme: State<String> = _selectedTheme

    fun setTheme(theme: String) {
        _selectedTheme.value = theme
    }

    private val _language = mutableStateOf("vi") // "vi" (Vietnamese) or "en" (English)
    val language: State<String> = _language

    fun setLanguage(lang: String) {
        _language.value = lang
    }

    // --- Pomodoro Timer State ---
    private val _timerSeconds = mutableStateOf(25 * 60)
    val timerSeconds: State<Int> = _timerSeconds

    private val _timerMaxSeconds = mutableStateOf(25 * 60)
    val timerMaxSeconds: State<Int> = _timerMaxSeconds

    private val _timerRunning = mutableStateOf(false)
    val timerRunning: State<Boolean> = _timerRunning

    private val _sessionType = mutableStateOf("focus") // "focus", "break", "meditation"
    val sessionType: State<String> = _sessionType

    private val _selectedTask = mutableStateOf<StudyTask?>(null)
    val selectedTask: State<StudyTask?> = _selectedTask

    private var timerJob: Job? = null
    private var timerStartedAtMs = 0L
    private var timerStartedWithSeconds = 0
    private var persistedFocusElapsedSeconds: Int = 0

    // --- Meditation Guided Mode Animation scale (Breathe in/out loop) ---
    private val _breatheState = mutableStateOf("Hít vào") // Hít vào (Breathe In), Giữ hơi (Hold), Thở ra (Breathe Out)
    val breatheState: State<String> = _breatheState

    private val _breatheScale = mutableStateOf(1.0f)
    val breatheScale: State<Float> = _breatheScale

    // --- Reward Streak System ---
    private val _todayFocusCount = mutableStateOf(0)
    val todayFocusCount: State<Int> = _todayFocusCount

    // --- Smart Notification simulation (In-app popup bubble banner) ---
    private val _pushNotificationMessage = mutableStateOf<String?>(null)
    val pushNotificationMessage: State<String?> = _pushNotificationMessage

    fun showPushNotification(message: String) {
        _pushNotificationMessage.value = message
        // Auto-dismiss in-app push notification banner after 4.5 seconds
        viewModelScope.launch {
            delay(4500)
            if (_pushNotificationMessage.value == message) {
                _pushNotificationMessage.value = null
            }
        }
    }

    fun dismissPushNotification() {
        _pushNotificationMessage.value = null
    }

    // --- Soundscape Audio States ---
    private val _activeSoundscapeMode = mutableStateOf("none")
    val activeSoundscapeMode: State<String> = _activeSoundscapeMode

    fun setSoundscapeMode(mode: String) {
        _activeSoundscapeMode.value = mode
        if (_timerRunning.value && mode != "none") {
            com.example.util.SoundscapeManager.startSound(mode)
        } else {
            com.example.util.SoundscapeManager.stopSound()
        }
    }

    fun toggleBiometrics() {
        _isBiometricsEnabled.value = !_isBiometricsEnabled.value
        prefs.edit().putBoolean("is_biometrics_enabled", _isBiometricsEnabled.value).apply()
        val msg = if (_isBiometricsEnabled.value) {
            if (_language.value == "vi") "Đã bật đăng nhập sinh trắc học vân tay/khuôn mặt." else "Biometric fingerprint/face login enabled."
        } else {
            if (_language.value == "vi") "Đã tắt khóa sinh trắc học." else "Biometric login disabled."
        }
        showPushNotification(msg)
    }

    // --- Artificial Intelligence (Gemini) Personalized Exercises ---
    private val _aiChallengeLoading = mutableStateOf(false)
    val aiChallengeLoading: State<Boolean> = _aiChallengeLoading

    private val _currentChallenge = mutableStateOf<StudyChallenge?>(null)
    val currentChallenge: State<StudyChallenge?> = _currentChallenge

    fun generateAISkillChallenge(request: StudySkillRequest) {
        viewModelScope.launch {
            _aiChallengeLoading.value = true
            try {
                _currentChallenge.value = GeminiAssistant.buildOfflineChallenge(request)
            } catch (e: Exception) {
                _currentChallenge.value = StudyChallenge(
                    title = "Phương pháp Feynman cơ bản",
                    diagnosis = "AI không lấy được phản hồi đầy đủ, nên app chuyển sang bài tập offline an toàn.",
                    tip = "Học bằng cách dạy lại giúp bạn phát hiện lỗ hổng kiến thức nhanh hơn đọc lại thụ động.",
                    microPlan = listOf("5p: gạch ý chính", "15p: tự giảng lại", "5p: ghi phần còn vấp"),
                    exercise = "Hãy tóm tắt chủ đề '${request.goal}' một cách súc tích nhất để giảng lại cho một người chưa biết gì.",
                    answerChecklist = listOf("Có ví dụ cụ thể", "Không dùng thuật ngữ mơ hồ", "Nêu được câu hỏi còn chưa chắc"),
                    estimatedMinutes = request.timeBudgetMinutes,
                    difficulty = "Vừa sức",
                    nextAction = "Chạy một phiên Focus ngắn và lưu lại phần còn vấp."
                )
            } finally {
                _aiChallengeLoading.value = false
            }
        }
    }

    fun generateAISkillChallenge(category: String, taskTitle: String) {
        generateAISkillChallenge(
            StudySkillRequest(
                category = category,
                goal = taskTitle
            )
        )
    }

    // Complete AI Challenge & reward point gamification
    fun claimAIChallengeRewards() {
        val currentUid = _currentUsername.value ?: "guest"
        viewModelScope.launch {
            val pts = 50
            val reason = if (_language.value == "vi") {
                "Hoàn thành thử thách kỹ năng từ AI: ${_currentChallenge.value?.title ?: "Kỹ năng cá nhân hóa"}"
            } else {
                "Completed AI skill challenge: ${_currentChallenge.value?.title ?: "Personalized skill"}"
            }
            repository.insertReward(RewardLog(userId = currentUid, pointsEarned = pts, reason = reason))
            _currentChallenge.value = null
        }
    }

    fun clearAIChallenge() {
        _currentChallenge.value = null
    }

    fun showAIChallenge(result: StudyChallenge) { _currentChallenge.value = result }

    fun claimNewStudyBadges(username: String, achievedIds: Set<String>): List<String> {
        if (username.isBlank() || achievedIds.isEmpty()) return emptyList()
        val newlyUnlocked = achievedIds.filterNot { badgeId ->
            prefs.getBoolean("study_badge_seen:$username:$badgeId", false)
        }
        if (newlyUnlocked.isNotEmpty()) {
            prefs.edit().apply { newlyUnlocked.forEach { putBoolean("study_badge_seen:$username:$it", true) } }.apply()
        }
        return newlyUnlocked
    }

    // --- AI Smart Planner Scheduler ---
    private val _aiPlannerLoading = mutableStateOf(false)
    val aiPlannerLoading: State<Boolean> = _aiPlannerLoading

    private val _aiRecommendedTasks = mutableStateOf<List<StudyTask>>(emptyList())
    val aiRecommendedTasks: State<List<StudyTask>> = _aiRecommendedTasks
    private val _aiPlanSaving = mutableStateOf(false)
    val aiPlanSaving: State<Boolean> = _aiPlanSaving

    fun generateAISchedule(category: String, major: String) {
        viewModelScope.launch {
            _aiPlannerLoading.value = true
            try {
                val result = GeminiAssistant.offlinePlan(category)
                _aiRecommendedTasks.value = result
            } catch (e: Exception) {
                _aiRecommendedTasks.value = emptyList()
            } finally {
                _aiPlannerLoading.value = false
            }
        }
    }

    fun saveAISchedule() {
        val currentUid = _currentUsername.value ?: "guest"
        val tasksToSave = _aiRecommendedTasks.value
        if (tasksToSave.isEmpty()) return
        
        viewModelScope.launch {
            for (task in tasksToSave) {
                val correctTask = task.copy(userId = currentUid)
                val taskId = repository.insertTask(correctTask).toInt()
                StudyReminderWorker.schedule(appContext, correctTask.copy(id = taskId))
            }
            _aiRecommendedTasks.value = emptyList()
            val msg = if (_language.value == "vi") "Đã nạp 3 mục tiêu từ AI gợi ý vào của bạn!" else "Successfully populated 3 AI tasks into your schedule!"
            showPushNotification(msg)
        }
    }

    fun clearAIRecommendations() {
        _aiRecommendedTasks.value = emptyList()
    }

    fun selectTaskToFocus(task: StudyTask?) {
        if (_timerRunning.value) {
            pauseTimer()
        }
        _selectedTask.value = task
        if (task != null) {
            _sessionType.value = "focus"
            meditationAnimJob?.cancel()
            _timerSeconds.value = task.targetMinutes * 60
            _timerMaxSeconds.value = task.targetMinutes * 60
        } else {
            _timerSeconds.value = 25 * 60
            _timerMaxSeconds.value = 25 * 60
        }
        persistedFocusElapsedSeconds = 0
    }

    fun startTimer() {
        if (_timerRunning.value) return
        if (_timerSeconds.value <= 0) _timerSeconds.value = _timerMaxSeconds.value
        timerStartedWithSeconds = _timerSeconds.value
        timerStartedAtMs = SystemClock.elapsedRealtime()
        _timerRunning.value = true
        
        if (_activeSoundscapeMode.value != "none") {
            com.example.util.SoundscapeManager.startSound(_activeSoundscapeMode.value)
        }

        timerJob = viewModelScope.launch {
            while (_timerRunning.value) {
                delay(1000)
                updateTimerFromClock()
                if (_timerSeconds.value == 0) break
            }
            if (_timerRunning.value) onTimerComplete()
        }
        
        // Trigger breathing animation background cycle if meditation mode starts
        if (_sessionType.value == "meditation") {
            startMeditationBreathingCycles()
        }
    }

    fun pauseTimer() {
        if (_timerRunning.value) {
            updateTimerFromClock()
            if (_timerSeconds.value == 0) {
                onTimerComplete()
                return
            }
        }
        stopTimer(commitPartialFocus = true)
    }

    private fun updateTimerFromClock() {
        if (!_timerRunning.value) return
        val remaining = FocusCountdown.remainingSeconds(timerStartedWithSeconds, SystemClock.elapsedRealtime() - timerStartedAtMs)
        val elapsed = (_timerSeconds.value - remaining).coerceAtLeast(0)
        _timerSeconds.value = remaining
        if (elapsed > 0 && _sessionType.value == "focus") {
            _selectedTask.value?.let { task ->
                _selectedTask.value = task.copy(elapsedTimeSeconds = task.elapsedTimeSeconds + elapsed)
            }
        }
    }

    private fun stopTimer(commitPartialFocus: Boolean) {
        val wasRunning = _timerRunning.value
        val pendingFocusSeconds = if (commitPartialFocus && wasRunning) {
            pendingFocusSecondsForCurrentTimer()
        } else {
            0
        }
        val taskSnapshot = _selectedTask.value
        val taskTitleSnapshot = taskSnapshot?.title ?: "Tập trung tự do"

        _timerRunning.value = false
        timerJob?.cancel()
        timerJob = null
        com.example.util.SoundscapeManager.stopSound()

        if (pendingFocusSeconds > 0) {
            persistedFocusElapsedSeconds += FocusSessionAccounting.wholeMinutes(pendingFocusSeconds) * 60
            persistPartialFocusSession(
                seconds = pendingFocusSeconds,
                task = taskSnapshot,
                taskTitle = taskTitleSnapshot
            )
        }
    }

    fun resetTimer() {
        pauseTimer()
        _timerSeconds.value = _timerMaxSeconds.value
        persistedFocusElapsedSeconds = 0
    }

    // Change Session mode (Focus, Break, Meditation)
    fun setSessionMode(mode: String) {
        pauseTimer()
        _sessionType.value = mode
        val length = when (mode) {
            "focus" -> (_selectedTask.value?.targetMinutes ?: 25) * 60
            "break" -> 5 * 60
            "meditation" -> 10 * 60 // 10 minutes meditation guide
            else -> 25 * 60
        }
        _timerSeconds.value = length
        _timerMaxSeconds.value = length
        persistedFocusElapsedSeconds = 0

        if (mode == "meditation") {
            startMeditationBreathingCycles()
        }
    }

    fun setCustomTimerDuration(minutes: Int) {
        pauseTimer()
        val length = minutes * 60
        _timerSeconds.value = length
        _timerMaxSeconds.value = length
        persistedFocusElapsedSeconds = 0
    }

    // Guided meditation instructions loop
    private var meditationAnimJob: Job? = null
    private fun startMeditationBreathingCycles() {
        meditationAnimJob?.cancel()
        meditationAnimJob = viewModelScope.launch {
            while (_sessionType.value == "meditation" && _timerRunning.value) {
                // Phase 1: Inhale
                _breatheState.value = if (_language.value == "vi") "Hít vào chậm rãi..." else "Inhale deeply..."
                // Grow bubble from 1.0f to 1.8f
                for (i in 1..20) {
                    _breatheScale.value = 1.0f + (0.04f * i)
                    delay(200)
                }
                
                // Phase 2: Hold
                _breatheState.value = if (_language.value == "vi") "Giữ hơi thở thư giãn..." else "Hold breath..."
                delay(2000)
                
                // Phase 3: Exhale
                _breatheState.value = if (_language.value == "vi") "Thở ra nhẹ nhàng..." else "Exhale completely..."
                // Shrink bubble back to 1.0f
                for (i in 1..20) {
                    _breatheScale.value = 1.8f - (0.04f * i)
                    delay(200)
                }
                
                // Phase 4: Rest
                delay(1000)
            }
        }
    }

    private fun focusElapsedSecondsForCurrentTimer(): Int {
        return (_timerMaxSeconds.value - _timerSeconds.value).coerceAtLeast(0)
    }

    private fun pendingFocusSecondsForCurrentTimer(): Int {
        if (_sessionType.value != "focus") return 0
        return (focusElapsedSecondsForCurrentTimer() - persistedFocusElapsedSeconds).coerceAtLeast(0)
    }

    private fun persistPartialFocusSession(
        seconds: Int,
        task: StudyTask?,
        taskTitle: String
    ) {
        if (seconds <= 0) return
        val currentUid = _currentUsername.value ?: "guest"
        val durationMins = FocusSessionAccounting.wholeMinutes(seconds)

        viewModelScope.launch {
            if (durationMins > 0) {
                repository.insertSession(
                    FocusSession(
                        userId = currentUid,
                        taskId = task?.id,
                        taskTitle = taskTitle,
                        durationMinutes = durationMins,
                        sessionType = "focus"
                    )
                )
            }
            persistTaskProgress(task)
        }
    }

    private suspend fun persistTaskProgress(task: StudyTask?): Boolean {
        val current = task ?: return false
        val result = database.studyDao().applyFocusProgress(current) ?: return false
        if (result.task.isCompleted) {
            StudyReminderWorker.cancel(appContext, result.task.id)
        } else {
            StudyReminderWorker.schedule(appContext, result.task)
        }
        return result.newlyCompleted
    }

    private fun onTimerComplete() {
        val type = _sessionType.value
        val durationMins = _timerMaxSeconds.value / 60
        val pendingFocusSeconds = pendingFocusSecondsForCurrentTimer()
        val selectedTaskSnapshot = _selectedTask.value
        val targetTitle = selectedTaskSnapshot?.title ?: "Tập trung tự do"
        val currentUid = _currentUsername.value ?: "guest"

        stopTimer(commitPartialFocus = false)
        setSessionMode(if (type == "focus") "meditation" else "focus")

        viewModelScope.launch {
            val sessionDurationMinutes = if (type == "focus") {
                FocusSessionAccounting.wholeMinutes(pendingFocusSeconds)
            } else {
                durationMins
            }
            if (sessionDurationMinutes > 0) {
                repository.insertSession(
                    FocusSession(
                        userId = currentUid,
                        taskId = selectedTaskSnapshot?.id,
                        taskTitle = targetTitle,
                        durationMinutes = sessionDurationMinutes,
                        sessionType = type
                    )
                )
            }

            // Trigger action based on completed session
            when (type) {
                "focus" -> {
                    _todayFocusCount.value += 1
                    
                    // Gamification Rewards for Focus completion (+100 pts)
                    val pts = 100
                    val rewReason = if (_language.value == "vi") {
                        "Hoàn thành phiên tập trung Pomodoro ($durationMins phút): $targetTitle"
                    } else {
                        "Completed focus Pomodoro ($durationMins minutes): $targetTitle"
                    }
                    repository.insertReward(RewardLog(userId = currentUid, pointsEarned = pts, reason = rewReason))
                    
                    // Update current task in database
                    selectedTaskSnapshot?.let { current ->
                        if (persistTaskProgress(current)) {
                            val compPts = 150
                            val compReason = if (_language.value == "vi") "Hoàn thành mục tiêu học tập: ${current.title}" else "Achieved study goal: ${current.title}"
                            repository.insertReward(RewardLog(userId = currentUid, pointsEarned = compPts, reason = compReason))
                            showPushNotification("🏆 Mục tiêu '${current.title}' đã đạt 100%! Thưởng thêm $compPts điểm.")
                        }
                    }

                    showPushNotification("🎯 Phiên học hoàn thành! Bạn nhận được +100 điểm thưởng. Hãy thiền định ngắn để thả lỏng.")
                    
                }
                "meditation" -> {
                    val pts = 50
                    val rewReason = if (_language.value == "vi") "Hoàn thành bài tập thiền phục hồi tâm trí ($durationMins phút)" else "Completed mindfulness meditation ($durationMins minutes)"
                    repository.insertReward(RewardLog(userId = currentUid, pointsEarned = pts, reason = rewReason))
                    showPushNotification("🌸 Đã hoàn thành thiền định ngắn! Nhận +50 điểm. Đầu óc bạn đã phục hồi tối đa.")
                }
                "break" -> {
                    showPushNotification("⚡ Đã hết giờ nghỉ ngơi! Hãy sạc năng lượng và bắt đầu phiên Pomodoro mới.")
                }
            }
        }
    }

    // --- Task Database Actions ---
    fun completePlannedTask(task: StudyTask) {
        val user = _currentUsername.value ?: return
        viewModelScope.launch {
            if (database.studyDao().completePlan(user, task.id)) {
                StudyReminderWorker.cancel(appContext, task.id)
            }
        }
    }

    fun completeLessonPlan(lessonId: Long) {
        val user = _currentUsername.value ?: return
        viewModelScope.launch {
            database.studyDao().getTasksByUserId(user).first()
                .filter { it.description == "studyspace://lesson/$lessonId" && !it.isCompleted }
                .forEach { task ->
                    if (database.studyDao().completePlan(user, task.id)) {
                        StudyReminderWorker.cancel(appContext, task.id)
                    }
                }
        }
    }

    fun createTask(title: String, category: String, description: String, targetMins: Int, dueAtMillis: Long = System.currentTimeMillis() + 86400000L * 3, onSaved: () -> Unit = {}) {
        val currentUid = _currentUsername.value ?: "guest"
        viewModelScope.launch {
            if (title.isBlank()) return@launch
            val newTask = StudyTask(
                userId = currentUid,
                title = title,
                category = if (category.isBlank()) "Khác" else category,
                description = description,
                targetMinutes = targetMins,
                dueDate = dueAtMillis
            )
            val taskId = repository.insertTask(newTask).toInt()
            StudyReminderWorker.schedule(appContext, newTask.copy(id = taskId))
            
            val msg = if (_language.value == "vi") "Đã tạo mục tiêu '$title' thành công!" else "Study goal '$title' created successfully!"
            showPushNotification(msg)
            onSaved()
        }
    }

    fun saveGeneratedStudyPlan(tasks: List<StudyTask>, onSaved: () -> Unit) {
        if (tasks.isEmpty() || _aiPlanSaving.value) return
        val currentUid = _currentUsername.value ?: "guest"
        viewModelScope.launch {
            _aiPlanSaving.value = true
            try {
                val uniqueTasks = tasks.distinctBy { it.title }
                val savedTasks = database.withTransaction {
                    uniqueTasks.map { task ->
                        val ownedTask = task.copy(id = 0, userId = currentUid)
                        ownedTask.copy(id = repository.insertTask(ownedTask).toInt())
                    }
                }
                savedTasks.forEach { task -> runCatching { StudyReminderWorker.schedule(appContext, task) } }
                val message = if (_language.value == "vi") {
                    "Đã thêm ${uniqueTasks.size} việc học vào Kế hoạch."
                } else {
                    "Added ${uniqueTasks.size} study tasks to your plan."
                }
                showPushNotification(message)
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = if (_language.value == "vi") "Không thể lưu kế hoạch. Hãy thử lại." else "Could not save the plan. Please try again."
                showPushNotification(message)
            } finally {
                _aiPlanSaving.value = false
            }
        }
    }

    fun deleteTask(task: StudyTask) {
        viewModelScope.launch {
            repository.deleteTask(task)
            StudyReminderWorker.cancel(appContext, task.id)
            val msg = if (_language.value == "vi") "Đã xóa mục tiêu '${task.title}'." else "Goal '${task.title}' deleted."
            showPushNotification(msg)
            
            // Unselect if currently focused
            if (_selectedTask.value?.id == task.id) {
                selectTaskToFocus(null)
            }
        }
    }

    // --- Exporters Trigger ---
    private val _isExporting = mutableStateOf(false)
    val isExporting: State<Boolean> = _isExporting

    fun exportData(context: Context, format: String) {
        _isExporting.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val tasks = allTasks.value
            val sessions = allSessions.value
            val resultPath = if (format == "CSV") {
                repository.exportToCSV(context, tasks, sessions)
            } else {
                repository.exportToPDF(context, tasks, sessions)
            }
            
            withContext(Dispatchers.Main) {
                _isExporting.value = false
                if (resultPath != null) {
                    val successMsg = if (_language.value == "vi") {
                        "Đã xuất tệp $format tại thư mục Tài liệu (Documents)! Đường dẫn: $resultPath"
                    } else {
                        "Exported $format file in your Documents folder! Location: $resultPath"
                    }
                    Toast.makeText(context, successMsg, Toast.LENGTH_LONG).show()
                    showPushNotification("💾 Đã xuất báo cáo $format thành công!")
                } else {
                    val errorMsg = if (_language.value == "vi") "Lỗi xuất tập tin. Hãy thử lại." else "Failed to export. Please try again."
                    Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun clearAllData() {
        val currentUid = _currentUsername.value ?: "guest"
        viewModelScope.launch {
            repository.resetDatabaseForUser(currentUid)
            _todayFocusCount.value = 0
            _selectedTask.value = null
            setSessionMode("focus")
            val msg = if (_language.value == "vi") "Đã khôi phục cài đặt gốc và xóa mọi dữ liệu học tập!" else "Database cleared. App master-reset complete!"
            showPushNotification(msg)
        }
    }

    fun exportDatabaseToJSON(): String {
        val tasksList = allTasks.value
        val sessionsList = allSessions.value
        val username = _currentUsername.value ?: "guest"
        
        return try {
            val root = org.json.JSONObject()
            root.put("version", 1)
            root.put("username", username)
            root.put("exportedAt", System.currentTimeMillis())
            
            val tasksArray = org.json.JSONArray()
            for (task in tasksList) {
                val tJson = org.json.JSONObject()
                tJson.put("title", task.title)
                tJson.put("category", task.category)
                tJson.put("description", task.description)
                tJson.put("targetMinutes", task.targetMinutes)
                tJson.put("elapsedTimeSeconds", task.elapsedTimeSeconds)
                tJson.put("isCompleted", task.isCompleted)
                tJson.put("dueDate", task.dueDate)
                tJson.put("isSmartReminderEnabled", task.isSmartReminderEnabled)
                tJson.put("reminderHour", task.reminderHour)
                tJson.put("reminderMinute", task.reminderMinute)
                tasksArray.put(tJson)
            }
            root.put("tasks", tasksArray)
            
            val sessionsArray = org.json.JSONArray()
            for (sess in sessionsList) {
                val sJson = org.json.JSONObject()
                sJson.put("taskTitle", sess.taskTitle)
                sJson.put("durationMinutes", sess.durationMinutes)
                sJson.put("sessionType", sess.sessionType)
                sJson.put("timestamp", sess.timestamp)
                sessionsArray.put(sJson)
            }
            root.put("sessions", sessionsArray)
            
            val plainJson = root.toString(2)
            com.example.util.SecurityUtils.encryptAes(plainJson)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    fun importDatabaseFromJSON(jsonStr: String, onResult: (Boolean, String) -> Unit) {
        if (jsonStr.isBlank()) {
            val msg = if (_language.value == "vi") "Chuỗi sao lưu rỗng!" else "Backup text is empty!"
            onResult(false, msg)
            return
        }
        val targetUser = _currentUsername.value ?: "guest"
        viewModelScope.launch {
            try {
                val decryptedJson = com.example.util.SecurityUtils.decryptAes(jsonStr)
                val root = org.json.JSONObject(decryptedJson)
                if (!root.has("tasks") && !root.has("sessions")) {
                    val msg = if (_language.value == "vi") "Mẫu dữ liệu sao lưu không đúng cấu trúc quy chuẩn!" else "Invalid backup data schema format!"
                    onResult(false, msg)
                    return@launch
                }
                
                val tasksArray = root.optJSONArray("tasks")
                if (tasksArray != null) {
                    for (i in 0 until tasksArray.length()) {
                        val tObj = tasksArray.getJSONObject(i)
                        val title = tObj.getString("title")
                        val category = tObj.getString("category")
                        val desc = tObj.optString("description", "")
                        val targetMins = tObj.optInt("targetMinutes", 25)
                        val elapsed = tObj.optInt("elapsedTimeSeconds", 0)
                        val comp = tObj.optBoolean("isCompleted", false)
                        val reminderEnabled = tObj.optBoolean("isSmartReminderEnabled", true)
                        
                        val importedTask = StudyTask(
                            userId = targetUser,
                            title = title,
                            category = category,
                            description = desc,
                            targetMinutes = targetMins,
                            elapsedTimeSeconds = elapsed,
                            isCompleted = comp,
                            isSmartReminderEnabled = reminderEnabled
                        )
                        val taskId = repository.insertTask(importedTask).toInt()
                        StudyReminderWorker.schedule(appContext, importedTask.copy(id = taskId))
                    }
                }
                
                val sessionsArray = root.optJSONArray("sessions")
                if (sessionsArray != null) {
                    for (i in 0 until sessionsArray.length()) {
                        val sObj = sessionsArray.getJSONObject(i)
                        val sTitle = sObj.optString("taskTitle", "Tập trung tự do")
                        val dur = sObj.optInt("durationMinutes", 25)
                        val sType = sObj.optString("sessionType", "focus")
                        val ts = sObj.optLong("timestamp", System.currentTimeMillis())
                        
                        val importedSession = FocusSession(
                            userId = targetUser,
                            taskTitle = sTitle,
                            durationMinutes = dur,
                            sessionType = sType,
                            timestamp = ts
                        )
                        repository.insertSession(importedSession)
                    }
                }
                
                val msg = if (_language.value == "vi") "Nhập sao lưu dữ liệu tập trung thành công!" else "Imported study files successfully!"
                onResult(true, msg)
                showPushNotification("📥 Đã khôi phục dữ liệu học tập thành công!")
            } catch (e: Exception) {
                e.printStackTrace()
                val msg = if (_language.value == "vi") "Lỗi cú pháp JSON: ${e.localizedMessage}" else "JSON Error: ${e.localizedMessage}"
                onResult(false, msg)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        com.example.util.SoundscapeManager.stopSound()
    }
}

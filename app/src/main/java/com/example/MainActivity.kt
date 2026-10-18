package com.example

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import com.example.ui.viewmodel.StudyViewModel
import com.example.data.FocusSession
import com.example.data.StudyTask
import com.example.util.StudyLocalization
import com.example.util.StudyReminderWorker
import com.example.util.StudySkillRequest
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : FragmentActivity() {
    private val viewModel: StudyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        StudyLocalization.init(applicationContext)
        StudyReminderWorker.createChannel(applicationContext)
        setContent {
            val isDark by viewModel.isDarkMode
            val selectedTheme by viewModel.selectedTheme
            val currentLanguage by viewModel.language

            MyApplicationTheme(isDark = isDark, themeName = selectedTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    com.example.learning.StudentWebPortalScreen(BuildConfig.STUDENT_WEB_BASE_URL)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.onAppBackgrounded()
    }

    override fun onResume() {
        super.onResume()
        viewModel.onAppForegrounded()
    }

    fun authenticateWithBiometrics(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val manager = BiometricManager.from(this)
        if (manager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            onError("Thiết bị chưa có sinh trắc học hoặc khóa màn hình.")
            return
        }

        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errString.toString())
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onError("Không nhận diện được sinh trắc học. Hãy thử lại.")
                }
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Mở khóa StudySpace")
            .setSubtitle("Dùng sinh trắc học hoặc PIN/mật khẩu thiết bị để tiếp tục")
            .setAllowedAuthenticators(authenticators)
            .build()

        prompt.authenticate(info)
    }
}

@Composable
fun StudyMainScreen(viewModel: StudyViewModel) {
    val currentUser by viewModel.currentUser
    val learningViewModel: com.example.learning.LearningViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    var connectAccount by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(currentUser?.username) {
        learningViewModel.bindOwner(currentUser?.username.orEmpty())
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        learningViewModel.restoreRememberedAccount(viewModel::restoreOnlineAccount)
    }
    androidx.compose.runtime.LaunchedEffect(learningViewModel.signedIn, currentUser?.username) {
        if (currentUser != null && !learningViewModel.signedIn) viewModel.logout()
    }
    val currentScreen by viewModel.currentScreen
    val currentLanguage by viewModel.language
    val notificationMessage by viewModel.pushNotificationMessage
    val isAppLocked by viewModel.isAppLocked
    val onboardingVisible by viewModel.onboardingVisible

    val configuration = LocalConfiguration.current
    val isExpandedWidth = configuration.screenWidthDp >= 600

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (currentUser == null || connectAccount) {
            com.example.learning.ProductAuth(viewModel, learningViewModel,
                onClose = if (currentUser != null) ({ connectAccount = false }) else null)
        } else if (isAppLocked) {
            BiometricLockScreen(viewModel, onLogout = { learningViewModel.logout(); viewModel.logout() })
        } else if (onboardingVisible) {
            com.example.learning.StudyOnboardingScreen(viewModel, learningViewModel)
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                if (isExpandedWidth) {
                    StudySideRail(viewModel = viewModel)
                }
                Scaffold(
                    modifier = Modifier.weight(1f),
                    bottomBar = {
                        if (!isExpandedWidth) {
                            StudyBottomBar(viewModel = viewModel)
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 760.dp)
                        ) {
                            AnimatedContent(
                                targetState = currentScreen,
                                transitionSpec = {
                                    (fadeIn(tween(220)) + slideInHorizontally(
                                        initialOffsetX = { it / 24 },
                                        animationSpec = tween(220)
                                    )) togetherWith (fadeOut(tween(150)) + slideOutHorizontally(
                                        targetOffsetX = { -it / 32 },
                                        animationSpec = tween(150)
                                    ))
                                },
                                label = "screen_transition"
                            ) { screen ->
                                when (screen) {
                                    "learning" -> com.example.learning.LearningScreen(owner = currentUser!!.username, vm = learningViewModel, study = viewModel, onConnect = { connectAccount = true })
                                    "dashboard" -> com.example.learning.LearningHome(viewModel, learningViewModel, onConnect = { connectAccount = true })
                                    "focus" -> FocusScreen(viewModel = viewModel)
                                    "planner" -> com.example.learning.StudyPlanScreen(viewModel, learningViewModel)
                                    "groups" -> com.example.learning.CourseChatScreen(learningViewModel)
                                    "profile" -> com.example.learning.ProductAccount(viewModel, learningViewModel, onConnect = { connectAccount = true })
                                    "assistant" -> com.example.learning.ProductAssistant(viewModel, learningViewModel)
                                    "goals" -> com.example.learning.ProductGoalsScreen(viewModel, learningViewModel)
                                    "reports" -> com.example.learning.ProductReportsScreen(viewModel, learningViewModel)
                                }
                            }
                        }
                    }
                }
            }
        }

        // High-fidelity Floating In-App Smart Push Notification Simulator Banner
        AnimatedVisibility(
            visible = notificationMessage != null,
            enter = slideInVertically(
                initialOffsetY = { -it },
                animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)
            ) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(20.dp)
        ) {
                notificationMessage?.let { msg ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                RoundedCornerShape(16.dp)
                            )
                            .testTag("push_notification_banner"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = "Notification",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (currentLanguage == "vi") "EDUSTUDY NHẮC NHỞ" else "STUDYSPACE ALERT",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = msg,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }

@Composable
fun StudyBottomBar(viewModel: StudyViewModel) {
    val currentScreen by viewModel.currentScreen
    val lang by viewModel.language

    // Custom Bottom navigation bar with elegant active pill indicators & navigationBarsPadding
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            )
            .navigationBarsPadding()
            .padding(vertical = 10.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val tabs = listOf(
            NavigationTabItem("dashboard", Icons.Default.Home, "product_home"),
            NavigationTabItem("learning", Icons.Default.School, "product_courses"),
            NavigationTabItem("planner", Icons.Default.Star, "product_plan"),
            NavigationTabItem("profile", Icons.Default.Person, "product_account")
        )

        tabs.forEach { tab ->
            val isActive = currentScreen == tab.id
            val titleText = StudyLocalization.translate(tab.titleRes, lang)

            val tintColor by animateColorAsState(
                targetValue = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                label = "color"
            )

            val activeBgColor by animateColorAsState(
                targetValue = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
                label = "bgColor"
            )

            val scale by animateFloatAsState(
                targetValue = if (isActive) 1.08f else 1.0f,
                label = "scale"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(activeBgColor)
                    .clickable { viewModel.navigateTo(tab.id) }
                    .padding(vertical = 12.dp, horizontal = 4.dp)
                    .testTag("nav_tab_${tab.id}"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = titleText,
                        tint = tintColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = titleText,
                        fontSize = 10.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color = tintColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun StudySideRail(viewModel: StudyViewModel) {
    val currentScreen by viewModel.currentScreen
    val lang by viewModel.language

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(88.dp)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                RoundedCornerShape(0.dp)
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(vertical = 20.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App logo or Cap
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.School,
                contentDescription = "App Logo Cap",
                tint = MaterialTheme.colorScheme.background,
                modifier = Modifier.size(24.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        val tabs = listOf(
            NavigationTabItem("dashboard", Icons.Default.Home, "product_home"),
            NavigationTabItem("learning", Icons.Default.School, "product_courses"),
            NavigationTabItem("planner", Icons.Default.Star, "product_plan"),
            NavigationTabItem("profile", Icons.Default.Person, "product_account")
        )

        tabs.forEach { tab ->
            val isActive = currentScreen == tab.id
            val titleText = StudyLocalization.translate(tab.titleRes, lang)

            val tintColor by animateColorAsState(
                targetValue = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                label = "color"
            )

            val activeBgColor by animateColorAsState(
                targetValue = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
                label = "bgColor"
            )

            val scale by animateFloatAsState(
                targetValue = if (isActive) 1.08f else 1.0f,
                label = "scale"
            )

            Box(
                modifier = Modifier
                    .scale(scale)
                    .clip(RoundedCornerShape(12.dp))
                    .background(activeBgColor)
                    .clickable { viewModel.navigateTo(tab.id) }
                    .padding(vertical = 12.dp, horizontal = 8.dp)
                    .fillMaxWidth()
                    .testTag("nav_tab_rail_${tab.id}"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = titleText,
                        tint = tintColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = titleText,
                        fontSize = 9.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color = tintColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

data class NavigationTabItem(
    val id: String,
    val icon: ImageVector,
    val titleRes: String
)


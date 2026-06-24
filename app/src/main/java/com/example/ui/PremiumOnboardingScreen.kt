package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.ReaderViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.ui.theme.*

// Custom Onboarding Steps Enumeration
enum class OnboardingStep {
    WELCOME,
    USER_NAME,
    NATIVE_LANG,
    TARGET_LANG,
    JOURNEY_PREVIEW,
    GOAL_SELECTION,
    FINAL_WELCOME
}

data class OnboardingLanguage(
    val code: String,
    val name: String,
    val flag: String,
    val sampleGreeting: String,
    val sampleTranslation: String
)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun PremiumOnboardingScreen(
    viewModel: ReaderViewModel,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentStep by remember { mutableStateOf(OnboardingStep.WELCOME) }
    
    // Captured inputs
    var userName by remember { mutableStateOf("") }
    var nativeLang by remember { mutableStateOf("") }
    var targetLang by remember { mutableStateOf("") }
    
    var nativeLangObj by remember { mutableStateOf<OnboardingLanguage?>(null) }
    var targetLangObj by remember { mutableStateOf<OnboardingLanguage?>(null) }
    
    var dailyGoalMinutes by remember { mutableStateOf(10) }
    var dailyGoalSentences by remember { mutableStateOf(10) }
    var accountCreatedState by remember { mutableStateOf(false) }

    // List of language opportunities
    val availableLanguages = remember {
        listOf(
            OnboardingLanguage("ar", "Arabic", "🇪🇬", "مرحبا", "Привет"),
            OnboardingLanguage("en", "English", "🇺🇸", "Hello", "Здравствуйте"),
            OnboardingLanguage("fr", "French", "🇫🇷", "Bonjour", "French translate"),
            OnboardingLanguage("de", "German", "🇩🇪", "Hallo", "German translate"),
            OnboardingLanguage("ru", "Russian", "🇷🇺", "Привет", "مرحبا"),
            OnboardingLanguage("it", "Italian", "🇮🇹", "Ciao", "Italian translate"),
            OnboardingLanguage("es", "Spanish", "🇪🇸", "Hola", "Spanish translate")
        )
    }

    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Background infinite shifting ambient gradient variables
    val infiniteTransition = rememberInfiniteTransition(label = "ShiftingBackground")
    val gradientShiftX by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BgShiftX"
    )
    val gradientShiftY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BgShiftY"
    )

    // Base color pallet
    val isDark = MaterialTheme.colorScheme.onBackground == Color(0xFFFCFAF7)
    val startGradientBgColor = if (isDark) Color(0xFF100F11) else Color(0xFFFAF9F6)
    val endGradientBgColor = if (isDark) Color(0xFF1E1C22) else Color(0xFFEFF1ED)
    
    val premiumAccent = Color(0xFF655D4D) // Premium gold-accented sand
    val secondaryPremiumAccent = Color(0xFFC5B59E)

    val appThemeStyle = LocalAppThemeStyle.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                if (appThemeStyle != "glassy") {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = if (isDark) {
                                listOf(Color(0xFF332F28), startGradientBgColor, endGradientBgColor)
                            } else {
                                listOf(Color(0xFFF9F7F0), startGradientBgColor, endGradientBgColor)
                            },
                            center = Offset(gradientShiftX, gradientShiftY),
                            radius = 1200f
                        )
                    )
                }
            }
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                if (targetState.ordinal > initialState.ordinal) {
                    slideInHorizontally(animationSpec = spring(stiffness = Spring.StiffnessLow)) { it } + fadeIn() with
                            slideOutHorizontally(animationSpec = spring(stiffness = Spring.StiffnessLow)) { -it } + fadeOut()
                } else {
                    slideInHorizontally(animationSpec = spring(stiffness = Spring.StiffnessLow)) { -it } + fadeIn() with
                            slideOutHorizontally(animationSpec = spring(stiffness = Spring.StiffnessLow)) { it } + fadeOut()
                }
            },
            label = "StepNavigation"
        ) { step ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Indicator Row (Except full immersive loader states)
                if (step != OnboardingStep.WELCOME) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OnboardingStep.values().forEach { s ->
                            val isActive = s == step
                            val isCompleted = s.ordinal < step.ordinal
                            
                            val progressColor = if (isActive) {
                                MaterialTheme.colorScheme.primary
                            } else if (isCompleted) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(4.dp)
                                    .clip(CircleShape)
                                    .background(progressColor)
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Core Step Card
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    when (step) {
                        OnboardingStep.WELCOME -> {
                            WelcomeStepView(
                                onContinue = { currentStep = OnboardingStep.USER_NAME }
                            )
                        }
                        OnboardingStep.USER_NAME -> {
                            UserNameStepView(
                                name = userName,
                                onNameChanged = { userName = it },
                                onContinue = {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    currentStep = OnboardingStep.NATIVE_LANG
                                }
                            )
                        }
                        OnboardingStep.NATIVE_LANG -> {
                            LanguagePickerStepView(
                                heading = "Which language do you speak?",
                                subtitle = "This will be used for translations.",
                                languages = availableLanguages,
                                selectedLanguage = nativeLangObj,
                                onLanguageSelected = {
                                    nativeLangObj = it
                                    nativeLang = it.name
                                },
                                onContinue = {
                                    currentStep = OnboardingStep.TARGET_LANG
                                },
                                summaryLabel = "Native Language"
                            )
                        }
                        OnboardingStep.TARGET_LANG -> {
                            LanguagePickerStepView(
                                heading = "Which language would you like to learn?",
                                subtitle = "We'll create stories and lessons for this language.",
                                languages = availableLanguages.filter { it.code != nativeLangObj?.code },
                                selectedLanguage = targetLangObj,
                                onLanguageSelected = {
                                    targetLangObj = it
                                    targetLang = it.name
                                },
                                onContinue = {
                                    currentStep = OnboardingStep.JOURNEY_PREVIEW
                                },
                                summaryLabel = "Target Language",
                                isTargetLanguageStep = true,
                                nativeLangObj = nativeLangObj
                            )
                        }
                        OnboardingStep.JOURNEY_PREVIEW -> {
                            JourneyPreviewStepView(
                                userName = userName,
                                targetLang = targetLang,
                                onContinue = { currentStep = OnboardingStep.GOAL_SELECTION }
                            )
                        }
                        OnboardingStep.GOAL_SELECTION -> {
                            GoalSelectionStepView(
                                recommendedValue = 10,
                                selectedValue = dailyGoalSentences,
                                onGoalSelected = {
                                    dailyGoalSentences = it
                                    dailyGoalMinutes = when (it) {
                                        5 -> 5
                                        10 -> 10
                                        20 -> 20
                                        else -> 10
                                    }
                                },
                                onContinue = { currentStep = OnboardingStep.FINAL_WELCOME }
                            )
                        }
                        OnboardingStep.FINAL_WELCOME -> {
                            FinalWelcomeStepView(
                                userName = userName,
                                targetLanguage = targetLang,
                                goalSentences = dailyGoalSentences,
                                onStartReading = {
                                    // Save state completely & update user profile records
                                    val difficulty = when (dailyGoalSentences) {
                                        5 -> "Beginner"
                                        10 -> "Intermediate"
                                        20 -> "Advanced"
                                        else -> "Intermediate"
                                    }
                                    viewModel.completeOnboarding(
                                        name = userName,
                                        nativeLang = nativeLang,
                                        targetLang = targetLang,
                                        goalMultiplier = dailyGoalSentences,
                                        difficulty = difficulty
                                    )
                                    onFinished()
                                }
                            )
                        }
                    }
                }

                // Optional Bottom Navigation Controller back key helper
                if (currentStep != OnboardingStep.WELCOME &&
                    currentStep != OnboardingStep.FINAL_WELCOME
                ) {
                    TextButton(
                        onClick = {
                            val previousStep = OnboardingStep.values()[currentStep.ordinal - 1]
                            currentStep = previousStep
                        },
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Go Back",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun WelcomeStepView(
    onContinue: () -> Unit
) {
    var animateStart by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        animateStart = true
    }

    val titleAlpha by animateFloatAsState(
        targetValue = if (animateStart) 1f else 0f,
        animationSpec = tween(1200, easing = EaseOutCubic),
        label = "TitleAlpha"
    )
    val titleSlideY by animateDpAsState(
        targetValue = if (animateStart) 0.dp else 40.dp,
        animationSpec = tween(1200, easing = EaseOutCubic),
        label = "TitleSlide"
    )
    val buttonScale by animateFloatAsState(
        targetValue = if (animateStart) 1f else 0.8f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "ButtonScale"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Column(
            modifier = Modifier
                .graphicsLayer(
                    alpha = titleAlpha,
                    translationY = titleSlideY.value
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Elegant modern icon representation
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Welcome.",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp,
                    fontSize = 42.sp,
                    lineHeight = 48.sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Let's build your reading journey.",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 22.sp
                ),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "We'll personalize stories, translations,\nand vocabulary just for you.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Moving abstract beautiful canvas decoration
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .alpha(titleAlpha),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val waveBrush = Brush.linearGradient(
                    colors = listOf(Color(0xFFC5B59E), Color(0xFFEAD8C3), Color(0xFFFAF9F6).copy(alpha = 0f)),
                    start = Offset(0f, size.height / 2),
                    end = Offset(size.width, size.height / 2)
                )
                // Ambient fluid gradient blobs
                drawCircle(
                    brush = waveBrush,
                    radius = 90.dp.toPx(),
                    center = Offset(size.width / 2f, size.height / 2f)
                )
            }
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.60f),
                modifier = Modifier
                    .size(64.dp)
                    .scale(1.2f)
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .scale(buttonScale),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            Text(
                text = "Get Started",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
fun UserNameStepView(
    name: String,
    onNameChanged: (String) -> Unit,
    onContinue: () -> Unit
) {
    val showGreeting = name.trim().isNotEmpty()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "What should we call you?",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Spacer(modifier = Modifier.height(28.dp))

        // Premium elegant Glassmorphic text field block
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            border = BorderStroke(
                width = 1.5.dp,
                color = if (name.isEmpty()) {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                } else {
                    MaterialTheme.colorScheme.primary
                }
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 18.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.CenterStart
            ) {
                if (name.isEmpty()) {
                    Text(
                        text = "Your name",
                        style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    )
                }

                BasicTextField(
                    value = name,
                    onValueChange = onNameChanged,
                    textStyle = MaterialTheme.typography.titleMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (name.trim().isNotEmpty()) {
                                onContinue()
                            }
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Live Dynamic Greeting
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            AnimatedVisibility(
                visible = showGreeting,
                enter = fadeIn() + slideInVertically(initialOffsetY = { 20 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { 20 })
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Hello, $name 👋",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onContinue,
            enabled = name.trim().isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            )
        ) {
            Text(
                text = "Continue",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
fun LanguagePickerStepView(
    heading: String,
    subtitle: String,
    languages: List<OnboardingLanguage>,
    selectedLanguage: OnboardingLanguage?,
    onLanguageSelected: (OnboardingLanguage) -> Unit,
    onContinue: () -> Unit,
    summaryLabel: String,
    isTargetLanguageStep: Boolean = false,
    nativeLangObj: OnboardingLanguage? = null
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredLanguages = remember(searchQuery, languages) {
        if (searchQuery.isEmpty()) {
            languages
        } else {
            languages.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = heading,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
        )

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search languages...") },
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        )

        // Horizontal Grid or List of Options
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredLanguages) { lang ->
                    val isSelected = selectedLanguage?.code == lang.code
                    
                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) 1.02f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                        label = "LangScale"
                    )

                    Surface(
                        onClick = { onLanguageSelected(lang) },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                        },
                        border = BorderStroke(
                            width = 1.5.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .scale(scale)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = lang.flag,
                                fontSize = 28.sp,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                            Text(
                                text = lang.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Target language dynamic preview card
        if (isTargetLanguageStep && selectedLanguage != null && nativeLangObj != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Translate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${selectedLanguage.name} → ${nativeLangObj.name} Live Exposure",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = selectedLanguage.sampleGreeting,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = selectedLanguage.sampleTranslation,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
        } else if (selectedLanguage != null) {
            // Native Language quick summary bar
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$summaryLabel: ",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${selectedLanguage.flag} ${selectedLanguage.name}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Button(
            onClick = onContinue,
            enabled = selectedLanguage != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            )
        ) {
            Text(
                text = "Continue",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
fun JourneyPreviewStepView(
    userName: String,
    targetLang: String,
    onContinue: () -> Unit
) {
    var animateCards by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        animateCards = true
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Perfect, $userName.",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Black,
                fontSize = 32.sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "You're going to learn $targetLang\nthrough stories, context,\nand daily reading.",
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 18.sp,
                lineHeight = 24.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
        )

        // Custom Staggered Animation statistics preview boxes
        val items = listOf(
            Triple("Books & Stories", "📚 Hand-curated contextual fables for exposure.", Color(0xFFFFF9C4)),
            Triple("Dynamic Vocabulary", "🧠 Interactive tap definition & spaced memory.", Color(0xFFE8EAF6)),
            Triple("Continuous Streaks", "🔥 Secure with Streak protection Shields.", Color(0xFFFFF3E0)),
            Triple("Adaptive Statistics", "📈 Beautiful contribution grids & milestones.", Color(0xFFE8F5E9))
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items.forEachIndexed { index, (title, desc, color) ->
                val delayTime = index * 180
                var visible by remember { mutableStateOf(false) }
                
                LaunchedEffect(animateCards) {
                    delay(delayTime.toLong())
                    visible = true
                }

                AnimatedVisibility(
                    visible = visible,
                    enter = slideInVertically(initialOffsetY = { 40 }) + fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(color),
                                contentAlignment = Alignment.Center
                            ) {
                                // Extract emblem icon
                                val baseStr = title.substringBefore(" ")
                                Text(
                                    text = when (baseStr) {
                                        "Books" -> "📚"
                                        "Dynamic" -> "🧠"
                                        "Continuous" -> "🔥"
                                        "Adaptive" -> "📈"
                                        else -> "✨"
                                    },
                                    fontSize = 20.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = "Continue",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
fun GoalSelectionStepView(
    recommendedValue: Int = 10,
    selectedValue: Int,
    onGoalSelected: (Int) -> Unit,
    onContinue: () -> Unit
) {
    val options = listOf(
        Triple(5, "Beginner", "Light commitment to test the waters."),
        Triple(10, "Intermediate (Recommended)", "Build consistent, lasting fluency habits."),
        Triple(20, "Advanced Polyglot", "Deep immersion, high exposure velocity.")
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "How much would you like to read each day?",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                lineHeight = 34.sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Determine the target number of foreign sentence cards you wish to unlock daily.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            options.forEach { (count, tier, bio) ->
                val isSelected = selectedValue == count
                val isRecommended = count == recommendedValue
                
                Surface(
                    onClick = { onGoalSelected(count) },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                    },
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "$count Cards",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "·  ~$count Mins",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$tier: $bio",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        // Recommended accent highlight indicator
                        if (isRecommended && !isSelected) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.padding(start = 4.dp)
                            ) {
                                Text(
                                    text = " POPULAR ",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        } else if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Active Selection",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = "Continue",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
fun FinalWelcomeStepView(
    userName: String,
    targetLanguage: String,
    goalSentences: Int,
    onStartReading: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE8F5E9)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DoneAll,
                    tint = Color(0xFF4CAF50),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Welcome, $userName.",
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Your $targetLanguage reading journey begins today.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                textAlign = TextAlign.Center
            )
        }

        // Display first goal card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "TODAY'S ADAPTIVE GOAL",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Read $goalSentences sentence cards",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Build language mastery through daily reading immersion",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        Button(
            onClick = onStartReading,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
        ) {
            Text(
                text = "Start Reading",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

// Ease animations helper
val EaseOutCubic: androidx.compose.animation.core.Easing = androidx.compose.animation.core.Easing { fraction ->
    val t = fraction - 1
    t * t * t + 1
}

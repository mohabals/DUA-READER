package com.example // ⚠️ Ensure this matches your project's true package location

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast

// --- FIREBASE IMPORTS ADDED SOBERLY HERE ---
import com.google.firebase.FirebaseApp

import com.example.ui.LibraryScreen
import com.example.ui.ReaderScreen
import com.example.ui.SavedScreen
import com.example.ui.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.ReaderViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // 🚀 CRITICAL CORE LAYER: Start Firebase services right on initialization
        FirebaseApp.initializeApp(this)

        setContent {
            val viewModel: ReaderViewModel = viewModel()
            val isDarkModeSetting by viewModel.isDarkMode.collectAsState()
            val appThemeStyle by viewModel.appTheme.collectAsState()
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val darkTheme = when (isDarkModeSetting) {
                null -> systemDark
                true -> true
                false -> false
            }
            MyApplicationTheme(darkTheme = darkTheme, appThemeStyle = appThemeStyle) {
                CompositionLocalProvider(com.example.ui.theme.LocalAppThemeStyle provides appThemeStyle) {
                    com.example.ui.theme.GlassyBackground {
                        MainAppContainer(viewModel)
                    }
                }
            }
        }
    }
}

enum class ReaderScreenType {
    LIBRARY, FEED, SAVED, SETTINGS
}

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainAppContainer(viewModel: ReaderViewModel) {
    val isOnboardingCompleted by viewModel.isOnboardingCompleted.collectAsState()

    if (!isOnboardingCompleted) {
        com.example.ui.PremiumOnboardingScreen(
            viewModel = viewModel,
            onFinished = {},
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    var currentScreen by remember { mutableStateOf(ReaderScreenType.LIBRARY) }
    
    BackHandler(enabled = currentScreen != ReaderScreenType.LIBRARY) {
        currentScreen = ReaderScreenType.LIBRARY
    }

    var isScrolling by remember { mutableStateOf(false) }
    
    var isDockRevealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isDockRevealed = true
    }
    
    val activeStory by viewModel.activeStory.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = if (com.example.ui.theme.LocalAppThemeStyle.current == "glassy") Color.Transparent else MaterialTheme.colorScheme.background,
        bottomBar = {
            if (currentScreen != ReaderScreenType.FEED) {
                AnimatedVisibility(
                    visible = !isScrolling,
                    enter = slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = tween(durationMillis = 250)
                    ) + fadeIn(animationSpec = tween(150)),
                    exit = slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = tween(durationMillis = 250)
                    ) + fadeOut(animationSpec = tween(150))
                ) {
                    val isDark = MaterialTheme.colorScheme.onBackground == Color(0xFFFCFAF7)
                    
                    val entranceYOffset by animateDpAsState(
                        targetValue = if (isDockRevealed) 0.dp else 24.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "DockEntranceY"
                    )
                    val entranceScale by animateFloatAsState(
                        targetValue = if (isDockRevealed) 1f else 0.94f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "DockEntranceScale"
                    )
                    val entranceAlpha by animateFloatAsState(
                        targetValue = if (isDockRevealed) 1f else 0f,
                        animationSpec = tween(650),
                        label = "DockEntranceAlpha"
                    )

                    Box(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(start = 24.dp, end = 24.dp, bottom = 14.dp)
                            .fillMaxWidth()
                            .height(100.dp)
                            .graphicsLayer {
                                translationY = entranceYOffset.toPx()
                                scaleX = entranceScale
                                scaleY = entranceScale
                                alpha = entranceAlpha
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            GlassyNavItem(
                                selected = currentScreen == ReaderScreenType.LIBRARY,
                                onClick = { currentScreen = ReaderScreenType.LIBRARY },
                                icon = {
                                    Icon(
                                        imageVector = if (currentScreen == ReaderScreenType.LIBRARY) Icons.Filled.Home else Icons.Outlined.Home,
                                        contentDescription = "Home",
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                label = "Home"
                            )

                            GlassyNavItem(
                                selected = currentScreen == ReaderScreenType.FEED,
                                onClick = { currentScreen = ReaderScreenType.FEED },
                                icon = {
                                    Icon(
                                        imageVector = if (currentScreen == ReaderScreenType.FEED) Icons.Filled.MenuBook else Icons.Outlined.MenuBook,
                                        contentDescription = "Reading Feed",
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                label = "Active Read"
                            )

                            GlassyNavItem(
                                selected = currentScreen == ReaderScreenType.SAVED,
                                onClick = { currentScreen = ReaderScreenType.SAVED },
                                icon = {
                                    Icon(
                                        imageVector = if (currentScreen == ReaderScreenType.SAVED) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                        contentDescription = "Saved Reviews",
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                label = "Reviews"
                            )

                            GlassyNavItem(
                                selected = currentScreen == ReaderScreenType.SETTINGS,
                                onClick = { currentScreen = ReaderScreenType.SETTINGS },
                                icon = {
                                    Icon(
                                        imageVector = if (currentScreen == ReaderScreenType.SETTINGS) Icons.Filled.Settings else Icons.Outlined.Settings,
                                        contentDescription = "Settings",
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                label = "Settings"
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
                .background(if (com.example.ui.theme.LocalAppThemeStyle.current == "glassy") Color.Transparent else MaterialTheme.colorScheme.background)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 18.dp, bottom = 8.dp, start = if (currentScreen == ReaderScreenType.FEED) 12.dp else 24.dp, end = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentScreen == ReaderScreenType.FEED) {
                    IconButton(
                        onClick = { currentScreen = ReaderScreenType.LIBRARY },
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Go Home",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
                Column {
                    Text(
                        text = when (currentScreen) {
                            ReaderScreenType.LIBRARY -> "MY STORIES HOME"
                            ReaderScreenType.FEED -> activeStory?.let { "READING: ${it.title.uppercase()}" } ?: "BILINGUAL STORY FEED"
                            ReaderScreenType.SAVED -> "STARRED VOCABULARY REVIEWS"
                            ReaderScreenType.SETTINGS -> "APPLICATION CONTROL PANEL"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.8.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = when (currentScreen) {
                            ReaderScreenType.LIBRARY -> "Collections Deck"
                            ReaderScreenType.FEED -> activeStory?.title ?: "No story selected"
                            ReaderScreenType.SAVED -> "Saved Reads"
                            ReaderScreenType.SETTINGS -> "System Settings"
                        },
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    val direction = targetState.ordinal.compareTo(initialState.ordinal)
                    if (direction > 0) {
                        slideInHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn() with
                                slideOutHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { -it } + fadeOut()
                    } else {
                        slideInHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { -it } + fadeIn() with
                                slideOutHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { it } + fadeOut()
                    }
                },
                modifier = Modifier.weight(1f)
            ) { screen ->
                when (screen) {
                    ReaderScreenType.LIBRARY -> LibraryScreen(viewModel = viewModel, onNavigateToFeed = { currentScreen = ReaderScreenType.FEED }, modifier = Modifier.fillMaxSize())
                    ReaderScreenType.FEED -> ReaderScreen(viewModel = viewModel, onScrollStateChanged = { isScrolling = it }, onNavigateToLibrary = { currentScreen = ReaderScreenType.LIBRARY }, modifier = Modifier.fillMaxSize())
                    ReaderScreenType.SAVED -> SavedScreen(viewModel = viewModel, onScrollStateChanged = { isScrolling = it }, modifier = Modifier.fillMaxSize())
                    ReaderScreenType.SETTINGS -> SettingsScreen(viewModel = viewModel, modifier = Modifier.fillMaxSize())
                }
            }
        }

        // --- Gemini AI Explanation Bottom Sheet overlay ---
        val aiExplanation by viewModel.aiExplanationState.collectAsState()
        if (aiExplanation !is ReaderViewModel.AiExplanationState.Idle) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val context = LocalContext.current

            ModalBottomSheet(
                onDismissRequest = { viewModel.dismissAiExplanation() },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 40.dp)
                ) {
                    // Title Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Gemini Analysis",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Gemini Sentence Analysis",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        IconButton(onClick = { viewModel.dismissAiExplanation() }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(16.dp))

                    when (val state = aiExplanation) {
                        is ReaderViewModel.AiExplanationState.Loading -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    text = "Gemini is translating and explaining...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        is ReaderViewModel.AiExplanationState.Success -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = "Sentence:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = state.sentence,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = "Analysis & Explanation:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = state.explanation,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                            val clip = android.content.ClipData.newPlainText("Sentence Explanation", state.explanation)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "Copied explanation to clipboard!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Copy Explanation")
                                    }

                                    OutlinedButton(
                                        onClick = { viewModel.dismissAiExplanation() },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Close")
                                    }
                                }
                            }
                        }
                        is ReaderViewModel.AiExplanationState.Error -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = "Error",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = "Failed to load explanation",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { viewModel.dismissAiExplanation() },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Dismiss")
                                }
                            }
                        }
                        else -> {}
                    }
                }
            }
        }

        // --- Celebration & Shield Protection Popups Removed ---
    }
}

@Composable
fun RowScope.GlassyNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: String
) {
    val isDark = MaterialTheme.colorScheme.onBackground == Color(0xFFFCFAF7)
    val liftOffset by animateDpAsState(targetValue = if (selected) (-10).dp else 0.dp, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow), label = "NavItemLift")
    val scale by animateFloatAsState(targetValue = if (selected) 1.18f else 1.0f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow), label = "DockIconScale")
    val pocketShadowElevation by animateDpAsState(targetValue = if (selected) 6.dp else 1.5.dp, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow), label = "PocketShadow")

    val contentColor = if (isDark) {
        if (selected) Color(0xFF00FFCC) else Color.White.copy(alpha = 0.65f)
    } else {
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.60f)
    }

    val pocketColor = if (isDark) {
        Color(255, 255, 255).copy(alpha = if (selected) 0.22f else 0.12f)
    } else {
        Color(255, 255, 255).copy(alpha = if (selected) 0.75f else 0.55f)
    }

    val pocketBlurEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        android.graphics.RenderEffect.createBlurEffect(15f, 15f, android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect()
    } else null

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(
                onClick = onClick,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationY = liftOffset.toPx()
                    scaleX = scale
                    scaleY = scale
                }
                .size(52.dp)
                .shadow(
                    elevation = pocketShadowElevation,
                    shape = RoundedCornerShape(22.dp),
                    clip = false,
                    ambientColor = if (isDark) Color(0xFF00FFCC).copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f),
                    spotColor = if (isDark) Color(0xFFFF007F).copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.10f)
                )
                .drawBehind {
                    drawRoundRect(color = pocketColor, cornerRadius = androidx.compose.ui.geometry.CornerRadius(22.dp.toPx()))
                }
                .border(width = 1.dp, color = if (selected) Color.White.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.35f), shape = RoundedCornerShape(22.dp))
                .clip(RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (pocketBlurEffect != null) {
                Box(modifier = Modifier.fillMaxSize().graphicsLayer { renderEffect = pocketBlurEffect })
            }
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                icon()
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        AnimatedVisibility(
            visible = selected,
            enter = scaleIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(if (isDark) Color(0xFF00FFCC) else MaterialTheme.colorScheme.primary))
        }
        
        if (!selected) {
            Spacer(modifier = Modifier.height(5.dp))
        }
    }
}
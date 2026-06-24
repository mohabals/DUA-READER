package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.*
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlinx.coroutines.launch
import com.example.viewmodel.ReaderViewModel
import com.example.model.Story
import com.example.model.StoryGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items

@Composable
fun SavedScreen(
    viewModel: ReaderViewModel,
    onScrollStateChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val savedSentences by viewModel.savedSentences.collectAsState()
    val fontSizeScale by viewModel.fontSizeScale.collectAsState()
    val allStories by viewModel.allStories.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val showTranslationAlways by viewModel.showTranslationAlways.collectAsState()
    val context = LocalContext.current

    // Navigation back press handler
    var selectedStoryForReview by remember { mutableStateOf<Story?>(null) }

    BackHandler(enabled = selectedStoryForReview != null) {
        selectedStoryForReview = null
    }

    // Filtered sentences belonging specifically to the chosen story for review
    val storySentences = remember(savedSentences, selectedStoryForReview) {
        savedSentences.filter { it.storyId == selectedStoryForReview?.id }
    }

    // Auto-return to categories index list if there are no cards left for this story
    LaunchedEffect(storySentences) {
        if (selectedStoryForReview != null && storySentences.isEmpty()) {
            selectedStoryForReview = null
        }
    }

    // Send visual rest confirmation
    LaunchedEffect(Unit) {
        onScrollStateChanged(false)
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (savedSentences.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.StarBorder,
                        contentDescription = "No Saved Sentences",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Saved reviews is empty",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Your starred cards will appear here. While reading stories, tap a sentence to reveal its translation and press the star icon to save it for review.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 300.dp)
                    )
                }
            }
        } else {
            if (selectedStoryForReview == null) {
                // Category Deck / Categories View
                val savedStories = remember(savedSentences, allStories) {
                    val savedStoryIds = savedSentences.map { it.storyId }.toSet()
                    allStories.filter { it.id in savedStoryIds }
                }
                
                val savedGroups = remember(savedStories, groups) {
                    val savedGroupIds = savedStories.map { it.groupId }.toSet()
                    groups.filter { it.id in savedGroupIds }
                }

                var expandedGroupId by remember { mutableStateOf<Int?>(null) }
                val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                val coroutineScope = rememberCoroutineScope()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    Text(
                        text = "Select a category category below to review. Tap a category card to see its story collection. Tap any story inside to begin.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .widthIn(max = 340.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    androidx.compose.foundation.lazy.LazyRow(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        itemsIndexed(
                            items = savedGroups,
                            key = { _, group -> group.id }
                        ) { index, group ->
                            val isExpanded = expandedGroupId == group.id
                            val groupStories = savedStories.filter { it.groupId == group.id }
                            val gradientColors = getCollectionGradient(group.id)
                            
                            val animatedCardHeight by animateDpAsState(
                                targetValue = if (isExpanded) 380.dp else 174.dp,
                                animationSpec = spring<androidx.compose.ui.unit.Dp>(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                ),
                                label = "ReviewCardExpansion"
                            )
                            
                            Card(
                                modifier = Modifier
                                    .width(300.dp)
                                    .height(animatedCardHeight)
                                    .shadow(
                                        elevation = if (isExpanded) 12.dp else 4.dp,
                                        shape = RoundedCornerShape(28.dp)
                                    )
                                    .clip(RoundedCornerShape(28.dp))
                                    .clickable {
                                        expandedGroupId = if (isExpanded) null else group.id
                                        coroutineScope.launch {
                                            listState.animateScrollToItem(index)
                                        }
                                    },
                                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(brush = Brush.linearGradient(colors = gradientColors))
                                        .padding(20.dp)
                                ) {
                                    // Background Watermark Index
                                    Text(
                                        text = "%02d".format(index + 1),
                                        style = MaterialTheme.typography.displayMedium.copy(
                                            fontWeight = FontWeight.Black,
                                            fontSize = 76.sp,
                                            color = Color.White.copy(alpha = 0.11f)
                                        ),
                                        modifier = Modifier.align(Alignment.TopEnd)
                                    )
                                    
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(
                                                text = "REVIEW CATEGORY",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    letterSpacing = 1.5.sp,
                                                    color = Color.White.copy(alpha = 0.6f)
                                                )
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = group.name,
                                                style = MaterialTheme.typography.titleLarge.copy(
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Surface(
                                                color = Color.White.copy(alpha = 0.18f),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = "  ${groupStories.size} stories to review  ",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                        
                                        if (isExpanded) {
                                            androidx.compose.foundation.lazy.LazyColumn(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(vertical = 12.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                items(groupStories) { story ->
                                                    val storySavedCount = savedSentences.count { it.storyId == story.id }
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(RoundedCornerShape(12.dp))
                                                            .background(Color.White.copy(alpha = 0.08f))
                                                            .clickable {
                                                                selectedStoryForReview = story
                                                            }
                                                            .padding(10.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.MenuBook,
                                                            contentDescription = null,
                                                            tint = Color.White,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                        Text(
                                                            text = story.title,
                                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                                            color = Color.White,
                                                            maxLines = 1,
                                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        Surface(
                                                            color = Color.White.copy(alpha = 0.15f),
                                                            shape = RoundedCornerShape(6.dp)
                                                        ) {
                                                            Text(
                                                                text = " $storySavedCount ",
                                                                style = MaterialTheme.typography.labelSmall.copy(
                                                                    color = Color.White,
                                                                    fontWeight = FontWeight.Bold
                                                                ),
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        
                                        // Quick footer expand box
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(Color.White.copy(alpha = 0.25f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                    contentDescription = "Expand Review List",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // High-fidelity standard vertical feed for reviewing saved sentences
                val reviewListState = androidx.compose.foundation.lazy.rememberLazyListState()
                val isScrolling = reviewListState.isScrollInProgress
                LaunchedEffect(isScrolling) {
                    onScrollStateChanged(isScrolling)
                }

                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    // Back and Header Bar for Active review story
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { selectedStoryForReview = null },
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back to categories",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Column {
                            Text(
                                text = "REVIEWING STORY",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.6.sp
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = selectedStoryForReview?.title ?: "",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }

                    androidx.compose.foundation.lazy.LazyColumn(
                        state = reviewListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        itemsIndexed(
                            items = storySentences,
                            key = { _, item -> item.id }
                        ) { index, sentence ->
                            SentenceItemView(
                                sentence = sentence,
                                index = index,
                                isStoppedHere = false,
                                onMarkStopped = {},
                                onToggleSaved = { viewModel.toggleSavedDirect(sentence) },
                                onCopy = { message ->
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                },
                                fontSizeScale = fontSizeScale,
                                alwaysShowTranslation = showTranslationAlways,
                                onAnalyze = {
                                    com.example.api.GeminiClient.launchOnDeviceAnalysis(context, sentence.originalText)
                                },
                                onRevealTranslation = {
                                    viewModel.logCardReviewed()
                                },
                                onSpeak = { txt ->
                                    viewModel.speakText(txt)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun getCollectionGradient(groupId: Int): List<Color> {
    return when (groupId % 5) {
        0 -> listOf(Color(0xFF2E3D30), Color(0xFF4A5D4E)) // Deep Forest Pine
        1 -> listOf(Color(0xFF3B2F2F), Color(0xFF5A4A4A)) // Cozy Warm Walnut
        2 -> listOf(Color(0xFF2B3A4A), Color(0xFF42576C)) // Soft Slate Blue
        3 -> listOf(Color(0xFF4A3E3D), Color(0xFF6B5B5A)) // Earthy Terracotta/Mocha
        else -> listOf(Color(0xFF2E2C33), Color(0xFF4A4652)) // Rich Mysterious Obsidian/Grape
    }
}

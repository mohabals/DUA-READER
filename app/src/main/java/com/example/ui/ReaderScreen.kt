package com.example.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
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
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.example.viewmodel.ReaderViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    onScrollStateChanged: (Boolean) -> Unit,
    onNavigateToLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sentences by viewModel.sentences.collectAsState()
    val activeStory by viewModel.activeStory.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    val grammarColorViewModel: com.example.viewmodel.GrammarColorViewModel = viewModel()
    val pronunciationCoachViewModel: com.example.viewmodel.PronunciationCoachViewModel = viewModel()

    val expWordFocus by viewModel.expWordFocus.collectAsState()
    val expGrammarColor by viewModel.expGrammarColor.collectAsState()
    val expKaraokeTts by viewModel.expKaraokeTts.collectAsState()
    val expPronunciationCoach by viewModel.expPronunciationCoach.collectAsState()

    val currentlySpokenText by viewModel.currentlySpokenText.collectAsState()
    val highlightRange by viewModel.wordHighlightManager.highlightRange.collectAsState()

    val grammarTagsMap by grammarColorViewModel.sentenceTags.collectAsState()

    val isRecording by pronunciationCoachViewModel.isRecording.collectAsState()
    val recordingSentenceId by pronunciationCoachViewModel.recordingSentenceId.collectAsState()
    val recordingDuration by pronunciationCoachViewModel.recordingDuration.collectAsState()
    val feedbackMap by pronunciationCoachViewModel.feedbackMap.collectAsState()

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "تم تفعيل إذن الميكروفون! يمكنك الآن البدء بتسجيل صوتك لقراءته.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "إذن الميكروفون مطلوب لتشغيل مدرب النطق الصوتي.", Toast.LENGTH_LONG).show()
        }
    }

    if (expGrammarColor) {
        LaunchedEffect(sentences) {
            for (sentence in sentences) {
                grammarColorViewModel.requestGrammarAnalysis(sentence.originalText)
            }
        }
    }

    val fontSizeScale by viewModel.fontSizeScale.collectAsState()
    val showTranslationAlways by viewModel.showTranslationAlways.collectAsState()

    var showImportDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    // Emit scroll updates to notify parent container to slide-hide/show floating navigation dock
    val isScrolling = listState.isScrollInProgress
    LaunchedEffect(isScrolling) {
        onScrollStateChanged(isScrolling)
    }

    // Auto-scroll to bookmarked position if available when story is selected
    LaunchedEffect(activeStory?.id, sentences.size) {
        activeStory?.let { story ->
            if (story.lastReadIndex > 0 && sentences.isNotEmpty()) {
                delay(300)
                if (story.lastReadIndex < sentences.size) {
                    listState.scrollToItem(story.lastReadIndex)
                }
            }
        }
    }

    // Listen to parsing results to show feedback toasts
    LaunchedEffect(key1 = viewModel) {
        viewModel.importResult.collect { result ->
            when (result) {
                is ReaderViewModel.ImportResult.Success -> {
                    Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                }
                is ReaderViewModel.ImportResult.Error -> {
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (activeStory == null) {
                    // If no active story is selected, explain beautifully and link to the Library
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LibraryBooks,
                                contentDescription = "Go to Library",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(80.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No story selected",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Go to the Library screen to select a story or create a group category to manage your learning materials.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.widthIn(max = 300.dp)
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(onClick = onNavigateToLibrary) {
                                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Open Library Tab")
                            }
                        }
                    }
                } else if (sentences.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Book,
                            contentDescription = "Empty Story Reader",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(80.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = activeStory?.title ?: "Empty Story",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "This story has no sentences imported yet. Type/paste aligned paragraphs, or load a text file directly mapping original to translations.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(max = 300.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { showImportDialog = true }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Import Alignments")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (expKaraokeTts) {
                        item {
                            val karaokeIsPlaying by viewModel.karaokeTtsManager.isPlaying.collectAsState()
                            val activeKaraokeIndex by viewModel.karaokeTtsManager.currentSentenceIndex.collectAsState()

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.QueueMusic,
                                            contentDescription = "Karaoke Icon",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Column {
                                            Text(
                                                text = "Bilingual Karaoke Mode",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                            if (karaokeIsPlaying && activeKaraokeIndex != -1) {
                                                Text(
                                                    text = "Playing sentence ${activeKaraokeIndex + 1} of ${sentences.size}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            } else {
                                                Text(
                                                    text = "Reads Russian then Arabic, alternating",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                                )
                                            }
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            if (karaokeIsPlaying) {
                                                viewModel.stopKaraoke()
                                            } else {
                                                viewModel.playKaraoke(sentences)
                                            }
                                        },
                                        colors = IconButtonDefaults.iconButtonColors(
                                            containerColor = if (karaokeIsPlaying) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                            contentColor = if (karaokeIsPlaying) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
                                        )
                                    ) {
                                        Icon(
                                            imageVector = if (karaokeIsPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                            contentDescription = if (karaokeIsPlaying) "Stop Karaoke" else "Start Karaoke"
                                        )
                                    }
                                }
                            }
                        }
                    }

                    itemsIndexed(
                        items = sentences,
                        key = { _, item -> item.id }
                    ) { index, sentence ->
                        val isStoppedHere = activeStory?.lastReadIndex == index
                        SentenceItemView(
                            sentence = sentence,
                            index = index,
                            isStoppedHere = isStoppedHere,
                            onMarkStopped = {
                                activeStory?.let { story ->
                                    viewModel.updateStoryProgress(story.id, index)
                                }
                            },
                            onToggleSaved = { viewModel.toggleSavedDirect(sentence) },
                            onCopy = { message ->
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.centerFocusEffect(index, listState),
                            fontSizeScale = fontSizeScale,
                            alwaysShowTranslation = showTranslationAlways,
                            onAnalyze = {
                                com.example.api.GeminiClient.launchOnDeviceAnalysis(context, sentence.originalText)
                            },
                            onRevealTranslation = {
                                viewModel.logSentenceRead(sentence.id)
                            },
                            onSpeak = { txt ->
                                viewModel.speakText(txt)
                            },
                            highlightRange = if (currentlySpokenText == sentence.originalText) highlightRange else null,
                            grammarTags = grammarTagsMap[sentence.originalText],
                            isWordFocusEnabled = expWordFocus,
                            isGrammarEnabled = expGrammarColor,
                            isPronunciationCoachEnabled = expPronunciationCoach,
                            isRecordingThis = isRecording && recordingSentenceId == sentence.id,
                            recordingDurationSec = recordingDuration,
                            onMicTap = {
                                if (isRecording && recordingSentenceId == sentence.id) {
                                    pronunciationCoachViewModel.stopRecording(sentence.originalText)
                                } else {
                                    val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                        context,
                                        android.Manifest.permission.RECORD_AUDIO
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                                    if (hasPermission) {
                                        pronunciationCoachViewModel.startRecording(context, sentence.id, sentence.originalText)
                                    } else {
                                        recordAudioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                            feedbackResult = feedbackMap[sentence.id],
                            onClearFeedback = {
                                pronunciationCoachViewModel.clearFeedback(sentence.id)
                            }
                        )
                    }
                }
            }
        }


    }

    // Custom Import Overlay Sheet/Dialog supporting both text paste and `.txt` file selector
    if (showImportDialog) {
        Dialog(onDismissRequest = { showImportDialog = false }) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .widthIn(max = 450.dp)
            ) {
                var textInput by remember { mutableStateOf("") }
                var appendInsteadOfReplace by remember { mutableStateOf(false) }

                // Text File Picker Launcher
                val filePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri ->
                    uri?.let {
                        val contentResolver = context.contentResolver
                        try {
                            contentResolver.openInputStream(uri)?.use { inputStream ->
                                val txt = inputStream.bufferedReader().use { reader -> reader.readText() }
                                textInput = txt
                                Toast.makeText(context, "Bilingual file parsed successfully!", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "File load failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    }
                }

                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "Import to: ${activeStory?.title}",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "Instructions: Input sentences. Each line represents one translation pair divided by the '|' pipe character.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))

                    // Formatting Helper Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "Original sentence | Translation\nЯ живу в Москве. | I live in Moscow.",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        placeholder = { Text("Paste bilingual alignments here, or import a text file below...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        shape = MaterialTheme.shapes.medium,
                        textStyle = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Text File Attachment Selector Panel
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable {
                                filePickerLauncher.launch("text/plain")
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Attachment,
                            contentDescription = "Attach File",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Load from a (.txt) file",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Append vs Replace
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = appendInsteadOfReplace,
                            onCheckedChange = { appendInsteadOfReplace = it },
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "Append to current story",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Add to existing lines instead of overwriting",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showImportDialog = false }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                viewModel.importText(textInput, append = appendInsteadOfReplace)
                                showImportDialog = false
                            }
                        ) {
                            Text("Import")
                        }
                    }
                }
            }
        }
    }

    // Delete All Confirmation Dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Current Story?") },
            text = { Text("Are you sure you want to permanently clear all sentences for '${activeStory?.title}'? Saved starred reviews inside this story will be deleted.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearActiveStorySentences()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val showVoiceInstall by viewModel.karaokeTtsManager.showVoiceInstallDialog.collectAsState()
    if (showVoiceInstall != null) {
        val lang = if (showVoiceInstall == "ru") "Russian" else "Arabic"
        AlertDialog(
            onDismissRequest = { viewModel.karaokeTtsManager.dismissVoiceDialog() },
            title = { Text("$lang TTS Voice Required") },
            text = { Text("To play bilingual text in $lang, you need to make sure the $lang TTS voice data is installed on your device. Go to Settings -> Accessibility -> Text-to-Speech to configure it.") },
            confirmButton = {
                TextButton(onClick = { viewModel.karaokeTtsManager.dismissVoiceDialog() }) {
                    Text("OK")
                }
            }
        )
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

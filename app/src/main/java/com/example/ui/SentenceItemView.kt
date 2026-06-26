package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import com.example.model.SentenceCard
import com.example.model.WordTag
import com.example.viewmodel.PronunciationCoachViewModel

@Composable
fun buildBilingualAnnotatedString(
    text: String,
    highlightRange: IntRange?,
    grammarTags: List<WordTag>?,
    isHighlightEnabled: Boolean,
    isGrammarEnabled: Boolean
): AnnotatedString {
    val builder = AnnotatedString.Builder(text)

    if (isGrammarEnabled && grammarTags != null) {
        for (tag in grammarTags) {
            val start = tag.start
            val end = tag.end
            if (start in 0..text.length && end in 0..text.length && start < end) {
                val color = when (tag.pos.lowercase(java.util.Locale.getDefault())) {
                    "noun" -> Color(0xFF1E88E5)
                    "verb" -> Color(0xFFE53935)
                    "adjective" -> Color(0xFF43A047)
                    "adverb" -> Color(0xFFFB8C00)
                    else -> Color(0xFF7E57C2)
                }
                builder.addStyle(
                    style = SpanStyle(
                        color = color,
                        fontWeight = FontWeight.Bold
                    ),
                    start = start,
                    end = end
                )
            }
        }
    }

    if (isHighlightEnabled && highlightRange != null) {
        val start = highlightRange.first
        val end = highlightRange.last + 1
        if (start in 0..text.length && end in 0..text.length && start < end) {
            builder.addStyle(
                style = SpanStyle(
                    background = Color(0xFFFFEB3B),
                    color = Color.Black,
                    fontWeight = FontWeight.ExtraBold
                ),
                start = start,
                end = end
            )
        }
    }

    return builder.toAnnotatedString()
}

@Composable
fun SentenceItemView(
    sentence: SentenceCard,
    index: Int,
    isStoppedHere: Boolean,
    onMarkStopped: () -> Unit,
    onToggleSaved: () -> Unit,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
    fontSizeScale: Float = 1.0f,
    alwaysShowTranslation: Boolean = false,
    onAnalyze: (() -> Unit)? = null,
    onRevealTranslation: () -> Unit = {},
    onSpeak: ((String) -> Unit)? = null,
    highlightRange: IntRange? = null,
    grammarTags: List<WordTag>? = null,
    isWordFocusEnabled: Boolean = false,
    isGrammarEnabled: Boolean = false,
    isPronunciationCoachEnabled: Boolean = false,
    isRecordingThis: Boolean = false,
    recordingDurationSec: Int = 0,
    onMicTap: (() -> Unit)? = null,
    feedbackResult: PronunciationCoachViewModel.FeedbackResult? = null,
    onClearFeedback: (() -> Unit)? = null
) {
    var isExpanded by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val currentOnReveal = rememberUpdatedState(onRevealTranslation)
    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            currentOnReveal.value()
        }
    }

    val scaleFactor by animateFloatAsState(
        targetValue = if (sentence.isSaved) 1.2f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "star_scale"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 16.dp)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isStoppedHere) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isStoppedHere) 2.dp else 0.dp
        )
    ) {
        Column(
            modifier = Modifier
                .clickable { isExpanded = !isExpanded }
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isStoppedHere) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "SENTENCE ${index + 1}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isStoppedHere) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (sentence.isSaved) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFFFF8E1)
                        ) {
                            Text(
                                text = "SAVED",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFB300),
                                    fontSize = 9.sp
                                )
                            )
                        }
                    }

                    if (isStoppedHere) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFE8F5E9)
                        ) {
                            Text(
                                text = "ACTIVE",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4CAF50),
                                    fontSize = 9.sp
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val annotatedOriginal = buildBilingualAnnotatedString(
                text = sentence.originalText,
                highlightRange = highlightRange,
                grammarTags = grammarTags,
                isHighlightEnabled = isWordFocusEnabled,
                isGrammarEnabled = isGrammarEnabled
            )

            Text(
                text = annotatedOriginal,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (20 * fontSizeScale).sp,
                    lineHeight = (28 * fontSizeScale).sp,
                    textAlign = TextAlign.Center
                ),
                color = if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )

            if (alwaysShowTranslation && !isExpanded) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = sentence.translatedText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Normal,
                        fontSize = (15 * fontSizeScale).sp,
                        lineHeight = (20 * fontSizeScale).sp,
                        textAlign = TextAlign.Center
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (isRecordingThis) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FiberManualRecord,
                        contentDescription = "Recording Indicator",
                        tint = Color.Red,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "Recording... ${recordingDurationSec}s",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = sentence.translatedText,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = (18 * fontSizeScale).sp,
                            lineHeight = (26 * fontSizeScale).sp,
                            textAlign = TextAlign.Center
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (onAnalyze != null) {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onAnalyze()
                                },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = "Analyze Sentence with Gemini AI",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                        }

                        if (onSpeak != null) {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSpeak(sentence.originalText)
                                },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = "Play Pronunciation Audio",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                        }

                        if (isPronunciationCoachEnabled && onMicTap != null) {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onMicTap()
                                },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = if (isRecordingThis) Icons.Default.MicOff else Icons.Default.Mic,
                                    contentDescription = "Pronunciation Coach Microphone",
                                    tint = if (isRecordingThis) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                        }

                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                try {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_TEXT, sentence.originalText)
                                        `package` = "com.google.android.apps.translate"
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    val encodedText = java.net.URLEncoder.encode(sentence.originalText, "UTF-8")
                                    val webIntent = android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("https://translate.google.com/?sl=auto&tl=en&text=$encodedText&op=translate")
                                    )
                                    context.startActivity(webIntent)
                                }
                            },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = "Google Translate",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onMarkStopped()
                            },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = if (isStoppedHere) Icons.Default.CheckCircle else Icons.Default.Check,
                                contentDescription = "Mark as stopped here",
                                tint = if (isStoppedHere) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val textToCopy = "${sentence.originalText}\n${sentence.translatedText}"
                                clipboardManager.setText(AnnotatedString(textToCopy))
                                onCopy("Copied sentence to clipboard!")
                            },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = "Copy sentence",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onToggleSaved()
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .scale(scaleFactor)
                        ) {
                            Icon(
                                imageVector = if (sentence.isSaved) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = "Save sentence",
                                tint = if (sentence.isSaved) {
                                    Color(0xFFFFB300)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                },
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            if (feedbackResult != null) {
                Spacer(modifier = Modifier.height(16.dp))
                val cardColor = when (feedbackResult.color) {
                    PronunciationCoachViewModel.FeedbackColor.GREEN -> Color(0xFFE8F5E9)
                    PronunciationCoachViewModel.FeedbackColor.YELLOW -> Color(0xFFFFFDE7)
                    PronunciationCoachViewModel.FeedbackColor.RED -> Color(0xFFFFEBEE)
                }
                val contentColor = when (feedbackResult.color) {
                    PronunciationCoachViewModel.FeedbackColor.GREEN -> Color(0xFF2E7D32)
                    PronunciationCoachViewModel.FeedbackColor.YELLOW -> Color(0xFFF57F17)
                    PronunciationCoachViewModel.FeedbackColor.RED -> Color(0xFFC62828)
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = cardColor
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, contentColor.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RecordVoiceOver,
                                    contentDescription = "Pronunciation Feedback Icon",
                                    tint = contentColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Pronunciation Coach",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = contentColor
                                )
                            }
                            if (onClearFeedback != null) {
                                IconButton(
                                    onClick = onClearFeedback,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear Feedback",
                                        tint = contentColor.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        if (feedbackResult.transcript.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "What we heard: \"${feedbackResult.transcript}\"",
                                style = MaterialTheme.typography.bodySmall.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                                color = contentColor.copy(alpha = 0.8f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = feedbackResult.feedbackText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Black
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Modifier.background(color: Color, shape: androidx.compose.ui.graphics.Shape): Modifier {
    return this.then(Modifier.drawBehind {
        drawRoundRect(
            color = color,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(24f, 24f)
        )
    })
}

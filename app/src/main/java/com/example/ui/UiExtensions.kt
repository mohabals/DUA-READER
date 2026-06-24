package com.example.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.abs

fun Modifier.centerFocusEffect(
    itemIndex: Int,
    listState: LazyListState
): Modifier = this.composed {
    val scaleAndAlpha by remember(itemIndex, listState) {
        derivedStateOf {
            val firstVisibleIndex = listState.firstVisibleItemIndex
            val firstVisibleOffset = listState.firstVisibleItemScrollOffset
            
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            val firstVisibleItem = visibleItems.find { it.index == firstVisibleIndex }
            val firstVisibleSize = firstVisibleItem?.size ?: 1
            
            val fraction = if (firstVisibleSize > 0) {
                (firstVisibleOffset.toFloat() / firstVisibleSize).coerceIn(0f, 1f)
            } else {
                0f
            }
            
            when {
                itemIndex < firstVisibleIndex -> {
                    Pair(0.94f, 0.45f)
                }
                itemIndex == firstVisibleIndex -> {
                    val progress = 1f - fraction
                    val alpha = 0.45f + progress * 0.55f
                    val scale = 0.94f + progress * 0.12f
                    Pair(scale, alpha)
                }
                itemIndex == firstVisibleIndex + 1 -> {
                    val progress = fraction
                    val alpha = 0.45f + progress * 0.55f
                    val scale = 0.94f + progress * 0.12f
                    Pair(scale, alpha)
                }
                else -> {
                    Pair(0.94f, 0.45f)
                }
            }
        }
    }
    
    this.graphicsLayer {
        scaleX = scaleAndAlpha.first
        scaleY = scaleAndAlpha.first
        alpha = scaleAndAlpha.second
    }
}

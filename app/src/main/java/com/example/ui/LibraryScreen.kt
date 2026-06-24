package com.example.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlinx.coroutines.launch
import com.example.model.Story
import com.example.model.StoryGroup
import com.example.viewmodel.ReaderViewModel
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: ReaderViewModel,
    onNavigateToFeed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val groups by viewModel.groups.collectAsState()
    val allStories by viewModel.allStories.collectAsState()
    val activeStory by viewModel.activeStory.collectAsState()
    val context = LocalContext.current

    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showCreateStoryDialog by remember { mutableStateOf<StoryGroup?>(null) }
    var showGTranslateShortcutDialog by remember { mutableStateOf<String?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        if (groups.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Class,
                        contentDescription = "Empty Library",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Your Library is empty",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Create category groups to organize your bilingual text materials, articles, and book chapters. You can start by loading classic samples or imports.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 300.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { showCreateGroupDialog = true }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create Story Group")
                    }
                }
            }
        } else {
            var expandedGroupId by remember { mutableStateOf<Int?>(null) }
            val listState = rememberLazyListState()
            val coroutineScope = rememberCoroutineScope()
            
            var showCategoriesDialog by remember { mutableStateOf(false) }
            var showStoriesDialog by remember { mutableStateOf(false) }
            var showBookmarksDialog by remember { mutableStateOf(false) }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 4.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Top
            ) {
                // 1. Dashboard Stats Tiles Row (Professional Upgrade with functional popups)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DashboardStatTile(
                        label = "Categories",
                        value = "${groups.size}",
                        icon = Icons.Default.FolderOpen,
                        onClick = { showCategoriesDialog = true },
                        modifier = Modifier.weight(1f)
                    )
                    DashboardStatTile(
                        label = "Stories",
                        value = "${allStories.size}",
                        icon = Icons.Default.MenuBook,
                        onClick = { showStoriesDialog = true },
                        modifier = Modifier.weight(1f)
                    )
                    val bookmarkedCount = allStories.count { it.lastReadIndex > 0 }
                    DashboardStatTile(
                        label = "Bookmarks",
                        value = "$bookmarkedCount",
                        icon = Icons.Default.CheckCircle,
                        onClick = { showBookmarksDialog = true },
                        modifier = Modifier.weight(1f)
                    )
                }

                // 2. Control buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { showCreateGroupDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.large,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("New Group", fontSize = 13.sp)
                    }

                    val currentActiveGroup = expandedGroupId?.let { id -> groups.find { it.id == id } } ?: groups.firstOrNull()
                    Button(
                        onClick = { currentActiveGroup?.let { showCreateStoryDialog = it } },
                        enabled = currentActiveGroup != null,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.large,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(Icons.Default.PostAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Story +", fontSize = 13.sp)
                    }
                }

                // Swipable, beautifully organized Horizontal Category Carousel
                LazyRow(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    itemsIndexed(
                        items = groups,
                        key = { _, group -> group.id }
                    ) { index, group ->
                        val isExpanded = expandedGroupId == group.id
                        val groupStories = allStories.filter { it.groupId == group.id }
                        val gradientColors = getCollectionGradient(group.id)
                        
                        // Animated height for the elegant card expansion
                        val animatedCardHeight by animateDpAsState(
                            targetValue = if (isExpanded) 400.dp else 174.dp,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "CardExpansion"
                        )
                        
                        // Card with snapping scale on selection
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
                                    .background(
                                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                            colors = gradientColors
                                        )
                                    )
                                    .padding(20.dp)
                            ) {
                                // Background Index Watermark
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
                                    // Header Info always visible
                                    Column {
                                        Text(
                                            text = "CATEGORY #${index + 1}",
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
                                        
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Surface(
                                                color = Color.White.copy(alpha = 0.18f),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = "  ${groupStories.size} stories  ",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }
                                    
                                    // Expanded content showing stories
                                    if (isExpanded) {
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(vertical = 12.dp)
                                        ) {
                                            HorizontalDivider(color = Color.White.copy(alpha = 0.15f), thickness = 1.dp)
                                            Spacer(modifier = Modifier.height(10.dp))
                                            
                                            if (groupStories.isEmpty()) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .weight(1f),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        Text(
                                                            text = "No stories inside this category",
                                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                            color = Color.White.copy(alpha = 0.8f)
                                                        )
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        Button(
                                                            onClick = { showCreateStoryDialog = group },
                                                            colors = ButtonDefaults.buttonColors(
                                                                containerColor = Color.White.copy(alpha = 0.2f),
                                                                contentColor = Color.White
                                                            ),
                                                            shape = RoundedCornerShape(8.dp),
                                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                            modifier = Modifier.height(32.dp)
                                                        ) {
                                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text("Add Story", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }
                                            } else {
                                                // Scrollable inline story items
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .weight(1f)
                                                        .verticalScroll(rememberScrollState()),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    groupStories.forEachIndexed { storyIdx, story ->
                                                        val isSelected = activeStory?.id == story.id
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clip(RoundedCornerShape(12.dp))
                                                                .background(
                                                                    if (isSelected) Color.White.copy(alpha = 0.22f)
                                                                    else Color.White.copy(alpha = 0.08f)
                                                                )
                                                                .clickable {
                                                                    viewModel.selectStory(story)
                                                                    Toast.makeText(context, "Opening story: ${story.title}", Toast.LENGTH_SHORT).show()
                                                                    onNavigateToFeed()
                                                                }
                                                                .padding(10.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(26.dp)
                                                                    .clip(RoundedCornerShape(6.dp))
                                                                    .background(
                                                                        if (isSelected) Color.White
                                                                        else Color.White.copy(alpha = 0.15f)
                                                                    ),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Text(
                                                                    text = "%02d".format(storyIdx + 1),
                                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = if (isSelected) Color.Black else Color.White
                                                                    )
                                                                )
                                                            }
                                                            
                                                            Spacer(modifier = Modifier.width(10.dp))
                                                            
                                                            Text(
                                                                text = story.title,
                                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                                                color = Color.White,
                                                                maxLines = 1,
                                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                            
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = if (story.lastReadIndex > 0) Icons.Default.Bookmark else Icons.Default.PlayArrow,
                                                                    contentDescription = null,
                                                                    tint = if (story.lastReadIndex > 0) Color(0xFF81C784) else Color.White,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                                IconButton(
                                                                    onClick = { viewModel.deleteStory(story) },
                                                                    modifier = Modifier.size(24.dp)
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Default.Close,
                                                                        contentDescription = "Delete Story",
                                                                        tint = Color.White.copy(alpha = 0.5f),
                                                                        modifier = Modifier.size(14.dp)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    
                                    // Quick footer buttons at card bottom
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            IconButton(
                                                onClick = { showCreateStoryDialog = group },
                                                colors = IconButtonDefaults.iconButtonColors(
                                                    containerColor = Color.White.copy(alpha = 0.14f)
                                                ),
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PostAdd,
                                                    contentDescription = "Quick Add Story Inside",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = { viewModel.deleteGroup(group) },
                                                colors = IconButtonDefaults.iconButtonColors(
                                                    containerColor = Color.White.copy(alpha = 0.08f)
                                                ),
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DeleteOutline,
                                                    contentDescription = "Delete Group",
                                                    tint = Color.White.copy(alpha = 0.8f),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        
                                        // Expand indicator like the round arrow button in Iconic Brazil card!
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color.White.copy(alpha = 0.25f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = "Expand",
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

            Spacer(modifier = Modifier.height(16.dp))

            // Popups / Overlays for each top stat tile when pressed
            if (showCategoriesDialog) {
                Dialog(onDismissRequest = { showCategoriesDialog = false }) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        tonalElevation = 6.dp,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .widthIn(max = 400.dp)
                    ) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("All Categories", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 300.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                itemsIndexed(groups) { idx, grp ->
                                    val count = allStories.count { it.groupId == grp.id }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                            .clickable {
                                                coroutineScope.launch {
                                                    listState.animateScrollToItem(idx)
                                                    expandedGroupId = grp.id
                                                }
                                                showCategoriesDialog = false
                                            }
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(grp.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                            Text("$count stories", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { showCategoriesDialog = false }) {
                                    Text("Close")
                                }
                            }
                        }
                    }
                }
            }

            if (showStoriesDialog) {
                Dialog(onDismissRequest = { showStoriesDialog = false }) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        tonalElevation = 6.dp,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .widthIn(max = 400.dp)
                    ) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Book, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("All Stories", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            if (allStories.isEmpty()) {
                                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                                    Text("No stories in your library yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.heightIn(max = 300.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(allStories) { story ->
                                        val grp = groups.find { it.id == story.groupId }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                                .clickable {
                                                    viewModel.selectStory(story)
                                                    Toast.makeText(context, "Opening story: ${story.title}", Toast.LENGTH_SHORT).show()
                                                    onNavigateToFeed()
                                                    showStoriesDialog = false
                                                }
                                                .padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(story.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                                Text(grp?.name ?: "No Category", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { showStoriesDialog = false }) {
                                    Text("Close")
                                }
                            }
                        }
                    }
                }
            }

            if (showBookmarksDialog) {
                Dialog(onDismissRequest = { showBookmarksDialog = false }) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        tonalElevation = 6.dp,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .widthIn(max = 400.dp)
                    ) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("Saved Bookmarks", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            val bookmarkedStories = allStories.filter { it.lastReadIndex > 0 }
                            if (bookmarkedStories.isEmpty()) {
                                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                                    Text("No active bookmarks. They save when reading!", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.heightIn(max = 300.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(bookmarkedStories) { story ->
                                        val grp = groups.find { it.id == story.groupId }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                                .clickable {
                                                    viewModel.selectStory(story)
                                                    Toast.makeText(context, "Resuming story: ${story.title}", Toast.LENGTH_SHORT).show()
                                                    onNavigateToFeed()
                                                    showBookmarksDialog = false
                                                }
                                                .padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(story.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(grp?.name ?: "No Category", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    Text(" • ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    Text("Card #${story.lastReadIndex + 1}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                                                }
                                            }
                                            Icon(Icons.Default.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { showBookmarksDialog = false }) {
                                    Text("Close")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog Create Category Group
    if (showCreateGroupDialog) {
        Dialog(onDismissRequest = { showCreateGroupDialog = false }) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                var groupNameInput by remember { mutableStateOf("") }

                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "Create Group Category",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = groupNameInput,
                        onValueChange = { groupNameInput = it },
                        placeholder = { Text("E.g. German Readings, Italian Clips") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showCreateGroupDialog = false }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = {
                                viewModel.addGroup(groupNameInput)
                                showCreateGroupDialog = false
                            }
                        ) {
                            Text("Create")
                        }
                    }
                }
            }
        }
    }

    // Google Translate Shortcut App Launcher overlay dialog (Premium Add-On)
    showGTranslateShortcutDialog?.let { currentLabel ->
        Dialog(onDismissRequest = { showGTranslateShortcutDialog = null }) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .widthIn(max = 410.dp)
            ) {
                var translateTextInput by remember { mutableStateOf("") }
                
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Translate,
                            contentDescription = "Google Translate Toolkit",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Google Translate Quick Tool",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "Access Google Scholar translation instantly! Paste any difficult word, line, or idiom you are studying from your list below to translate in the target Google Translate utility:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = translateTextInput,
                        onValueChange = { translateTextInput = it },
                        placeholder = { Text("Type word / paste idiom here...") },
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        shape = MaterialTheme.shapes.medium
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showGTranslateShortcutDialog = null }) {
                            Text("Dismiss")
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = {
                                if (translateTextInput.isNotBlank()) {
                                    try {
                                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(android.content.Intent.EXTRA_TEXT, translateTextInput)
                                            `package` = "com.google.android.apps.translate"
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        val encodedText = java.net.URLEncoder.encode(translateTextInput, "UTF-8")
                                        val webIntent = android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse("https://translate.google.com/?sl=auto&tl=en&text=$encodedText&op=translate")
                                        )
                                        context.startActivity(webIntent)
                                    }
                                }
                                showGTranslateShortcutDialog = null
                            },
                            enabled = translateTextInput.isNotBlank()
                        ) {
                            Text("Open Translate App")
                        }
                    }
                }
            }
        }
    }

    // Dialog Create Story inside selected Category Group (Contains Direct File Import contract)
    showCreateStoryDialog?.let { targetGroup ->
        Dialog(onDismissRequest = { showCreateStoryDialog = null }) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .widthIn(max = 450.dp)
            ) {
                var storyTitleInput by remember { mutableStateOf("") }
                var rawBilingualContentInput by remember { mutableStateOf("") }

                val filePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri ->
                    uri?.let {
                        val contentResolver = context.contentResolver
                        try {
                            contentResolver.openInputStream(uri)?.use { inputStream ->
                                val txt = inputStream.bufferedReader().use { reader -> reader.readText() }
                                rawBilingualContentInput = txt
                                Toast.makeText(context, "Loaded file contents into editor!", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error loading file: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    }
                }

                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "Add Story to '${targetGroup.name}'",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = storyTitleInput,
                        onValueChange = { storyTitleInput = it },
                        label = { Text("Story Title") },
                        placeholder = { Text("E.g. Chapter 1, Short Story") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Bilingual text input (formatted: Original | Translation per line):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = rawBilingualContentInput,
                        onValueChange = { rawBilingualContentInput = it },
                        placeholder = { Text("Or write/paste sentences here...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        shape = MaterialTheme.shapes.medium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Import Text File Trigger Row
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
                            contentDescription = "Upload Text File",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Import from a (.txt) Text File",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showCreateStoryDialog = null }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = {
                                viewModel.addStoryToGroup(
                                    groupId = targetGroup.id,
                                    title = storyTitleInput,
                                    content = rawBilingualContentInput,
                                    onSuccess = {
                                        showCreateStoryDialog = null
                                        onNavigateToFeed()
                                    }
                                )
                            },
                            enabled = storyTitleInput.isNotBlank()
                        ) {
                            Text("Create & Read")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardStatTile(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        ),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
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

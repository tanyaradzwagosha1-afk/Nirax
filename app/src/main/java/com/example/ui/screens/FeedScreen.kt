package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.Comment
import com.example.data.Post
import com.example.data.PostCategory
import com.example.data.PublicProfile
import com.example.data.UiState
import com.example.ui.components.CategoryBadge
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ErrorStateBanner
import com.example.ui.components.NiraxAvatar
import com.example.ui.components.PostCard
import com.example.ui.components.PostPlaceholderCard
import com.example.ui.components.formatRelativeTimestamp
import com.example.ui.theme.CoralAccent
import com.example.ui.theme.IndigoPrimary

private enum class FeedFilter(val label: String) {
    ALL("All"),
    FOLLOWING("Following"),
    DISCUSSION("Discussion"),
    UPDATE("Update"),
    QUESTION("Question"),
    SHOWCASE("Showcase")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    currentUserId: String,
    myProfile: PublicProfile?,
    postsState: UiState<List<Post>>,
    commentsState: UiState<List<Comment>>,
    likedPostIds: Set<String>,
    followingIds: Set<String>,
    isMutating: Boolean,
    onCreatePost: (String, PostCategory, () -> Unit) -> Unit,
    onEditPost: (String, String, PostCategory, () -> Unit) -> Unit,
    onDeletePost: (String) -> Unit,
    onToggleLike: (String) -> Unit,
    onOpenThread: (String?) -> Unit,
    onAddComment: (String, String, () -> Unit) -> Unit,
    onDeleteComment: (String, String) -> Unit,
    onMessageUser: (String, String, String, Int) -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeFilter by remember { mutableStateOf(FeedFilter.ALL) }
    var showComposeSheet by remember { mutableStateOf(false) }
    var editingPost by remember { mutableStateOf<Post?>(null) }
    var activeThreadPost by remember { mutableStateOf<Post?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingPost = null
                    showComposeSheet = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("compose_post_fab")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.compose_post)
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxSize()
            ) {
                // Brand Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Brush.linearGradient(listOf(IndigoPrimary, CoralAccent))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "N",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Text(
                                text = "Community Pulse",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (myProfile != null) {
                        NiraxAvatar(
                            name = myProfile.displayName,
                            avatarIndex = myProfile.avatarIndex,
                            size = 38.dp,
                            online = myProfile.online,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(onClick = onProfileClick)
                                .testTag("feed_profile_avatar")
                        )
                    }
                }

                // Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FeedFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = activeFilter == filter,
                            onClick = { activeFilter = filter },
                            label = { Text(filter.label) },
                            modifier = Modifier.testTag("feed_filter_${filter.name.lowercase()}")
                        )
                    }
                }

                when (postsState) {
                    is UiState.Loading -> {
                        LazyColumn(
                            contentPadding = PaddingValues(
                                start = 20.dp,
                                end = 20.dp,
                                top = 10.dp,
                                bottom = 96.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.testTag("feed_loading_list")
                        ) {
                            items(4) { index ->
                                PostPlaceholderCard(
                                    modifier = Modifier.testTag("post_placeholder_$index")
                                )
                            }
                        }
                    }

                    is UiState.Error -> {
                        Box(modifier = Modifier.padding(20.dp)) {
                            ErrorStateBanner(message = postsState.message)
                        }
                    }

                    is UiState.Success -> {
                        val filteredPosts = remember(postsState.data, activeFilter, followingIds, currentUserId) {
                            when (activeFilter) {
                                FeedFilter.ALL -> postsState.data
                                FeedFilter.FOLLOWING -> postsState.data.filter {
                                    it.authorId == currentUserId || followingIds.contains(it.authorId)
                                }
                                FeedFilter.DISCUSSION -> postsState.data.filter { it.category == PostCategory.Discussion.name }
                                FeedFilter.UPDATE -> postsState.data.filter { it.category == PostCategory.Update.name }
                                FeedFilter.QUESTION -> postsState.data.filter { it.category == PostCategory.Question.name }
                                FeedFilter.SHOWCASE -> postsState.data.filter { it.category == PostCategory.Showcase.name }
                            }
                        }

                        if (filteredPosts.isEmpty()) {
                            EmptyStateView(
                                title = stringResource(R.string.feed_empty_title),
                                body = if (activeFilter == FeedFilter.FOLLOWING) {
                                    "Follow people in Discover to see their posts here, or switch back to All."
                                } else {
                                    stringResource(R.string.feed_empty_body)
                                }
                            )
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(
                                    start = 20.dp,
                                    end = 20.dp,
                                    top = 10.dp,
                                    bottom = 96.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.testTag("feed_posts_list")
                            ) {
                                items(filteredPosts, key = { it.id }) { post ->
                                    PostCard(
                                        post = post,
                                        isLiked = likedPostIds.contains(post.id),
                                        isOwnPost = post.authorId == currentUserId,
                                        onLikeClick = { onToggleLike(post.id) },
                                        onOpenThread = {
                                            activeThreadPost = post
                                            onOpenThread(post.id)
                                        },
                                        onMessageAuthor = {
                                            onMessageUser(
                                                post.authorId,
                                                post.authorName,
                                                post.authorHandle,
                                                post.authorAvatarIndex
                                            )
                                        },
                                        onEditClick = {
                                            editingPost = post
                                            showComposeSheet = true
                                        },
                                        onDeleteClick = { onDeletePost(post.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showComposeSheet) {
        val targetEdit = editingPost
        ComposePostSheet(
            initialContent = targetEdit?.content.orEmpty(),
            initialCategory = PostCategory.fromRaw(targetEdit?.category),
            isEditing = targetEdit != null,
            isSaving = isMutating,
            onDismiss = {
                showComposeSheet = false
                editingPost = null
            },
            onSubmit = { text, category ->
                if (targetEdit != null) {
                    onEditPost(targetEdit.id, text, category) {
                        showComposeSheet = false
                        editingPost = null
                    }
                } else {
                    onCreatePost(text, category) {
                        showComposeSheet = false
                    }
                }
            }
        )
    }

    val threadPost = activeThreadPost
    if (threadPost != null) {
        // Keep thread post count synced with live feed if available
        val livePost = (postsState as? UiState.Success)?.data
            ?.firstOrNull { it.id == threadPost.id }
            ?: threadPost
        PostThreadSheet(
            post = livePost,
            currentUserId = currentUserId,
            commentsState = commentsState,
            isMutating = isMutating,
            onDismiss = {
                activeThreadPost = null
                onOpenThread(null)
            },
            onSendReply = { replyText, onDone ->
                onAddComment(livePost.id, replyText, onDone)
            },
            onDeleteReply = { commentId ->
                onDeleteComment(livePost.id, commentId)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposePostSheet(
    initialContent: String,
    initialCategory: PostCategory,
    isEditing: Boolean,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String, PostCategory) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var content by remember(initialContent) { mutableStateOf(initialContent) }
    var selectedCategory by remember(initialCategory) { mutableStateOf(initialCategory) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEditing) "Edit post" else "Share with Nirax",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "${content.length}/500",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (content.length > 500) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PostCategory.entries.forEach { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category.label) },
                        modifier = Modifier.testTag("compose_category_${category.name.lowercase()}")
                    )
                }
            }

            OutlinedTextField(
                value = content,
                onValueChange = { if (it.length <= 500) content = it },
                placeholder = { Text("What's on your mind? Spark a conversation...") },
                minLines = 4,
                maxLines = 8,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("compose_post_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onSubmit(content, selectedCategory) },
                    enabled = !isSaving && content.trim().isNotEmpty(),
                    modifier = Modifier.testTag("publish_post_button")
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(if (isEditing) "Save changes" else "Publish")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostThreadSheet(
    post: Post,
    currentUserId: String,
    commentsState: UiState<List<Comment>>,
    isMutating: Boolean,
    onDismiss: () -> Unit,
    onSendReply: (String, () -> Unit) -> Unit,
    onDeleteReply: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var replyText by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Thread (${post.commentsCount})",
                    style = MaterialTheme.typography.titleLarge
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close thread")
                }
            }

            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            NiraxAvatar(name = post.authorName, avatarIndex = post.authorAvatarIndex, size = 34.dp)
                            Column {
                                Text(post.authorName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    post.authorHandle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        CategoryBadge(PostCategory.fromRaw(post.category))
                    }
                    Text(post.content, style = MaterialTheme.typography.bodyMedium)
                }
            }

            HorizontalDivider()

            when (commentsState) {
                is UiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    ErrorStateBanner(message = commentsState.message)
                }
                is UiState.Success -> {
                    if (commentsState.data.isEmpty()) {
                        Text(
                            text = "No replies yet. Start the discussion below.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(240.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(commentsState.data, key = { it.id }) { comment ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant,
                                            RoundedCornerShape(14.dp)
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        NiraxAvatar(
                                            name = comment.authorName,
                                            avatarIndex = comment.authorAvatarIndex,
                                            size = 32.dp
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = comment.authorName,
                                                    style = MaterialTheme.typography.labelLarge
                                                )
                                                Text(
                                                    text = formatRelativeTimestamp(comment.createdAt),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = comment.text,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                        if (comment.authorId == currentUserId) {
                                            IconButton(
                                                onClick = { onDeleteReply(comment.id) },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.DeleteOutline,
                                                    contentDescription = "Delete reply",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = replyText,
                    onValueChange = { if (it.length <= 300) replyText = it },
                    placeholder = { Text("Write a reply...") },
                    singleLine = false,
                    maxLines = 3,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("reply_input")
                )
                IconButton(
                    onClick = {
                        onSendReply(replyText) {
                            replyText = ""
                        }
                    },
                    enabled = !isMutating && replyText.trim().isNotEmpty(),
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (replyText.trim().isNotEmpty()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .testTag("send_reply_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Send reply",
                        tint = if (replyText.trim().isNotEmpty()) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

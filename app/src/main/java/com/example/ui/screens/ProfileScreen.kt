package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.Comment
import com.example.data.Post
import com.example.data.PostCategory
import com.example.data.PublicProfile
import com.example.data.UiState
import com.example.ui.components.ErrorStateBanner
import com.example.ui.components.NiraxAvatar
import com.example.ui.components.PostCard
import com.example.ui.theme.AvatarPalettes
import com.example.ui.theme.OnlineEmerald

@Composable
fun ProfileScreen(
    currentUserId: String,
    profileState: UiState<PublicProfile?>,
    postsState: UiState<List<Post>>,
    commentsState: UiState<List<Comment>>,
    followingCount: Int,
    likedPostIds: Set<String>,
    isMutating: Boolean,
    onUpdateProfile: (String, String, String, String, Int, Boolean, () -> Unit) -> Unit,
    onToggleLike: (String) -> Unit,
    onOpenThread: (String?) -> Unit,
    onAddComment: (String, String, () -> Unit) -> Unit,
    onDeleteComment: (String, String) -> Unit,
    onEditPost: (String, String, PostCategory, () -> Unit) -> Unit,
    onDeletePost: (String) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showEditProfileSheet by remember { mutableStateOf(false) }
    var editingPost by remember { mutableStateOf<Post?>(null) }
    var activeThreadPost by remember { mutableStateOf<Post?>(null) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        when (profileState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> {
                Box(modifier = Modifier.padding(20.dp)) {
                    ErrorStateBanner(message = profileState.message)
                }
            }
            is UiState.Success -> {
                val profile = profileState.data
                val myPosts = remember(postsState, currentUserId) {
                    (postsState as? UiState.Success)?.data?.filter { it.authorId == currentUserId }.orEmpty()
                }
                val totalLikes = remember(myPosts) { myPosts.sumOf { it.likesCount } }

                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = 680.dp)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Your Profile", style = MaterialTheme.typography.headlineSmall)
                            OutlinedButton(
                                onClick = onSignOut,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("sign_out_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Logout,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.sign_out))
                            }
                        }
                    }

                    if (profile != null) {
                        item {
                            Surface(
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 2.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        NiraxAvatar(
                                            name = profile.displayName,
                                            avatarIndex = profile.avatarIndex,
                                            size = 64.dp,
                                            online = profile.online
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(profile.displayName, style = MaterialTheme.typography.titleLarge)
                                            Text(
                                                text = profile.handle,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (profile.statusNote.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = profile.statusNote,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = if (profile.online) OnlineEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    if (profile.bio.isNotBlank()) {
                                        Text(
                                            text = profile.bio,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        StatPill(label = "Posts", value = myPosts.size.toString(), modifier = Modifier.weight(1f))
                                        StatPill(label = "Following", value = followingCount.toString(), modifier = Modifier.weight(1f))
                                        StatPill(label = "Likes", value = totalLikes.toString(), modifier = Modifier.weight(1f))
                                    }

                                    Button(
                                        onClick = { showEditProfileSheet = true },
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("edit_profile_button")
                                    ) {
                                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Edit Profile & Status")
                                    }
                                }
                            }
                        }

                        item {
                            Text(
                                text = "Your Posts (${myPosts.size})",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }

                        if (myPosts.isEmpty()) {
                            item {
                                Text(
                                    text = "Posts you publish in the community feed will appear here.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            items(myPosts, key = { it.id }) { post ->
                                PostCard(
                                    post = post,
                                    isLiked = likedPostIds.contains(post.id),
                                    isOwnPost = true,
                                    onLikeClick = { onToggleLike(post.id) },
                                    onOpenThread = {
                                        activeThreadPost = post
                                        onOpenThread(post.id)
                                    },
                                    onMessageAuthor = {},
                                    onEditClick = { editingPost = post },
                                    onDeleteClick = { onDeletePost(post.id) }
                                )
                            }
                        }
                    }
                }

                if (showEditProfileSheet && profile != null) {
                    EditProfileSheet(
                        profile = profile,
                        isSaving = isMutating,
                        onDismiss = { showEditProfileSheet = false },
                        onSave = { name, handle, bio, status, avatar, online ->
                            onUpdateProfile(name, handle, bio, status, avatar, online) {
                                showEditProfileSheet = false
                            }
                        }
                    )
                }

                val targetEdit = editingPost
                if (targetEdit != null) {
                    ComposePostSheet(
                        initialContent = targetEdit.content,
                        initialCategory = PostCategory.fromRaw(targetEdit.category),
                        isEditing = true,
                        isSaving = isMutating,
                        onDismiss = { editingPost = null },
                        onSubmit = { text, category ->
                            onEditPost(targetEdit.id, text, category) {
                                editingPost = null
                            }
                        }
                    )
                }

                val threadPost = activeThreadPost
                if (threadPost != null) {
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
        }
    }
}

@Composable
private fun StatPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.titleMedium)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProfileSheet(
    profile: PublicProfile,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, Int, Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var displayName by remember(profile) { mutableStateOf(profile.displayName) }
    var handle by remember(profile) { mutableStateOf(profile.handle) }
    var bio by remember(profile) { mutableStateOf(profile.bio) }
    var statusNote by remember(profile) { mutableStateOf(profile.statusNote) }
    var avatarIndex by remember(profile) { mutableIntStateOf(profile.avatarIndex) }
    var online by remember(profile) { mutableStateOf(profile.online) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Edit Profile", style = MaterialTheme.typography.titleLarge)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AvatarPalettes.forEachIndexed { idx, pair ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(pair.first, pair.second)))
                            .then(
                                if (avatarIndex == idx) {
                                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                } else Modifier
                            )
                            .clickable { avatarIndex = idx },
                        contentAlignment = Alignment.Center
                    ) {
                        if (avatarIndex == idx) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Selected avatar color",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = displayName,
                onValueChange = { if (it.length <= 60) displayName = it },
                label = { Text("Display name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_display_name_input")
            )

            OutlinedTextField(
                value = handle,
                onValueChange = { if (it.length <= 32) handle = it },
                label = { Text("Handle") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_handle_input")
            )

            OutlinedTextField(
                value = statusNote,
                onValueChange = { if (it.length <= 80) statusNote = it },
                label = { Text("Status note") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_status_input")
            )

            OutlinedTextField(
                value = bio,
                onValueChange = { if (it.length <= 240) bio = it },
                label = { Text("Bio") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_bio_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Available to chat", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Show an active badge on your avatar",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = online,
                    onCheckedChange = { online = it },
                    modifier = Modifier.testTag("online_status_switch")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        onSave(displayName, handle, bio, statusNote, avatarIndex, online)
                    },
                    enabled = !isSaving && displayName.trim().isNotEmpty(),
                    modifier = Modifier.testTag("save_profile_button")
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Save")
                    }
                }
            }
        }
    }
}

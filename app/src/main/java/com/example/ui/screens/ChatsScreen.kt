package com.example.ui.screens

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.Conversation
import com.example.data.PublicProfile
import com.example.data.UiState
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ErrorStateBanner
import com.example.ui.components.NiraxAvatar
import com.example.ui.components.formatRelativeTimestamp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(
    currentUserId: String,
    conversationsState: UiState<List<Conversation>>,
    profilesState: UiState<List<PublicProfile>>,
    onOpenConversation: (PublicProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    var showNewChatPicker by remember { mutableStateOf(false) }

    val profilesById = remember(profilesState) {
        (profilesState as? UiState.Success)?.data?.associateBy { it.userId }.orEmpty()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewChatPicker = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("new_chat_fab")
            ) {
                Icon(
                    imageVector = Icons.Rounded.AddComment,
                    contentDescription = stringResource(R.string.new_message)
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
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Messages",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "Real-time 1-on-1 conversations",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))

                when (conversationsState) {
                    is UiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is UiState.Error -> {
                        ErrorStateBanner(message = conversationsState.message)
                    }
                    is UiState.Success -> {
                        if (conversationsState.data.isEmpty()) {
                            EmptyStateView(
                                title = stringResource(R.string.chats_empty_title),
                                body = stringResource(R.string.chats_empty_body)
                            )
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(bottom = 96.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(conversationsState.data, key = { it.id }) { conv ->
                                    val otherId = conv.participantIds.firstOrNull { it != currentUserId }
                                        ?: currentUserId
                                    val liveProfile = profilesById[otherId]
                                    val otherName = liveProfile?.displayName
                                        ?: conv.participantNames[otherId]
                                        ?: "Nirax User"
                                    val otherHandle = liveProfile?.handle
                                        ?: conv.participantHandles[otherId]
                                        ?: "@member"
                                    val otherAvatar = liveProfile?.avatarIndex
                                        ?: (conv.participantAvatars[otherId]?.toInt() ?: 0)
                                    val isOnline = liveProfile?.online ?: false

                                    val targetProfile = liveProfile ?: PublicProfile(
                                        userId = otherId,
                                        displayName = otherName,
                                        handle = otherHandle,
                                        avatarIndex = otherAvatar
                                    )

                                    ConversationRowCard(
                                        conversation = conv,
                                        currentUserId = currentUserId,
                                        otherName = otherName,
                                        otherAvatar = otherAvatar,
                                        isOnline = isOnline,
                                        onClick = { onOpenConversation(targetProfile) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewChatPicker) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var pickerQuery by remember { mutableStateOf("") }
        ModalBottomSheet(
            onDismissRequest = { showNewChatPicker = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Start a conversation",
                    style = MaterialTheme.typography.titleLarge
                )
                OutlinedTextField(
                    value = pickerQuery,
                    onValueChange = { pickerQuery = it },
                    placeholder = { Text("Search people...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                val candidates = remember(profilesState, pickerQuery, currentUserId) {
                    val q = pickerQuery.trim().lowercase()
                    (profilesState as? UiState.Success)?.data
                        ?.filter { it.userId != currentUserId }
                        ?.filter {
                            q.isEmpty() ||
                                it.displayName.lowercase().contains(q) ||
                                it.handle.lowercase().contains(q)
                        }
                        .orEmpty()
                }
                if (candidates.isEmpty()) {
                    Text(
                        text = "No other Nirax members found yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 20.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(candidates, key = { it.userId }) { person ->
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
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        showNewChatPicker = false
                                        onOpenConversation(person)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    NiraxAvatar(
                                        name = person.displayName,
                                        avatarIndex = person.avatarIndex,
                                        size = 42.dp,
                                        online = person.online
                                    )
                                    Column {
                                        Text(person.displayName, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            person.handle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
}

@Composable
private fun ConversationRowCard(
    conversation: Conversation,
    currentUserId: String,
    otherName: String,
    otherAvatar: Int,
    isOnline: Boolean,
    onClick: () -> Unit
) {
    val previewPrefix = if (conversation.lastMessageSenderId == currentUserId) "You: " else ""
    val timeLabel = remember(conversation.lastMessageAt, conversation.updatedAt) {
        formatRelativeTimestamp(conversation.lastMessageAt ?: conversation.updatedAt)
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .testTag("conversation_row_${conversation.id}")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NiraxAvatar(
                name = otherName,
                avatarIndex = otherAvatar,
                size = 48.dp,
                online = isOnline
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = otherName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "$previewPrefix${conversation.lastMessageText}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

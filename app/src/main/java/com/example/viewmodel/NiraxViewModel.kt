package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.Comment
import com.example.data.Conversation
import com.example.data.Message
import com.example.data.NiraxRepository
import com.example.data.Post
import com.example.data.PostCategory
import com.example.data.PublicProfile
import com.example.data.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class NiraxViewModel(
    private val repository: NiraxRepository,
    val currentUserId: String
) : ViewModel() {

    private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarMessages: SharedFlow<String> = _snackbarMessages.asSharedFlow()

    private val _isMutating = MutableStateFlow(false)
    val isMutating: StateFlow<Boolean> = _isMutating.asStateFlow()

    private val selectedPostId = MutableStateFlow<String?>(null)
    private val activeConversationId = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            val user = Firebase.auth.currentUser
            if (user != null && user.uid == currentUserId) {
                repository.ensureUserProvisioned(user)
                    .onFailure { err ->
                        _snackbarMessages.tryEmit(err.localizedMessage ?: "Failed to initialize profile")
                    }
            }
        }
    }

    val myProfileState: StateFlow<UiState<PublicProfile?>> =
        repository.observeMyProfile(currentUserId)
            .toUiStateFlow("Failed to load your profile")
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val profilesState: StateFlow<UiState<List<PublicProfile>>> =
        repository.observeDiscoverableProfiles()
            .toUiStateFlow("Failed to load people")
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val followingIds: StateFlow<Set<String>> =
        repository.observeFollowingIds(currentUserId)
            .catch { error ->
                unwrapAndLog(error, "Error observing following list")
                emit(emptySet())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = emptySet()
            )

    val likedPostIds: StateFlow<Set<String>> =
        repository.observeLikedPostIds(currentUserId)
            .catch { error ->
                unwrapAndLog(error, "Error observing likes")
                emit(emptySet())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = emptySet()
            )

    val postsState: StateFlow<UiState<List<Post>>> =
        repository.observePublicPosts()
            .toUiStateFlow("Failed to load community feed")
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val conversationsState: StateFlow<UiState<List<Conversation>>> =
        repository.observeConversations(currentUserId)
            .toUiStateFlow("Failed to load conversations")
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val commentsState: StateFlow<UiState<List<Comment>>> =
        selectedPostId
            .flatMapLatest { postId ->
                if (postId.isNullOrBlank()) {
                    flowOf<UiState<List<Comment>>>(UiState.Success(emptyList()))
                } else {
                    repository.observePostComments(postId)
                        .toUiStateFlow("Failed to load replies")
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val messagesState: StateFlow<UiState<List<Message>>> =
        activeConversationId
            .flatMapLatest { convId ->
                if (convId.isNullOrBlank()) {
                    flowOf<UiState<List<Message>>>(UiState.Success(emptyList()))
                } else {
                    repository.observeMessages(convId, currentUserId)
                        .toUiStateFlow("Failed to load messages")
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    fun selectPostForThread(postId: String?) {
        selectedPostId.value = postId
    }

    fun openConversationThread(otherUserId: String) {
        activeConversationId.value = repository.deterministicConversationId(currentUserId, otherUserId)
    }

    fun closeConversationThread() {
        activeConversationId.value = null
    }

    fun conversationIdFor(otherUserId: String): String =
        repository.deterministicConversationId(currentUserId, otherUserId)

    fun createPost(content: String, category: PostCategory, onSuccess: () -> Unit = {}) {
        val profile = (myProfileState.value as? UiState.Success)?.data
        if (profile == null) {
            _snackbarMessages.tryEmit("Your profile is still loading. Please wait a moment.")
            return
        }
        val trimmed = content.trim()
        if (trimmed.isEmpty() || trimmed.length > 500) {
            _snackbarMessages.tryEmit("Post must be between 1 and 500 characters.")
            return
        }
        viewModelScope.launch {
            _isMutating.value = true
            val result = repository.createPost(profile, trimmed, category)
            _isMutating.value = false
            result
                .onSuccess { onSuccess() }
                .onFailure { _snackbarMessages.tryEmit(it.localizedMessage ?: "Unable to publish post.") }
        }
    }

    fun editPost(postId: String, content: String, category: PostCategory, onSuccess: () -> Unit = {}) {
        val trimmed = content.trim()
        if (trimmed.isEmpty() || trimmed.length > 500) {
            _snackbarMessages.tryEmit("Post must be between 1 and 500 characters.")
            return
        }
        viewModelScope.launch {
            _isMutating.value = true
            val result = repository.editPost(postId, trimmed, category)
            _isMutating.value = false
            result
                .onSuccess { onSuccess() }
                .onFailure { _snackbarMessages.tryEmit(it.localizedMessage ?: "Unable to update post.") }
        }
    }

    fun deletePost(postId: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deletePost(postId)
                .onSuccess {
                    _snackbarMessages.tryEmit("Post deleted")
                    onSuccess()
                }
                .onFailure { _snackbarMessages.tryEmit(it.localizedMessage ?: "Unable to delete post.") }
        }
    }

    fun toggleLike(postId: String) {
        val currentlyLiked = likedPostIds.value.contains(postId)
        viewModelScope.launch {
            repository.toggleLike(postId, currentlyLiked)
                .onFailure { _snackbarMessages.tryEmit(it.localizedMessage ?: "Unable to update like.") }
        }
    }

    fun addComment(postId: String, text: String, onSuccess: () -> Unit = {}) {
        val profile = (myProfileState.value as? UiState.Success)?.data
        if (profile == null) {
            _snackbarMessages.tryEmit("Your profile is still loading.")
            return
        }
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > 300) {
            _snackbarMessages.tryEmit("Reply must be between 1 and 300 characters.")
            return
        }
        viewModelScope.launch {
            _isMutating.value = true
            val result = repository.addComment(postId, profile, trimmed)
            _isMutating.value = false
            result
                .onSuccess { onSuccess() }
                .onFailure { _snackbarMessages.tryEmit(it.localizedMessage ?: "Unable to send reply.") }
        }
    }

    fun deleteComment(postId: String, commentId: String) {
        viewModelScope.launch {
            repository.deleteComment(postId, commentId)
                .onFailure { _snackbarMessages.tryEmit(it.localizedMessage ?: "Unable to delete reply.") }
        }
    }

    fun toggleFollow(target: PublicProfile) {
        val currentlyFollowing = followingIds.value.contains(target.userId)
        viewModelScope.launch {
            repository.toggleFollow(target, currentlyFollowing)
                .onSuccess {
                    val verb = if (currentlyFollowing) "Unfollowed" else "Following"
                    _snackbarMessages.tryEmit("$verb ${target.displayName}")
                }
                .onFailure { _snackbarMessages.tryEmit(it.localizedMessage ?: "Unable to update follow state.") }
        }
    }

    fun updateProfile(
        displayName: String,
        handle: String,
        bio: String,
        statusNote: String,
        avatarIndex: Int,
        online: Boolean,
        onSuccess: () -> Unit = {}
    ) {
        if (displayName.trim().isEmpty() || displayName.trim().length > 60) {
            _snackbarMessages.tryEmit("Display name must be 1–60 characters.")
            return
        }
        val cleanHandle = handle.trim().removePrefix("@").replace(Regex("[^a-zA-Z0-9_]"), "")
        if (cleanHandle.isEmpty() || cleanHandle.length > 30) {
            _snackbarMessages.tryEmit("Handle must use 1–30 letters, numbers, or underscores.")
            return
        }
        viewModelScope.launch {
            _isMutating.value = true
            val result = repository.updateMyProfile(
                displayName = displayName,
                handle = "@$cleanHandle",
                bio = bio,
                statusNote = statusNote,
                avatarIndex = avatarIndex,
                online = online
            )
            _isMutating.value = false
            result
                .onSuccess {
                    _snackbarMessages.tryEmit("Profile saved")
                    onSuccess()
                }
                .onFailure { _snackbarMessages.tryEmit(it.localizedMessage ?: "Failed to update profile.") }
        }
    }

    fun sendMessage(recipient: PublicProfile, text: String, onSuccess: () -> Unit = {}) {
        val sender = (myProfileState.value as? UiState.Success)?.data
        if (sender == null) {
            _snackbarMessages.tryEmit("Your profile is still loading.")
            return
        }
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > 500) {
            _snackbarMessages.tryEmit("Message must be between 1 and 500 characters.")
            return
        }
        viewModelScope.launch {
            _isMutating.value = true
            val result = repository.sendMessage(sender, recipient, trimmed)
            _isMutating.value = false
            result
                .onSuccess { onSuccess() }
                .onFailure { _snackbarMessages.tryEmit(it.localizedMessage ?: "Message failed to send.") }
        }
    }

    private fun <T> Flow<T>.toUiStateFlow(fallbackError: String): Flow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val rootError = unwrapAndLog(error, fallbackError)
                emit(UiState.Error(rootError.message ?: fallbackError))
            }

    private fun unwrapAndLog(error: Throwable, contextMessage: String): Throwable {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, contextMessage, rootError)
        return rootError
    }

    private companion object {
        const val TAG = "NiraxViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}

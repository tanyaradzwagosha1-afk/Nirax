package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.math.absoluteValue

class NiraxRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun handleStreamError(error: Throwable, op: OperationType, path: String, expectedUid: String?) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && (expectedUid == null || auth.currentUser?.uid == expectedUid)) {
            handleFirestoreError(exceptionToLog, op, path)
        }
    }

    suspend fun ensureUserProvisioned(user: FirebaseUser): Result<Unit> {
        val uid = user.uid
        val privatePath = "users/$uid"
        return try {
            val currentUid = requireUserId()
            check(currentUid == uid) { "Authenticated user mismatch." }

            val rawName = user.displayName?.trim()?.takeIf { it.isNotEmpty() } ?: "Nirax Member"
            val displayName = rawName.take(60)
            val handleBase = rawName.lowercase()
                .replace(Regex("[^a-z0-9_]"), "")
                .take(18)
                .ifEmpty { "niraxuser" }
            val suffix = uid.take(4).lowercase()
            val handle = "@${handleBase}_$suffix".take(32)
            val email = (user.email ?: "member@nirax.app").take(254)
            val avatarIndex = (uid.hashCode().absoluteValue) % 6

            val privateRef = db.collection("users").document(uid)
            val publicRef = db.collection("public_profiles").document(uid)

            val privateSnap = privateRef.get().await()
            val publicSnap = publicRef.get().await()

            val batch = db.batch()
            if (!privateSnap.exists()) {
                batch.set(
                    privateRef,
                    mapOf(
                        "userId" to uid,
                        "email" to email,
                        "displayName" to displayName,
                        "handle" to handle,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            if (!publicSnap.exists()) {
                batch.set(
                    publicRef,
                    mapOf(
                        "userId" to uid,
                        "displayName" to displayName,
                        "handle" to handle,
                        "bio" to "Here on Nirax to connect and share.",
                        "statusNote" to "Open to conversations",
                        "avatarIndex" to avatarIndex,
                        "online" to true,
                        "discoverable" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            if (!privateSnap.exists() || !publicSnap.exists()) {
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, privatePath)
            Result.failure(e)
        }
    }

    fun observeMyProfile(userId: String): Flow<PublicProfile?> = flow {
        val uid = requireUserId()
        require(userId.isNotBlank() && uid == userId)
        val path = "public_profiles/$userId"
        db.collection("public_profiles").document(userId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(PublicProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }
            .catch { error ->
                handleStreamError(error, OperationType.GET, path, uid)
                throw error
            }
            .collect { emit(it) }
    }

    fun observeDiscoverableProfiles(): Flow<List<PublicProfile>> = flow {
        val uid = requireUserId()
        val path = "public_profiles"
        db.collection(path)
            .whereEqualTo("discoverable", true)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(PublicProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.displayName.lowercase() }
            }
            .catch { error ->
                handleStreamError(error, OperationType.LIST, path, uid)
                throw error
            }
            .collect { emit(it) }
    }

    fun observeFollowingIds(userId: String): Flow<Set<String>> = flow {
        val uid = requireUserId()
        require(userId.isNotBlank() && uid == userId)
        val path = "users/$userId/following"
        db.collection("users").document(userId).collection("following")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(FollowingRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .map { it.targetUid.ifBlank { it.id } }
                    .toSet()
            }
            .catch { error ->
                handleStreamError(error, OperationType.LIST, path, uid)
                throw error
            }
            .collect { emit(it) }
    }

    fun observeLikedPostIds(userId: String): Flow<Set<String>> = flow {
        val uid = requireUserId()
        require(userId.isNotBlank() && uid == userId)
        val path = "users/$userId/liked_posts"
        db.collection("users").document(userId).collection("liked_posts")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(LikedPostRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .map { it.postId.ifBlank { it.id } }
                    .toSet()
            }
            .catch { error ->
                handleStreamError(error, OperationType.LIST, path, uid)
                throw error
            }
            .collect { emit(it) }
    }

    fun observePublicPosts(): Flow<List<Post>> = flow {
        val uid = requireUserId()
        val path = "posts"
        db.collection(path)
            .whereEqualTo("visibility", "public")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(Post::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                handleStreamError(error, OperationType.LIST, path, uid)
                throw error
            }
            .collect { emit(it) }
    }

    fun observePostComments(postId: String): Flow<List<Comment>> = flow {
        val uid = requireUserId()
        require(postId.isNotBlank())
        val path = "posts/$postId/comments"
        db.collection("posts").document(postId).collection("comments")
            .whereEqualTo("postId", postId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(Comment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                handleStreamError(error, OperationType.LIST, path, uid)
                throw error
            }
            .collect { emit(it) }
    }

    fun observeConversations(userId: String): Flow<List<Conversation>> = flow {
        val uid = requireUserId()
        require(userId.isNotBlank() && uid == userId)
        val path = "conversations"
        db.collection(path)
            .whereArrayContains("participantIds", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.lastMessageAt ?: it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                handleStreamError(error, OperationType.LIST, path, uid)
                throw error
            }
            .collect { emit(it) }
    }

    fun observeMessages(conversationId: String, userId: String): Flow<List<Message>> = flow {
        val uid = requireUserId()
        require(conversationId.isNotBlank() && userId.isNotBlank() && uid == userId)
        val path = "conversations/$conversationId/messages"
        db.collection("conversations").document(conversationId).collection("messages")
            .whereArrayContains("participantIds", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(Message::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                handleStreamError(error, OperationType.LIST, path, uid)
                throw error
            }
            .collect { emit(it) }
    }

    suspend fun createPost(
        author: PublicProfile,
        content: String,
        category: PostCategory
    ): Result<Unit> {
        var docPath = "posts"
        return try {
            val uid = requireUserId()
            check(author.userId == uid) { "Author profile does not match signed-in user." }
            val trimmed = content.trim()
            require(trimmed.length in 1..500) { "Post must be between 1 and 500 characters." }

            val docRef = db.collection("posts").document()
            docPath = docRef.path
            val payload = mapOf(
                "authorId" to uid,
                "authorName" to author.displayName.take(60),
                "authorHandle" to author.handle.take(32),
                "authorAvatarIndex" to author.avatarIndex.coerceIn(0, 5),
                "content" to trimmed,
                "category" to category.name,
                "likesCount" to 0L,
                "commentsCount" to 0L,
                "visibility" to "public",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun editPost(
        postId: String,
        content: String,
        category: PostCategory
    ): Result<Unit> {
        val docPath = "posts/$postId"
        return try {
            requireUserId()
            require(postId.isNotBlank()) { "Post ID cannot be blank." }
            val trimmed = content.trim()
            require(trimmed.length in 1..500) { "Post must be between 1 and 500 characters." }

            db.collection("posts").document(postId)
                .update(
                    mapOf(
                        "content" to trimmed,
                        "category" to category.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deletePost(postId: String): Result<Unit> {
        val docPath = "posts/$postId"
        return try {
            requireUserId()
            require(postId.isNotBlank()) { "Post ID cannot be blank." }
            db.collection("posts").document(postId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleLike(postId: String, currentlyLiked: Boolean): Result<Unit> {
        val docPath = "posts/$postId"
        return try {
            val uid = requireUserId()
            require(postId.isNotBlank()) { "Post ID cannot be blank." }

            val batch = db.batch()
            val likeRef = db.collection("users").document(uid).collection("liked_posts").document(postId)
            val postRef = db.collection("posts").document(postId)

            if (currentlyLiked) {
                batch.delete(likeRef)
                batch.update(
                    postRef,
                    mapOf(
                        "likesCount" to FieldValue.increment(-1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                batch.set(
                    likeRef,
                    mapOf(
                        "userId" to uid,
                        "postId" to postId,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.update(
                    postRef,
                    mapOf(
                        "likesCount" to FieldValue.increment(1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addComment(
        postId: String,
        author: PublicProfile,
        text: String
    ): Result<Unit> {
        var docPath = "posts/$postId/comments"
        return try {
            val uid = requireUserId()
            check(author.userId == uid)
            require(postId.isNotBlank())
            val trimmed = text.trim()
            require(trimmed.length in 1..300) { "Reply must be between 1 and 300 characters." }

            val commentRef = db.collection("posts").document(postId).collection("comments").document()
            docPath = commentRef.path
            val postRef = db.collection("posts").document(postId)

            val batch = db.batch()
            batch.set(
                commentRef,
                mapOf(
                    "postId" to postId,
                    "authorId" to uid,
                    "authorName" to author.displayName.take(60),
                    "authorHandle" to author.handle.take(32),
                    "authorAvatarIndex" to author.avatarIndex.coerceIn(0, 5),
                    "text" to trimmed,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                postRef,
                mapOf(
                    "commentsCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteComment(postId: String, commentId: String): Result<Unit> {
        val docPath = "posts/$postId/comments/$commentId"
        return try {
            requireUserId()
            require(postId.isNotBlank() && commentId.isNotBlank())
            val commentRef = db.collection("posts").document(postId).collection("comments").document(commentId)
            val postRef = db.collection("posts").document(postId)

            val batch = db.batch()
            batch.delete(commentRef)
            batch.update(
                postRef,
                mapOf(
                    "commentsCount" to FieldValue.increment(-1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleFollow(
        target: PublicProfile,
        currentlyFollowing: Boolean
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/following/${target.userId}"
        return try {
            val uid = requireUserId()
            require(target.userId.isNotBlank() && target.userId != uid)
            val followRef = db.collection("users").document(uid).collection("following").document(target.userId)
            docPath = followRef.path

            if (currentlyFollowing) {
                followRef.delete().await()
            } else {
                followRef.set(
                    mapOf(
                        "targetUid" to target.userId,
                        "targetName" to target.displayName.take(60),
                        "targetHandle" to target.handle.take(32),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateMyProfile(
        displayName: String,
        handle: String,
        bio: String,
        statusNote: String,
        avatarIndex: Int,
        online: Boolean
    ): Result<Unit> {
        var docPath = "public_profiles/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val cleanName = displayName.trim()
            require(cleanName.length in 1..60) { "Display name must be 1-60 characters." }

            val normalizedHandle = handle.trim().let {
                val stripped = it.removePrefix("@").replace(Regex("[^a-zA-Z0-9_]"), "")
                "@$stripped"
            }
            require(normalizedHandle.length in 2..32) { "Handle must be 2-32 characters." }

            val cleanBio = bio.trim().take(240)
            val cleanStatus = statusNote.trim().take(80)
            val safeAvatar = avatarIndex.coerceIn(0, 5)

            val publicRef = db.collection("public_profiles").document(uid)
            docPath = publicRef.path
            val privateRef = db.collection("users").document(uid)

            val batch = db.batch()
            batch.update(
                publicRef,
                mapOf(
                    "displayName" to cleanName,
                    "handle" to normalizedHandle,
                    "bio" to cleanBio,
                    "statusNote" to cleanStatus,
                    "avatarIndex" to safeAvatar,
                    "online" to online,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                privateRef,
                mapOf(
                    "displayName" to cleanName,
                    "handle" to normalizedHandle,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    fun deterministicConversationId(uidA: String, uidB: String): String {
        val sorted = listOf(uidA, uidB).sorted()
        return "${sorted[0]}_${sorted[1]}"
    }

    suspend fun sendMessage(
        sender: PublicProfile,
        recipient: PublicProfile,
        text: String
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val convId = deterministicConversationId(uid, recipient.userId)
        var docPath = "conversations/$convId/messages"
        return try {
            val currentUid = requireUserId()
            check(sender.userId == currentUid)
            require(recipient.userId.isNotBlank() && recipient.userId != currentUid) {
                "Invalid recipient."
            }
            val trimmed = text.trim()
            require(trimmed.length in 1..500) { "Message must be between 1 and 500 characters." }

            val finalConvId = deterministicConversationId(currentUid, recipient.userId)
            val convRef = db.collection("conversations").document(finalConvId)
            val msgRef = convRef.collection("messages").document()
            docPath = msgRef.path

            val convSnap = convRef.get().await()
            val participants = listOf(currentUid, recipient.userId).sorted()

            val namesMap = mapOf(
                currentUid to sender.displayName.take(60),
                recipient.userId to recipient.displayName.take(60)
            )
            val handlesMap = mapOf(
                currentUid to sender.handle.take(32),
                recipient.userId to recipient.handle.take(32)
            )
            val avatarsMap = mapOf(
                currentUid to sender.avatarIndex.coerceIn(0, 5),
                recipient.userId to recipient.avatarIndex.coerceIn(0, 5)
            )

            val batch = db.batch()
            if (!convSnap.exists()) {
                batch.set(
                    convRef,
                    mapOf(
                        "participantIds" to participants,
                        "participantNames" to namesMap,
                        "participantHandles" to handlesMap,
                        "participantAvatars" to avatarsMap,
                        "lastMessageText" to trimmed,
                        "lastMessageSenderId" to currentUid,
                        "lastMessageAt" to FieldValue.serverTimestamp(),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                batch.update(
                    convRef,
                    mapOf(
                        "participantNames" to namesMap,
                        "participantHandles" to handlesMap,
                        "participantAvatars" to avatarsMap,
                        "lastMessageText" to trimmed,
                        "lastMessageSenderId" to currentUid,
                        "lastMessageAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.set(
                msgRef,
                mapOf(
                    "conversationId" to finalConvId,
                    "senderId" to currentUid,
                    "senderName" to sender.displayName.take(60),
                    "participantIds" to participants,
                    "text" to trimmed,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(finalConvId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }
}

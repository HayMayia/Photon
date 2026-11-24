package com.example.data

import android.content.Context
import androidx.annotation.Keep
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

@Keep
@IgnoreExtraProperties
data class UserProfileDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String? = null,
    val photoUrl: String? = null,
    val youtubeConnected: Boolean = false,
    val youtubeHandle: String? = null,
    val subscribedChannels: List<String> = emptyList(),
    val favoriteArtists: List<String> = emptyList(),
    val preferredMode: String = "YOUTUBE",
    val autoplayEnabled: Boolean = true,
    val onboardingComplete: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toDomain(): UserAccountProfile = UserAccountProfile(
        uid = userId.ifBlank { id },
        displayName = displayName,
        email = email.orEmpty(),
        photoUrl = photoUrl.orEmpty(),
        youtubeConnected = youtubeConnected,
        youtubeHandle = youtubeHandle.orEmpty(),
        subscribedChannels = subscribedChannels,
        favoriteArtists = favoriteArtists,
        preferredMode = AppMode.entries.firstOrNull { it.name == preferredMode } ?: AppMode.YOUTUBE,
        autoplayEnabled = autoplayEnabled,
        onboardingComplete = onboardingComplete
    )
}

@Keep
@IgnoreExtraProperties
data class SavedMediaDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val bucket: String = "LIKED",
    val videoId: String = "",
    val title: String = "",
    val channelName: String = "",
    val durationText: String = "",
    val durationSec: Long = 0L,
    val viewCountText: String = "",
    val publishedTimeText: String = "",
    val thumbnailUrl: String = "",
    val channelAvatarUrl: String = "",
    val liveStream: Boolean = false,
    val musicTrack: Boolean = false,
    val importedFromYouTube: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toMediaItem(): YouTubeMediaItem = YouTubeMediaItem(
        videoId = videoId,
        title = title,
        channelName = channelName,
        durationText = durationText,
        durationSec = durationSec.toInt().coerceAtLeast(0),
        viewCountText = viewCountText,
        publishedTimeText = publishedTimeText,
        thumbnailUrl = thumbnailUrl.ifBlank { "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" },
        channelAvatarUrl = channelAvatarUrl,
        isLive = liveStream,
        isMusic = musicTrack
    )

    fun bucketEnum(): MediaBucket =
        MediaBucket.entries.firstOrNull { it.name == bucket } ?: MediaBucket.LIKED
}

@Keep
@IgnoreExtraProperties
data class CloudPlaylistDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val source: String = "PHOTON",
    val items: List<Map<String, Any?>> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toDomain(): UserPlaylist {
        val parsedItems = items.mapNotNull { raw ->
            val vid = (raw["videoId"] as? String).orEmpty().trim()
            val title = (raw["title"] as? String).orEmpty().trim()
            if (vid.length < 6 || title.isEmpty()) return@mapNotNull null
            YouTubeMediaItem(
                videoId = vid,
                title = title,
                channelName = (raw["channelName"] as? String).orEmpty().ifBlank { "YouTube" },
                durationText = (raw["durationText"] as? String).orEmpty(),
                durationSec = (raw["durationSec"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
                viewCountText = (raw["viewCountText"] as? String).orEmpty(),
                publishedTimeText = (raw["publishedTimeText"] as? String).orEmpty(),
                thumbnailUrl = (raw["thumbnailUrl"] as? String).orEmpty()
                    .ifBlank { "https://i.ytimg.com/vi/$vid/hqdefault.jpg" },
                channelAvatarUrl = (raw["channelAvatarUrl"] as? String).orEmpty(),
                isLive = (raw["liveStream"] as? Boolean) ?: false,
                isMusic = (raw["musicTrack"] as? Boolean) ?: false
            )
        }
        return UserPlaylist(
            id = id,
            name = name,
            items = parsedItems,
            createdAt = createdAt?.toDate()?.time ?: 0L,
            source = source
        )
    }
}

data class SavedMediaSnapshot(
    val liked: List<YouTubeMediaItem> = emptyList(),
    val watchLater: List<YouTubeMediaItem> = emptyList(),
    val history: List<YouTubeMediaItem> = emptyList()
)

class PhotonCloudRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(expectedUid: String): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        check(uid == expectedUid) { "Signed-in user changed; refusing cross-user access." }
        return uid
    }

    fun observeUserProfile(userId: String): Flow<UserAccountProfile?> = flow {
        if (userId.isBlank()) {
            emit(null)
            return@flow
        }
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) null
                    else snapshot.toObject(UserProfileDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?.toDomain()
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(ex, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeSavedItems(userId: String): Flow<SavedMediaSnapshot> = flow {
        if (userId.isBlank()) {
            emit(SavedMediaSnapshot())
            return@flow
        }
        val path = "users/$userId/saved_items"
        emitAll(
            db.collection("users").document(userId).collection("saved_items")
                .snapshots()
                .map { snapshot ->
                    val docs = snapshot.toObjects(
                        SavedMediaDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { (it.updatedAt ?: it.createdAt) ?: Timestamp(0, 0) }

                    SavedMediaSnapshot(
                        liked = docs.filter { it.bucketEnum() == MediaBucket.LIKED }.map { it.toMediaItem() },
                        watchLater = docs.filter { it.bucketEnum() == MediaBucket.WATCH_LATER }.map { it.toMediaItem() },
                        history = docs.filter { it.bucketEnum() == MediaBucket.HISTORY }.map { it.toMediaItem() }.take(40)
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(ex, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observePlaylists(userId: String): Flow<List<UserPlaylist>> = flow {
        if (userId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "users/$userId/playlists"
        emitAll(
            db.collection("users").document(userId).collection("playlists")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CloudPlaylistDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { (it.updatedAt ?: it.createdAt) ?: Timestamp(0, 0) }
                        .map { it.toDomain() }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(ex, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureUserProfile(
        userId: String,
        displayName: String,
        email: String?,
        photoUrl: String?
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            val safeName = displayName.trim().take(100).ifBlank { "Google User" }
            val safeEmail = email?.trim()?.take(200)?.takeIf { it.isNotEmpty() }
            val safePhoto = photoUrl?.trim()?.take(1000)?.takeIf { it.isNotEmpty() }
            val docRef = db.collection("users").document(uid)
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "email" to safeEmail,
                    "photoUrl" to safePhoto,
                    "youtubeConnected" to false,
                    "subscribedChannels" to emptyList<String>(),
                    "favoriteArtists" to emptyList<String>(),
                    "preferredMode" to AppMode.YOUTUBE.name,
                    "autoplayEnabled" to true,
                    "onboardingComplete" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).await()
            } else {
                val updatePayload = mapOf(
                    "displayName" to safeName,
                    "email" to safeEmail,
                    "photoUrl" to safePhoto,
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.update(updatePayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updatePersonalization(
        userId: String,
        youtubeConnected: Boolean,
        youtubeHandle: String?,
        subscribedChannels: List<String>,
        favoriteArtists: List<String>,
        onboardingComplete: Boolean
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            val cleanChannels = subscribedChannels.map { it.trim().take(120) }.filter { it.isNotEmpty() }.distinct().take(40)
            val cleanArtists = favoriteArtists.map { it.trim().take(120) }.filter { it.isNotEmpty() }.distinct().take(40)
            val cleanHandle = youtubeHandle?.trim()?.take(80)?.takeIf { it.isNotEmpty() }
            val updateMap = mutableMapOf<String, Any>(
                "youtubeConnected" to youtubeConnected,
                "subscribedChannels" to cleanChannels,
                "favoriteArtists" to cleanArtists,
                "onboardingComplete" to onboardingComplete,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (cleanHandle != null) {
                updateMap["youtubeHandle"] = cleanHandle
            }
            db.collection("users").document(uid).update(updateMap).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updatePreferences(
        userId: String,
        preferredMode: AppMode? = null,
        autoplayEnabled: Boolean? = null
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            val updateMap = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
            if (preferredMode != null) updateMap["preferredMode"] = preferredMode.name
            if (autoplayEnabled != null) updateMap["autoplayEnabled"] = autoplayEnabled
            db.collection("users").document(uid).update(updateMap).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun upsertSavedItem(
        userId: String,
        bucket: MediaBucket,
        item: YouTubeMediaItem,
        importedFromYouTube: Boolean = false
    ): Result<Unit> {
        val safeVideoId = item.videoId.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.take(32)
        if (safeVideoId.length < 6) return Result.failure(IllegalArgumentException("Invalid video ID"))
        val docId = "${bucket.name}_$safeVideoId"
        val path = "users/$userId/saved_items/$docId"
        return try {
            val uid = requireUserId(userId)
            val docRef = db.collection("users").document(uid).collection("saved_items").document(docId)
            val existing = docRef.get().await()
            val safeTitle = item.title.trim().take(240).ifBlank { "YouTube Video" }
            val safeChannel = item.channelName.trim().take(120).ifBlank { "YouTube" }
            if (!existing.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "bucket" to bucket.name,
                    "videoId" to safeVideoId,
                    "title" to safeTitle,
                    "channelName" to safeChannel,
                    "durationText" to item.durationText.trim().take(32),
                    "durationSec" to item.durationSec.coerceIn(0, 864000).toLong(),
                    "viewCountText" to item.viewCountText.trim().take(64),
                    "publishedTimeText" to item.publishedTimeText.trim().take(64),
                    "thumbnailUrl" to item.highResThumbnailUrl.trim().take(1000),
                    "channelAvatarUrl" to item.channelAvatarUrl.trim().take(1000),
                    "liveStream" to item.isLive,
                    "musicTrack" to item.isMusic,
                    "importedFromYouTube" to importedFromYouTube,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                val updatePayload = mapOf(
                    "title" to safeTitle,
                    "channelName" to safeChannel,
                    "durationText" to item.durationText.trim().take(32),
                    "durationSec" to item.durationSec.coerceIn(0, 864000).toLong(),
                    "viewCountText" to item.viewCountText.trim().take(64),
                    "publishedTimeText" to item.publishedTimeText.trim().take(64),
                    "thumbnailUrl" to item.highResThumbnailUrl.trim().take(1000),
                    "channelAvatarUrl" to item.channelAvatarUrl.trim().take(1000),
                    "liveStream" to item.isLive,
                    "musicTrack" to item.isMusic,
                    "importedFromYouTube" to importedFromYouTube,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updatePayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun removeSavedItem(
        userId: String,
        bucket: MediaBucket,
        videoId: String
    ): Result<Unit> {
        val safeVideoId = videoId.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.take(32)
        val docId = "${bucket.name}_$safeVideoId"
        val path = "users/$userId/saved_items/$docId"
        return try {
            val uid = requireUserId(userId)
            db.collection("users").document(uid)
                .collection("saved_items").document(docId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun upsertPlaylist(
        userId: String,
        playlistId: String,
        name: String,
        items: List<YouTubeMediaItem>,
        source: String = "PHOTON"
    ): Result<Unit> {
        val safeId = playlistId.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.take(128)
        if (safeId.isBlank()) return Result.failure(IllegalArgumentException("Invalid playlist ID"))
        val path = "users/$userId/playlists/$safeId"
        return try {
            val uid = requireUserId(userId)
            val safeName = name.trim().take(120).ifBlank { "Untitled Playlist" }
            val safeSource = if (source in setOf("PHOTON", "YOUTUBE", "YT_MUSIC")) source else "PHOTON"
            val encodedItems = items.take(50).map { it.toCloudItemMap() }
            val docRef = db.collection("users").document(uid).collection("playlists").document(safeId)
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "name" to safeName,
                    "source" to safeSource,
                    "items" to encodedItems,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                val updatePayload = mapOf(
                    "name" to safeName,
                    "source" to safeSource,
                    "items" to encodedItems,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updatePayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    private fun YouTubeMediaItem.toCloudItemMap(): Map<String, Any> = mapOf(
        "videoId" to videoId.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.take(32),
        "title" to title.trim().take(240),
        "channelName" to channelName.trim().take(120),
        "durationText" to durationText.trim().take(32),
        "durationSec" to durationSec.coerceIn(0, 864000).toLong(),
        "viewCountText" to viewCountText.trim().take(64),
        "publishedTimeText" to publishedTimeText.trim().take(64),
        "thumbnailUrl" to highResThumbnailUrl.trim().take(1000),
        "channelAvatarUrl" to channelAvatarUrl.trim().take(1000),
        "liveStream" to isLive,
        "musicTrack" to isMusic
    )
}

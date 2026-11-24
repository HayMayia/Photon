package com.example.data

import androidx.compose.runtime.Immutable

enum class AppMode(val label: String) {
    YOUTUBE("YouTube"),
    MUSIC("Music")
}

enum class PlayerDisplayMode(val label: String) {
    VIDEO("Video"),
    AUDIO("Audio")
}

enum class YouTubeCategory(
    val label: String,
    val searchQuery: String,
    val liveOnly: Boolean = false
) {
    FOR_YOU("For You", "trending popular videos 2026"),
    SUBSCRIPTIONS("Subscriptions", "new uploads popular creators 2026"),
    TRENDING("Trending", "viral trending videos this week"),
    MUSIC("Music", "official music video 2026"),
    GAMING("Gaming", "gaming review gameplay 2026"),
    TECH("Tech", "tech review smartphones laptops 2026"),
    PODCASTS("Podcasts", "full podcast interview episode 2026"),
    SCIENCE("Science", "science documentary space physics"),
    LIVE("Live", "live stream music news radio", liveOnly = true)
}

enum class MusicCategory(val label: String, val searchQuery: String) {
    FOR_YOU("For You", "top songs official audio 2026"),
    YOUR_MIX("Your Mix", "recommended songs official audio 2026"),
    TOP_TRACKS("Top Tracks", "top hits music official audio 2026"),
    CHILL_LOFI("Chill & Lo-Fi", "lofi hip hop chill beats music"),
    ELECTRONIC("Electronic", "electronic ambient melodic official audio"),
    ACOUSTIC("Acoustic", "acoustic indie folk songs official audio"),
    FOCUS("Focus", "ambient deep focus instrumental piano music"),
    PODCASTS("Podcasts", "music podcast interview session")
}

enum class SearchFilter(val label: String) {
    ALL("All"),
    VIDEOS("Videos"),
    MUSIC("Music"),
    PODCASTS("Podcasts"),
    LIVE("Live")
}

enum class LibraryFilter(val label: String) {
    ALL("All"),
    WATCH_LATER("Watch Later"),
    LIKED("Liked"),
    PLAYLISTS("Playlists"),
    HISTORY("History")
}

enum class LibrarySort(val label: String) {
    RECENT("Recent"),
    ALPHABETICAL("A–Z")
}

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

enum class SleepTimerOption(val label: String, val minutes: Int?) {
    OFF("Off", null),
    FIFTEEN("15 minutes", 15),
    THIRTY("30 minutes", 30),
    FORTY_FIVE("45 minutes", 45),
    SIXTY("60 minutes", 60)
}

enum class MediaBucket {
    LIKED,
    WATCH_LATER,
    HISTORY
}

@Immutable
data class UserAccountProfile(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val youtubeConnected: Boolean = false,
    val youtubeHandle: String = "",
    val subscribedChannels: List<String> = emptyList(),
    val favoriteArtists: List<String> = emptyList(),
    val preferredMode: AppMode = AppMode.YOUTUBE,
    val autoplayEnabled: Boolean = true,
    val onboardingComplete: Boolean = false
) {
    val hasPersonalizationSignals: Boolean
        get() = youtubeConnected || subscribedChannels.isNotEmpty() || favoriteArtists.isNotEmpty()
}

@Immutable
data class YouTubeAccountImportResult(
    val accountHandle: String = "",
    val youtubeHomeItems: List<YouTubeMediaItem> = emptyList(),
    val subscriptionItems: List<YouTubeMediaItem> = emptyList(),
    val likedVideos: List<YouTubeMediaItem> = emptyList(),
    val ytMusicHomeItems: List<YouTubeMediaItem> = emptyList(),
    val ytMusicLikedSongs: List<YouTubeMediaItem> = emptyList(),
    val discoveredChannels: List<String> = emptyList(),
    val discoveredArtists: List<String> = emptyList()
)

@Immutable
data class PlaybackProgress(
    val positionSec: Int = 0,
    val durationSec: Int = 0
) {
    val fraction: Float
        get() = if (durationSec <= 0) 0f else (positionSec.toFloat() / durationSec.toFloat()).coerceIn(0f, 1f)
}

@Immutable
sealed interface FeedLoadState {
    data object Loading : FeedLoadState

    @Immutable
    data class Success(val items: List<YouTubeMediaItem>) : FeedLoadState

    @Immutable
    data class Error(val message: String) : FeedLoadState
}

@Immutable
data class YouTubeMediaItem(
    val videoId: String,
    val title: String,
    val channelName: String,
    val durationText: String = "",
    val durationSec: Int = 0,
    val viewCountText: String = "",
    val publishedTimeText: String = "",
    val thumbnailUrl: String = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
    val channelAvatarUrl: String = "",
    val isLive: Boolean = false,
    val isMusic: Boolean = false
) {
    val highResThumbnailUrl: String
        get() = thumbnailUrl.ifBlank { "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" }

    val metadataLine: String
        get() = buildList {
            if (channelName.isNotBlank()) add(channelName)
            if (viewCountText.isNotBlank()) add(viewCountText)
            if (publishedTimeText.isNotBlank()) add(publishedTimeText)
        }.joinToString(" · ")
}

@Immutable
data class UserPlaylist(
    val id: String,
    val name: String,
    val items: List<YouTubeMediaItem>,
    val createdAt: Long,
    val source: String = "PHOTON"
)

@Immutable
data class ExploreTopic(
    val id: String,
    val title: String,
    val subtitle: String,
    val query: String
)

val SuggestedYouTubeChannels = listOf(
    "MKBHD",
    "Veritasium",
    "Kurzgesagt",
    "Fireship",
    "Linus Tech Tips",
    "3Blue1Brown",
    "MrBeast",
    "NPR Music",
    "Johnny Harris",
    "Cleo Abram"
)

val SuggestedMusicArtists = listOf(
    "The Weeknd",
    "Daft Punk",
    "Tame Impala",
    "Billie Eilish",
    "Fred again..",
    "Kendrick Lamar",
    "Frank Ocean",
    "Lofi Girl",
    "Nujabes",
    "SZA"
)

val YouTubeExploreTopics = listOf(
    ExploreTopic("exp_tech", "Tech & Gear", "Reviews, breakdowns, launches", "tech review 2026"),
    ExploreTopic("exp_music", "New Music", "Official videos & audio", "new music official video 2026"),
    ExploreTopic("exp_science", "Science & Space", "Explainers & documentaries", "science space documentary"),
    ExploreTopic("exp_gaming", "Gaming", "Trailers, gameplay & essays", "video game review essay"),
    ExploreTopic("exp_podcasts", "Long-Form Podcasts", "Conversations & interviews", "full podcast episode interview"),
    ExploreTopic("exp_film", "Cinema & Design", "Video essays & craft", "film design video essay")
)

val MusicExploreTopics = listOf(
    ExploreTopic("mus_lofi", "Lo-Fi & Chill", "Beats for study & focus", "lofi chill beats instrumental"),
    ExploreTopic("mus_ambient", "Ambient", "Atmospheric & minimal", "ambient electronic music album"),
    ExploreTopic("mus_indie", "Indie & Alternative", "Fresh official audio", "indie alternative official audio"),
    ExploreTopic("mus_jazz", "Late Night Jazz", "Warm acoustic sessions", "late night jazz session"),
    ExploreTopic("mus_piano", "Modern Piano", "Solo piano & classical", "modern solo piano instrumental"),
    ExploreTopic("mus_synth", "Electronic", "Deep house & melodic synth", "melodic electronic official audio")
)

fun formatDuration(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    val seconds = safe % 60
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}

fun parseDurationToSeconds(text: String): Int {
    val clean = text.trim()
    if (clean.isEmpty() || clean.equals("LIVE", ignoreCase = true)) return 0
    val parts = clean.split(":").mapNotNull { it.trim().toIntOrNull() }
    return when (parts.size) {
        3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
        2 -> parts[0] * 60 + parts[1]
        1 -> parts[0]
        else -> 0
    }
}

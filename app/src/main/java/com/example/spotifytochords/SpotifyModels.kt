package com.example.spotifytochords

data class TrackInfo(
    val id: String,
    val name: String,
    val artists: List<String>,
    val album: String?
)

data class AudioAnalysis(
    val bars: List<TimedElement>,
    val segments: List<AudioSegment>
)

data class TimedElement(
    val startSec: Double,
    val durationSec: Double,
    val confidence: Double
)

data class AudioSegment(
    val startSec: Double,
    val durationSec: Double,
    val confidence: Double,
    val pitches: DoubleArray
)

data class AudioFeatures(
    val id: String? = null,
    val key: Int,
    val mode: Int,
    val tempo: Double,
    val timeSignature: Int
)

data class ChordTimelineEntry(
    val startSec: Double,
    val endSec: Double,
    val chord: String
)

data class ChordProgression(
    val timeline: List<ChordTimelineEntry>,
    val compactProgression: String,
    val keyLabel: String?,
    val tempoBpm: Double?,
    val timeSignature: Int?
)

data class SearchTrack(
    val id: String,
    val name: String,
    val artist: String,
    val album: String?,
    val albumImageUrl: String?,
    val previewUrl: String?,
    val keyLabel: String? = null,
    val tempoBpm: Double? = null
)

data class SearchArtist(
    val id: String,
    val name: String,
    val imageUrl: String?,
    val followers: Int? = null
)

data class SearchAlbum(
    val id: String,
    val name: String,
    val artist: String,
    val imageUrl: String?,
    val releaseDate: String? = null,
    val totalTracks: Int? = null
)

sealed class SearchListItem {
    data class TrackItem(val track: SearchTrack) : SearchListItem()
    data class ArtistItem(val artist: SearchArtist) : SearchListItem()
    data class AlbumItem(val album: SearchAlbum) : SearchListItem()
}

data class FeedPost(
    val username: String,
    val postedAgo: String,
    val likeCount: Int,
    val commentCount: Int,
    val saved: Boolean,
    val track: SearchTrack
)

data class PlayerTrackData(
    val track: SearchTrack,
    val progression: ChordProgression
)

data class SpotifyAuthToken(
    val accessToken: String,
    val refreshToken: String?,
    val expiresInSec: Int
)

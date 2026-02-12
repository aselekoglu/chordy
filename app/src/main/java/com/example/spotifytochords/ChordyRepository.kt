package com.example.spotifytochords

import android.content.Context
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.absoluteValue

object ChordyRepository {
    private const val preferencesFile = "spotify_credentials"
    private const val prefClientId = "pref_client_id"
    private const val prefClientSecret = "pref_client_secret"

    private var cachedToken: String? = null
    private var tokenTimestampMs: Long = 0L

    private val playerCache = ConcurrentHashMap<String, PlayerTrackData>()
    private val savedTracks = LinkedHashMap<String, SearchTrack>()

    fun hasCredentials(context: Context): Boolean {
        val (clientId, clientSecret) = getCredentials(context)
        return clientId.isNotBlank() && clientSecret.isNotBlank()
    }

    fun saveCredentials(context: Context, clientId: String, clientSecret: String) {
        context.getSharedPreferences(preferencesFile, Context.MODE_PRIVATE)
            .edit()
            .putString(prefClientId, clientId.trim())
            .putString(prefClientSecret, clientSecret.trim())
            .apply()
        cachedToken = null
        tokenTimestampMs = 0L
    }

    fun getCredentials(context: Context): Pair<String, String> {
        val preferences = context.getSharedPreferences(preferencesFile, Context.MODE_PRIVATE)
        val savedClientId = preferences.getString(prefClientId, "").orEmpty()
        val savedClientSecret = preferences.getString(prefClientSecret, "").orEmpty()
        val clientId = if (savedClientId.isNotBlank()) savedClientId else BuildConfig.SPOTIFY_CLIENT_ID
        val clientSecret = if (savedClientSecret.isNotBlank()) savedClientSecret else BuildConfig.SPOTIFY_CLIENT_SECRET
        return clientId to clientSecret
    }

    fun loadHomePosts(context: Context): List<FeedPost> {
        val tracks = searchTracks(context, "top hits", limit = 12)
        if (tracks.isEmpty()) return emptyList()
        val usernames = listOf("aselekoglu", "zz.drummer", "abel.tesfaye", "jenny.d", "adeola.plays", "theoryguy")
        return tracks.mapIndexed { index, track ->
            val likes = (track.id.hashCode().absoluteValue % 90) + 7
            val comments = (track.id.hashCode().absoluteValue % 8) + 1
            val username = usernames[index % usernames.size]
            val hours = (index % 6) + 1
            FeedPost(
                username = username,
                postedAgo = if (hours == 1) "$hours hour ago" else "$hours hours ago",
                likeCount = likes,
                commentCount = comments,
                saved = index % 3 == 1,
                track = track
            )
        }
    }

    fun searchTracks(context: Context, query: String, limit: Int = 15): List<SearchTrack> {
        val token = getAccessToken(context)
        val client = buildClient(context)
        val tracks = client.searchTracks(token, query, limit)
        if (tracks.isEmpty()) return tracks
        val featuresById = client.getAudioFeaturesBatch(token, tracks.map { it.id })
        return tracks.map { track ->
            val features = featuresById[track.id]
            val keyLabel = formatKey(features)
            track.copy(keyLabel = keyLabel, tempoBpm = features?.tempo?.takeIf { it.isFinite() })
        }
    }

    fun loadPlayerData(context: Context, track: SearchTrack): PlayerTrackData {
        val cached = playerCache[track.id]
        if (cached != null) return cached

        val token = getAccessToken(context)
        val client = buildClient(context)
        val analysis = client.getAudioAnalysis(token, track.id)
        val features = client.getAudioFeatures(token, track.id)
        val progression = ChordEstimator.estimate(analysis, features)
        val enrichedTrack = track.copy(
            keyLabel = progression.keyLabel ?: track.keyLabel,
            tempoBpm = progression.tempoBpm ?: track.tempoBpm
        )
        val playerData = PlayerTrackData(enrichedTrack, progression)
        playerCache[track.id] = playerData
        return playerData
    }

    fun addToLibrary(track: SearchTrack) {
        savedTracks[track.id] = track
    }

    fun getLibraryTracks(): List<SearchTrack> {
        return savedTracks.values.toList()
    }

    private fun getAccessToken(context: Context): String {
        if (cachedToken != null && System.currentTimeMillis() - tokenTimestampMs < 50 * 60 * 1000) {
            return cachedToken.orEmpty()
        }
        val client = buildClient(context)
        val token = client.requestAccessToken()
        cachedToken = token
        tokenTimestampMs = System.currentTimeMillis()
        return token
    }

    private fun buildClient(context: Context): SpotifyApiClient {
        val (clientId, clientSecret) = getCredentials(context)
        if (clientId.isBlank() || clientSecret.isBlank()) {
            throw IllegalStateException("Spotify credentials are missing.")
        }
        return SpotifyApiClient(clientId, clientSecret)
    }

    private fun formatKey(features: AudioFeatures?): String? {
        features ?: return null
        if (features.key !in 0..11) return null
        val note = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")[features.key]
        val mode = if (features.mode == 1) "maj" else "min"
        return "$note $mode"
    }
}

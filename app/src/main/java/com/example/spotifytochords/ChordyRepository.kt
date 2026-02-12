package com.example.spotifytochords

import android.content.Context
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.absoluteValue

object ChordyRepository {
    private const val tag = "ChordyRepository"

    private const val authPreferencesFile = "spotify_auth"
    private const val prefAccessToken = "pref_access_token"
    private const val prefRefreshToken = "pref_refresh_token"
    private const val prefExpiresAtMs = "pref_expires_at_ms"

    private var cachedToken: String? = null
    private var cachedRefreshToken: String? = null
    private var tokenExpiryTimestampMs: Long = 0L

    private val playerCache = ConcurrentHashMap<String, PlayerTrackData>()
    private val savedTracks = LinkedHashMap<String, SearchTrack>()

    fun hasCredentials(context: Context): Boolean {
        return hasSpotifySession(context)
    }

    fun hasSpotifySession(context: Context): Boolean {
        val preferences = context.getSharedPreferences(authPreferencesFile, Context.MODE_PRIVATE)
        val accessToken = preferences.getString(prefAccessToken, "").orEmpty()
        val refreshToken = preferences.getString(prefRefreshToken, "").orEmpty()
        return accessToken.isNotBlank() || refreshToken.isNotBlank()
    }

    fun clearSpotifySession(context: Context) {
        context.getSharedPreferences(authPreferencesFile, Context.MODE_PRIVATE)
            .edit()
            .remove(prefAccessToken)
            .remove(prefRefreshToken)
            .remove(prefExpiresAtMs)
            .apply()
        invalidateTokenCache()
        playerCache.clear()
    }

    fun saveSpotifyAuthToken(context: Context, token: SpotifyAuthToken) {
        val refreshToken = token.refreshToken ?: cachedRefreshToken.orEmpty()
        saveSpotifyAuthToken(context, token.accessToken, refreshToken, token.expiresInSec)
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
        val client = buildClient()
        val tracks = withTokenRetry(context) { token ->
            client.searchTracks(token, query, limit)
        }
        if (tracks.isEmpty()) return tracks

        val featuresById = try {
            withTokenRetry(context) { token ->
                client.getAudioFeaturesBatch(token, tracks.map { it.id })
            }
        } catch (exception: Exception) {
            Log.w(tag, "Audio features unavailable for track search; returning plain track results.", exception)
            emptyMap()
        }

        return tracks.map { track ->
            val features = featuresById[track.id]
            val keyLabel = formatKey(features)
            track.copy(keyLabel = keyLabel, tempoBpm = features?.tempo?.takeIf { it.isFinite() })
        }
    }

    fun searchArtists(context: Context, query: String, limit: Int = 20): List<SearchArtist> {
        val client = buildClient()
        return withTokenRetry(context) { token ->
            client.searchArtists(token, query, limit)
        }
    }

    fun searchAlbums(context: Context, query: String, limit: Int = 20): List<SearchAlbum> {
        val client = buildClient()
        return withTokenRetry(context) { token ->
            client.searchAlbums(token, query, limit)
        }
    }

    fun loadPlayerData(context: Context, track: SearchTrack): PlayerTrackData {
        val cached = playerCache[track.id]
        if (cached != null) return cached

        val client = buildClient()
        val progression = withTokenRetry(context) { token ->
            val analysis = client.getAudioAnalysis(token, track.id)
            val features = client.getAudioFeatures(token, track.id)
            ChordEstimator.estimate(analysis, features)
        }

        val enrichedTrack = track.copy(
            keyLabel = progression.keyLabel ?: track.keyLabel,
            tempoBpm = progression.tempoBpm ?: track.tempoBpm
        )
        return PlayerTrackData(enrichedTrack, progression).also {
            playerCache[track.id] = it
        }
    }

    fun addToLibrary(track: SearchTrack) {
        savedTracks[track.id] = track
    }

    fun getLibraryTracks(): List<SearchTrack> {
        return savedTracks.values.toList()
    }

    private fun getAccessToken(context: Context): String {
        val now = System.currentTimeMillis()
        if (!cachedToken.isNullOrBlank() && now < tokenExpiryTimestampMs - 30_000L) {
            return cachedToken.orEmpty()
        }

        val preferences = context.getSharedPreferences(authPreferencesFile, Context.MODE_PRIVATE)
        val storedAccessToken = preferences.getString(prefAccessToken, "").orEmpty()
        val storedRefreshToken = preferences.getString(prefRefreshToken, "").orEmpty()
        val storedExpiryMs = preferences.getLong(prefExpiresAtMs, 0L)

        if (storedAccessToken.isNotBlank() && now < storedExpiryMs - 30_000L) {
            cachedToken = storedAccessToken
            cachedRefreshToken = storedRefreshToken.ifBlank { null }
            tokenExpiryTimestampMs = storedExpiryMs
            return storedAccessToken
        }

        if (storedRefreshToken.isBlank()) {
            throw IllegalStateException("Spotify login required.")
        }

        return try {
            val refreshed = refreshAccessToken(context, storedRefreshToken)
            refreshed.accessToken
        } catch (exception: SpotifyApiException) {
            if (!isSessionExpiredResponse(exception)) throw exception
            Log.w(tag, "Stored Spotify session is no longer valid. Clearing session.", exception)
            clearSpotifySession(context)
            throw IllegalStateException("Spotify session expired. Please log in again.")
        }
    }

    private fun refreshAccessToken(context: Context, refreshToken: String): SpotifyAuthToken {
        val token = buildClient().refreshUserAccessToken(refreshToken)
        val mergedRefreshToken = token.refreshToken ?: refreshToken
        saveSpotifyAuthToken(context, token.accessToken, mergedRefreshToken, token.expiresInSec)
        return token.copy(refreshToken = mergedRefreshToken)
    }

    private fun invalidateTokenCache() {
        cachedToken = null
        cachedRefreshToken = null
        tokenExpiryTimestampMs = 0L
    }

    private fun <T> withTokenRetry(context: Context, action: (String) -> T): T {
        val firstToken = getAccessToken(context)
        return try {
            action(firstToken)
        } catch (exception: SpotifyApiException) {
            if (exception.statusCode != 401) throw exception
            Log.w(tag, "Spotify token unauthorized. Refreshing token and retrying once.", exception)
            val preferences = context.getSharedPreferences(authPreferencesFile, Context.MODE_PRIVATE)
            val refreshToken = preferences.getString(prefRefreshToken, "").orEmpty()
            if (refreshToken.isBlank()) {
                clearSpotifySession(context)
                throw IllegalStateException("Spotify session expired. Please log in again.")
            }
            val refreshed = try {
                refreshAccessToken(context, refreshToken)
            } catch (refreshException: SpotifyApiException) {
                if (!isSessionExpiredResponse(refreshException)) throw refreshException
                clearSpotifySession(context)
                throw IllegalStateException("Spotify session expired. Please log in again.")
            }
            action(refreshed.accessToken)
        }
    }

    private fun buildClient(): SpotifyApiClient {
        val clientId = BuildConfig.SPOTIFY_CLIENT_ID.trim()
        if (clientId.isBlank()) {
            throw IllegalStateException("Spotify client ID is missing. Set SPOTIFY_CLIENT_ID.")
        }
        return SpotifyApiClient(clientId, BuildConfig.SPOTIFY_CLIENT_SECRET)
    }

    private fun saveSpotifyAuthToken(context: Context, accessToken: String, refreshToken: String?, expiresInSec: Int) {
        val expiryMs = System.currentTimeMillis() + (expiresInSec.coerceAtLeast(1) * 1000L)
        context.getSharedPreferences(authPreferencesFile, Context.MODE_PRIVATE)
            .edit()
            .putString(prefAccessToken, accessToken)
            .putString(prefRefreshToken, refreshToken)
            .putLong(prefExpiresAtMs, expiryMs)
            .apply()

        cachedToken = accessToken
        cachedRefreshToken = refreshToken
        tokenExpiryTimestampMs = expiryMs
    }

    private fun formatKey(features: AudioFeatures?): String? {
        features ?: return null
        if (features.key !in 0..11) return null
        val note = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")[features.key]
        val mode = if (features.mode == 1) "maj" else "min"
        return "$note $mode"
    }

    private fun isSessionExpiredResponse(exception: SpotifyApiException): Boolean {
        if (exception.statusCode == 401) return true
        if (exception.statusCode == 400 && exception.message?.contains("invalid_grant", ignoreCase = true) == true) {
            return true
        }
        return false
    }
}

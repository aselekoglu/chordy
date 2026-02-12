package com.example.spotifytochords

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import kotlin.math.min

class SpotifyApiClient(
    private val clientId: String,
    private val clientSecret: String
) {

    fun requestAccessToken(): String {
        val credentials = "$clientId:$clientSecret"
        val basicToken = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
        val response = performRequest(
            method = "POST",
            url = "https://accounts.spotify.com/api/token",
            body = "grant_type=client_credentials",
            extraHeaders = mapOf(
                "Authorization" to "Basic $basicToken",
                "Content-Type" to "application/x-www-form-urlencoded"
            )
        )
        val json = JSONObject(response)
        return json.optString("access_token")
            .takeIf { it.isNotBlank() }
            ?: throw IOException("Spotify access token was empty.")
    }

    fun getTrackInfo(accessToken: String, trackId: String): TrackInfo {
        val response = performRequest(
            method = "GET",
            url = "https://api.spotify.com/v1/tracks/$trackId",
            bearerToken = accessToken
        )
        val json = JSONObject(response)
        val artistsArray = json.optJSONArray("artists") ?: JSONArray()
        val artists = mutableListOf<String>()
        for (index in 0 until artistsArray.length()) {
            artists += artistsArray.optJSONObject(index)?.optString("name").orEmpty()
        }
        return TrackInfo(
            id = json.optString("id", trackId),
            name = json.optString("name", "Unknown Track"),
            artists = artists.filter { it.isNotBlank() },
            album = json.optJSONObject("album")?.optString("name")?.takeIf { it.isNotBlank() }
        )
    }

    fun getAudioAnalysis(accessToken: String, trackId: String): AudioAnalysis {
        val response = performRequest(
            method = "GET",
            url = "https://api.spotify.com/v1/audio-analysis/$trackId",
            bearerToken = accessToken
        )
        val json = JSONObject(response)
        val bars = parseTimedElements(json.optJSONArray("bars"))
        val segmentsArray = json.optJSONArray("segments") ?: JSONArray()
        val segments = ArrayList<AudioSegment>(segmentsArray.length())
        for (index in 0 until segmentsArray.length()) {
            val segmentJson = segmentsArray.optJSONObject(index) ?: continue
            val pitchesJson = segmentJson.optJSONArray("pitches") ?: JSONArray()
            val pitches = DoubleArray(12)
            for (pitchIndex in 0 until min(12, pitchesJson.length())) {
                pitches[pitchIndex] = pitchesJson.optDouble(pitchIndex, 0.0)
            }
            segments += AudioSegment(
                startSec = segmentJson.optDouble("start", 0.0),
                durationSec = segmentJson.optDouble("duration", 0.0),
                confidence = segmentJson.optDouble("confidence", 0.0),
                pitches = pitches
            )
        }
        return AudioAnalysis(bars = bars, segments = segments)
    }

    fun getAudioFeatures(accessToken: String, trackId: String): AudioFeatures {
        val response = performRequest(
            method = "GET",
            url = "https://api.spotify.com/v1/audio-features/$trackId",
            bearerToken = accessToken
        )
        val json = JSONObject(response)
        return AudioFeatures(
            id = json.optString("id", trackId),
            key = json.optInt("key", -1),
            mode = json.optInt("mode", -1),
            tempo = json.optDouble("tempo", Double.NaN),
            timeSignature = json.optInt("time_signature", -1)
        )
    }

    fun getAudioFeaturesBatch(accessToken: String, trackIds: List<String>): Map<String, AudioFeatures> {
        if (trackIds.isEmpty()) return emptyMap()
        val joined = trackIds.joinToString(",")
        val response = performRequest(
            method = "GET",
            url = "https://api.spotify.com/v1/audio-features?ids=$joined",
            bearerToken = accessToken
        )
        val json = JSONObject(response)
        val featuresArray = json.optJSONArray("audio_features") ?: JSONArray()
        val map = LinkedHashMap<String, AudioFeatures>()
        for (index in 0 until featuresArray.length()) {
            val featureJson = featuresArray.optJSONObject(index) ?: continue
            val id = featureJson.optString("id").orEmpty()
            if (id.isBlank()) continue
            map[id] = AudioFeatures(
                id = id,
                key = featureJson.optInt("key", -1),
                mode = featureJson.optInt("mode", -1),
                tempo = featureJson.optDouble("tempo", Double.NaN),
                timeSignature = featureJson.optInt("time_signature", -1)
            )
        }
        return map
    }

    fun searchTracks(accessToken: String, query: String, limit: Int = 15): List<SearchTrack> {
        val encodedQuery = URLEncoder.encode(query, Charsets.UTF_8.name())
        val response = performRequest(
            method = "GET",
            url = "https://api.spotify.com/v1/search?type=track&market=US&limit=$limit&q=$encodedQuery",
            bearerToken = accessToken
        )
        val root = JSONObject(response)
        val tracksArray = root.optJSONObject("tracks")?.optJSONArray("items") ?: JSONArray()
        val tracks = mutableListOf<SearchTrack>()
        for (index in 0 until tracksArray.length()) {
            val trackJson = tracksArray.optJSONObject(index) ?: continue
            val id = trackJson.optString("id").orEmpty()
            if (id.isBlank()) continue
            val artistsArray = trackJson.optJSONArray("artists") ?: JSONArray()
            val firstArtist = artistsArray.optJSONObject(0)?.optString("name").orEmpty()
            val albumJson = trackJson.optJSONObject("album")
            val album = albumJson?.optString("name")?.takeIf { it.isNotBlank() }
            val images = albumJson?.optJSONArray("images") ?: JSONArray()
            val imageUrl = images.optJSONObject(1)?.optString("url")
                ?: images.optJSONObject(0)?.optString("url")
            tracks += SearchTrack(
                id = id,
                name = trackJson.optString("name", "Unknown"),
                artist = firstArtist.ifBlank { "Unknown Artist" },
                album = album,
                albumImageUrl = imageUrl,
                previewUrl = trackJson.optString("preview_url").takeIf { !it.isNullOrBlank() }
            )
        }
        return tracks
    }

    private fun parseTimedElements(array: JSONArray?): List<TimedElement> {
        if (array == null) return emptyList()
        val list = ArrayList<TimedElement>(array.length())
        for (index in 0 until array.length()) {
            val json = array.optJSONObject(index) ?: continue
            list += TimedElement(
                startSec = json.optDouble("start", 0.0),
                durationSec = json.optDouble("duration", 0.0),
                confidence = json.optDouble("confidence", 0.0)
            )
        }
        return list
    }

    private fun performRequest(
        method: String,
        url: String,
        bearerToken: String? = null,
        body: String? = null,
        extraHeaders: Map<String, String> = emptyMap()
    ): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 25_000
            doInput = true
            if (body != null) {
                doOutput = true
            }
            if (bearerToken != null) {
                setRequestProperty("Authorization", "Bearer $bearerToken")
            }
            extraHeaders.forEach { (key, value) -> setRequestProperty(key, value) }
        }

        try {
            if (body != null) {
                connection.outputStream.use { output ->
                    output.write(body.toByteArray(Charsets.UTF_8))
                }
            }
            val responseCode = connection.responseCode
            val responseBody = readResponseBody(connection)
            if (responseCode !in 200..299) {
                val message = parseApiError(responseBody).ifBlank { "HTTP $responseCode" }
                throw IOException("Spotify API request failed ($responseCode): $message")
            }
            return responseBody
        } finally {
            connection.disconnect()
        }
    }

    private fun readResponseBody(connection: HttpURLConnection): String {
        val stream = connection.errorStream ?: connection.inputStream
        return stream?.use { it.readTextUtf8() }.orEmpty()
    }

    private fun parseApiError(body: String): String {
        if (body.isBlank()) return ""
        return try {
            val json = JSONObject(body)
            when {
                json.has("error_description") -> json.optString("error_description")
                json.has("error") && json.opt("error") is JSONObject -> {
                    json.optJSONObject("error")?.optString("message").orEmpty()
                }
                json.has("error") -> json.optString("error")
                else -> body
            }
        } catch (_: Exception) {
            body
        }
    }

    private fun InputStream.readTextUtf8(): String {
        return bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}

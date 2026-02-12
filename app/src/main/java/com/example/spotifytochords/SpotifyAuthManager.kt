package com.example.spotifytochords

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

object SpotifyAuthManager {
    private const val authorizeBaseUrl = "https://accounts.spotify.com/authorize"
    private const val flowPreferencesFile = "spotify_auth_flow"
    private const val prefCodeVerifier = "pref_code_verifier"
    private const val prefState = "pref_state"

    fun startLogin(activity: Activity) {
        val clientId = BuildConfig.SPOTIFY_CLIENT_ID.trim()
        if (clientId.isBlank()) {
            throw IllegalStateException("Spotify client ID is missing. Set SPOTIFY_CLIENT_ID.")
        }

        val codeVerifier = generateCodeVerifier()
        val codeChallenge = sha256Base64Url(codeVerifier)
        val state = randomToken(16)

        activity.getSharedPreferences(flowPreferencesFile, Context.MODE_PRIVATE)
            .edit()
            .putString(prefCodeVerifier, codeVerifier)
            .putString(prefState, state)
            .apply()

        val authorizeUriBuilder = Uri.parse(authorizeBaseUrl).buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", BuildConfig.SPOTIFY_REDIRECT_URI)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("state", state)
            .appendQueryParameter("show_dialog", "true")

        activity.startActivity(Intent(Intent.ACTION_VIEW, authorizeUriBuilder.build()))
    }

    fun completeLogin(context: Context, callbackUri: Uri): SpotifyAuthToken {
        val expectedRedirect = Uri.parse(BuildConfig.SPOTIFY_REDIRECT_URI)
        if (
            callbackUri.scheme != expectedRedirect.scheme ||
            callbackUri.host != expectedRedirect.host ||
            callbackUri.path != expectedRedirect.path
        ) {
            throw IllegalStateException("Unexpected Spotify callback URI.")
        }

        val preferences = context.getSharedPreferences(flowPreferencesFile, Context.MODE_PRIVATE)
        val expectedState = preferences.getString(prefState, "").orEmpty()
        val codeVerifier = preferences.getString(prefCodeVerifier, "").orEmpty()

        preferences.edit()
            .remove(prefState)
            .remove(prefCodeVerifier)
            .apply()

        val returnedState = callbackUri.getQueryParameter("state").orEmpty()
        val error = callbackUri.getQueryParameter("error")
        if (!error.isNullOrBlank()) {
            throw IllegalStateException("Spotify login failed: $error")
        }
        if (expectedState.isBlank() || codeVerifier.isBlank() || returnedState != expectedState) {
            throw IllegalStateException("Spotify login validation failed. Please try again.")
        }

        val code = callbackUri.getQueryParameter("code").orEmpty()
        if (code.isBlank()) {
            throw IllegalStateException("Spotify login did not return an authorization code.")
        }

        val client = SpotifyApiClient(
            clientId = BuildConfig.SPOTIFY_CLIENT_ID.trim(),
            clientSecret = BuildConfig.SPOTIFY_CLIENT_SECRET
        )
        return client.exchangeAuthorizationCode(
            code = code,
            redirectUri = BuildConfig.SPOTIFY_REDIRECT_URI,
            codeVerifier = codeVerifier
        )
    }

    private fun generateCodeVerifier(): String {
        return randomToken(64)
    }

    private fun randomToken(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(
            bytes,
            Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE
        )
    }

    private fun sha256Base64Url(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(
            digest,
            Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE
        )
    }
}

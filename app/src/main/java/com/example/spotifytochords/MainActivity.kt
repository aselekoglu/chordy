package com.example.spotifytochords

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.io.IOException
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private val workerExecutor = Executors.newSingleThreadExecutor()

    private lateinit var layoutClientId: TextInputLayout
    private lateinit var layoutClientSecret: TextInputLayout
    private lateinit var layoutTrackInput: TextInputLayout
    private lateinit var editClientId: TextInputEditText
    private lateinit var editClientSecret: TextInputEditText
    private lateinit var editTrackInput: TextInputEditText
    private lateinit var buttonFetchChords: MaterialButton
    private lateinit var progressBar: ProgressBar
    private lateinit var textStatus: TextView
    private lateinit var textTrackInfo: TextView
    private lateinit var textChordOutput: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()
        prefillCredentialInputs()
        buttonFetchChords.setOnClickListener { fetchAndEstimateChords() }
    }

    override fun onDestroy() {
        super.onDestroy()
        workerExecutor.shutdownNow()
    }

    private fun bindViews() {
        layoutClientId = findViewById(R.id.layoutClientId)
        layoutClientSecret = findViewById(R.id.layoutClientSecret)
        layoutTrackInput = findViewById(R.id.layoutTrackInput)
        editClientId = findViewById(R.id.editClientId)
        editClientSecret = findViewById(R.id.editClientSecret)
        editTrackInput = findViewById(R.id.editTrackInput)
        buttonFetchChords = findViewById(R.id.buttonFetchChords)
        progressBar = findViewById(R.id.progressBar)
        textStatus = findViewById(R.id.textStatus)
        textTrackInfo = findViewById(R.id.textTrackInfo)
        textChordOutput = findViewById(R.id.textChordOutput)
    }

    private fun prefillCredentialInputs() {
        val preferences = getSharedPreferences(preferencesFile, MODE_PRIVATE)
        val savedClientId = preferences.getString(prefClientId, "").orEmpty()
        val savedClientSecret = preferences.getString(prefClientSecret, "").orEmpty()
        editClientId.setText(if (savedClientId.isNotBlank()) savedClientId else BuildConfig.SPOTIFY_CLIENT_ID)
        editClientSecret.setText(
            if (savedClientSecret.isNotBlank()) savedClientSecret else BuildConfig.SPOTIFY_CLIENT_SECRET
        )
    }

    private fun fetchAndEstimateChords() {
        clearInputErrors()
        val clientId = editClientId.text?.toString().orEmpty().trim()
        val clientSecret = editClientSecret.text?.toString().orEmpty().trim()
        val trackInput = editTrackInput.text?.toString().orEmpty().trim()

        var hasError = false
        if (clientId.isBlank()) {
            layoutClientId.error = getString(R.string.error_missing_credentials)
            hasError = true
        }
        if (clientSecret.isBlank()) {
            layoutClientSecret.error = getString(R.string.error_missing_credentials)
            hasError = true
        }
        if (trackInput.isBlank()) {
            layoutTrackInput.error = getString(R.string.error_missing_track)
            hasError = true
        }
        if (hasError) return

        val trackId = SpotifyTrackParser.extractTrackId(trackInput)
        if (trackId == null) {
            layoutTrackInput.error = getString(R.string.error_invalid_track)
            return
        }

        storeCredentials(clientId, clientSecret)
        setLoadingState(true)
        textTrackInfo.text = ""
        textChordOutput.text = getString(R.string.placeholder_output)

        workerExecutor.execute {
            try {
                val spotifyClient = SpotifyApiClient(clientId = clientId, clientSecret = clientSecret)
                val accessToken = spotifyClient.requestAccessToken()
                val trackInfo = spotifyClient.getTrackInfo(accessToken, trackId)
                val audioAnalysis = spotifyClient.getAudioAnalysis(accessToken, trackId)
                val audioFeatures = try {
                    spotifyClient.getAudioFeatures(accessToken, trackId)
                } catch (_: IOException) {
                    null
                }

                val progression = ChordEstimator.estimate(audioAnalysis, audioFeatures)
                val output = buildResultText(trackInfo, progression)
                runOnUiThread {
                    setLoadingState(false)
                    textStatus.text = "Completed. Chords estimated for ${trackInfo.name}."
                    textTrackInfo.text = formatTrackInfo(trackInfo)
                    textChordOutput.text = output
                }
            } catch (exception: Exception) {
                val safeMessage = exception.message ?: "Unknown error while contacting Spotify."
                runOnUiThread {
                    setLoadingState(false)
                    textStatus.text = "Failed: $safeMessage"
                    textTrackInfo.text = ""
                }
            }
        }
    }

    private fun buildResultText(trackInfo: TrackInfo, progression: ChordProgression): String {
        return buildString {
            appendLine("Track: ${trackInfo.name}")
            appendLine("Artists: ${trackInfo.artists.joinToString(", ").ifBlank { "Unknown Artist" }}")
            if (trackInfo.album != null) appendLine("Album: ${trackInfo.album}")
            if (progression.keyLabel != null) appendLine("Estimated key: ${progression.keyLabel}")
            if (progression.tempoBpm != null) appendLine("Tempo: ${"%.1f".format(progression.tempoBpm)} BPM")
            if (progression.timeSignature != null) appendLine("Time signature: ${progression.timeSignature}/4")
            appendLine()
            appendLine("Compact progression:")
            appendLine(progression.compactProgression)
            appendLine()
            appendLine("Timeline:")
            if (progression.timeline.isEmpty()) {
                appendLine("No stable timeline available.")
            } else {
                progression.timeline.forEach { entry ->
                    appendLine("${formatTimestamp(entry.startSec)} - ${formatTimestamp(entry.endSec)}   ${entry.chord}")
                }
            }
            appendLine()
            appendLine(getString(R.string.lyrics_not_available))
        }
    }

    private fun formatTrackInfo(trackInfo: TrackInfo): String {
        val artists = trackInfo.artists.joinToString(", ").ifBlank { "Unknown Artist" }
        return "${trackInfo.name} - $artists"
    }

    private fun formatTimestamp(seconds: Double): String {
        val totalSeconds = maxOf(0, seconds.roundToInt())
        val minutes = totalSeconds / 60
        val remainingSeconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, remainingSeconds)
    }

    private fun setLoadingState(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        buttonFetchChords.isEnabled = !isLoading
        textStatus.text = if (isLoading) getString(R.string.status_loading) else getString(R.string.status_idle)
    }

    private fun clearInputErrors() {
        layoutClientId.error = null
        layoutClientSecret.error = null
        layoutTrackInput.error = null
    }

    private fun storeCredentials(clientId: String, clientSecret: String) {
        getSharedPreferences(preferencesFile, MODE_PRIVATE)
            .edit()
            .putString(prefClientId, clientId)
            .putString(prefClientSecret, clientSecret)
            .apply()
    }

    companion object {
        private const val preferencesFile = "spotify_credentials"
        private const val prefClientId = "pref_client_id"
        private const val prefClientSecret = "pref_client_secret"
    }
}

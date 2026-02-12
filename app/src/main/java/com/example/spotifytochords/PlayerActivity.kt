package com.example.spotifytochords

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException
import java.util.concurrent.Executors
import kotlin.math.max

class PlayerActivity : AppCompatActivity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var textSong: TextView
    private lateinit var textArtist: TextView
    private lateinit var textChordCurrent: TextView
    private lateinit var textChordNext1: TextView
    private lateinit var textChordNext2: TextView
    private lateinit var textKeyTempo: TextView
    private lateinit var textStatus: TextView
    private lateinit var textCurrentTime: TextView
    private lateinit var textTotalTime: TextView
    private lateinit var seekPlayback: SeekBar
    private lateinit var buttonPlayPause: ImageButton
    private lateinit var buttonRestart: ImageButton
    private lateinit var buttonRetry: ImageButton

    private var mediaPlayer: MediaPlayer? = null
    private var playerData: PlayerTrackData? = null
    private var baseTrack: SearchTrack? = null
    private var isSeeking = false
    private var isPrepared = false

    private val updateRunnable = object : Runnable {
        override fun run() {
            val mp = mediaPlayer
            if (mp != null && isPrepared) {
                val current = mp.currentPosition
                if (!isSeeking) {
                    seekPlayback.progress = current
                }
                textCurrentTime.text = formatMs(current)
                updateChordsForPlayback(current / 1000.0)
                mainHandler.postDelayed(this, 250L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        bindViews()
        baseTrack = readTrackFromIntent()
        val track = baseTrack
        if (track == null) {
            finish()
            return
        }

        textSong.text = track.name
        textArtist.text = track.artist
        textChordCurrent.text = track.keyLabel ?: "--"
        textKeyTempo.text = if (track.keyLabel != null && track.tempoBpm != null) {
            "${track.keyLabel} | ${track.tempoBpm.toInt()} bpm"
        } else {
            getString(R.string.unknown_key_bpm)
        }

        initializeButtons()
        preparePreview(track.previewUrl)
        loadChordData(track)
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(updateRunnable)
        mediaPlayer?.release()
        mediaPlayer = null
        worker.shutdownNow()
    }

    private fun bindViews() {
        findViewById<ImageButton>(R.id.buttonBack).setOnClickListener { finish() }
        textSong = findViewById(R.id.textPlayerSong)
        textArtist = findViewById(R.id.textPlayerArtist)
        textChordCurrent = findViewById(R.id.textChordCurrent)
        textChordNext1 = findViewById(R.id.textChordNext1)
        textChordNext2 = findViewById(R.id.textChordNext2)
        textKeyTempo = findViewById(R.id.textKeyTempo)
        textStatus = findViewById(R.id.textPlayerStatus)
        textCurrentTime = findViewById(R.id.textCurrentTime)
        textTotalTime = findViewById(R.id.textTotalTime)
        seekPlayback = findViewById(R.id.seekPlayback)
        buttonPlayPause = findViewById(R.id.buttonPlayPause)
        buttonRestart = findViewById(R.id.buttonRestart)
        buttonRetry = findViewById(R.id.buttonRetryLoad)
    }

    private fun initializeButtons() {
        buttonPlayPause.setOnClickListener {
            togglePlayback()
        }
        buttonRestart.setOnClickListener {
            mediaPlayer?.let { mp ->
                if (isPrepared) {
                    mp.seekTo(0)
                }
            }
        }
        buttonRetry.setOnClickListener {
            baseTrack?.let { loadChordData(it) }
        }
        seekPlayback.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    textCurrentTime.text = formatMs(progress)
                    updateChordsForPlayback(progress / 1000.0)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isSeeking = false
                mediaPlayer?.seekTo(seekBar?.progress ?: 0)
            }
        })
    }

    private fun loadChordData(track: SearchTrack) {
        textStatus.text = getString(R.string.player_loading_chords)
        worker.execute {
            try {
                val loaded = ChordyRepository.loadPlayerData(this, track)
                runOnUiThread {
                    playerData = loaded
                    textStatus.text = getString(R.string.feature_limited_preview)
                    val keyLabel = loaded.track.keyLabel
                    val tempo = loaded.track.tempoBpm
                    textKeyTempo.text = if (keyLabel != null && tempo != null) {
                        "${keyLabel} | ${tempo.toInt()} bpm"
                    } else {
                        getString(R.string.unknown_key_bpm)
                    }
                    updateChordsForPlayback((mediaPlayer?.currentPosition ?: 0) / 1000.0)
                }
            } catch (exception: SpotifyApiException) {
                runOnUiThread {
                    textStatus.text = when (exception.statusCode) {
                        401 -> getString(R.string.spotify_login_required)
                        403 -> getString(R.string.audio_api_forbidden)
                        else -> getString(R.string.error_generic)
                    }
                }
            } catch (_: IllegalStateException) {
                runOnUiThread {
                    textStatus.text = getString(R.string.spotify_login_required)
                }
            } catch (_: Exception) {
                runOnUiThread {
                    textStatus.text = getString(R.string.error_generic)
                }
            }
        }
    }

    private fun preparePreview(previewUrl: String?) {
        val safePreviewUrl = previewUrl
            ?.trim()
            ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        if (safePreviewUrl.isNullOrBlank()) {
            buttonPlayPause.isEnabled = false
            buttonRestart.isEnabled = false
            textStatus.text = getString(R.string.playing_unavailable)
            return
        }

        val player = MediaPlayer()
        try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            player.setDataSource(safePreviewUrl)
            player.setOnPreparedListener { mp ->
                isPrepared = true
                seekPlayback.max = max(30_000, mp.duration)
                textTotalTime.text = formatMs(mp.duration)
                textCurrentTime.text = formatMs(0)
                mp.start()
                buttonPlayPause.setImageResource(android.R.drawable.ic_media_pause)
                mainHandler.post(updateRunnable)
            }
            player.setOnCompletionListener {
                buttonPlayPause.setImageResource(android.R.drawable.ic_media_play)
            }
            player.setOnErrorListener { _, _, _ ->
                buttonPlayPause.isEnabled = false
                buttonRestart.isEnabled = false
                textStatus.text = getString(R.string.playing_unavailable)
                true
            }
            player.prepareAsync()
            mediaPlayer = player
        } catch (_: IOException) {
            player.release()
            buttonPlayPause.isEnabled = false
            buttonRestart.isEnabled = false
            textStatus.text = getString(R.string.playing_unavailable)
        } catch (_: IllegalArgumentException) {
            player.release()
            buttonPlayPause.isEnabled = false
            buttonRestart.isEnabled = false
            textStatus.text = getString(R.string.playing_unavailable)
        }
    }

    private fun togglePlayback() {
        val mp = mediaPlayer ?: return
        if (!isPrepared) return
        if (mp.isPlaying) {
            mp.pause()
            buttonPlayPause.setImageResource(android.R.drawable.ic_media_play)
        } else {
            mp.start()
            buttonPlayPause.setImageResource(android.R.drawable.ic_media_pause)
            mainHandler.post(updateRunnable)
        }
    }

    private fun updateChordsForPlayback(seconds: Double) {
        val progression = playerData?.progression ?: return
        if (progression.timeline.isEmpty()) return
        val timeline = progression.timeline
        val currentIndex = timeline.indexOfLast { seconds >= it.startSec }.coerceAtLeast(0)
        val current = timeline.getOrNull(currentIndex) ?: timeline.first()
        textChordCurrent.text = current.chord
        textChordNext1.text = timeline.getOrNull(currentIndex + 1)?.chord ?: ""
        textChordNext2.text = timeline.getOrNull(currentIndex + 2)?.chord ?: ""
    }

    private fun readTrackFromIntent(): SearchTrack? {
        val id = intent.getStringExtra(extraTrackId) ?: return null
        val previewUrl = intent.getStringExtra(extraTrackPreview)
            ?.trim()
            ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        return SearchTrack(
            id = id,
            name = intent.getStringExtra(extraTrackName).orEmpty(),
            artist = intent.getStringExtra(extraTrackArtist).orEmpty(),
            album = intent.getStringExtra(extraTrackAlbum),
            albumImageUrl = intent.getStringExtra(extraTrackImage),
            previewUrl = previewUrl,
            keyLabel = intent.getStringExtra(extraTrackKeyLabel),
            tempoBpm = intent.getDoubleExtra(extraTrackTempo, -1.0).takeIf { it >= 0.0 }
        )
    }

    private fun formatMs(value: Int): String {
        val totalSeconds = max(0, value / 1000)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    companion object {
        const val extraTrackId = "extraTrackId"
        const val extraTrackName = "extraTrackName"
        const val extraTrackArtist = "extraTrackArtist"
        const val extraTrackAlbum = "extraTrackAlbum"
        const val extraTrackImage = "extraTrackImage"
        const val extraTrackPreview = "extraTrackPreview"
        const val extraTrackKeyLabel = "extraTrackKeyLabel"
        const val extraTrackTempo = "extraTrackTempo"
    }
}

package com.example.spotifytochords

import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import coil.load
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.io.IOException

class MainActivity : AppCompatActivity() {
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var miniPlayerContainer: View
    private lateinit var imageMiniCover: ImageView
    private lateinit var textMiniSong: TextView
    private lateinit var textMiniArtist: TextView
    private lateinit var buttonMiniPlayPause: ImageButton

    private var miniPlayerMediaPlayer: MediaPlayer? = null
    private var isMiniPlayerPrepared = false
    private var miniPlayerTrack: SearchTrack? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bottomNavigation = findViewById(R.id.bottomNavigation)
        miniPlayerContainer = findViewById(R.id.miniPlayerContainer)
        imageMiniCover = findViewById(R.id.imageMiniCover)
        textMiniSong = findViewById(R.id.textMiniSong)
        textMiniArtist = findViewById(R.id.textMiniArtist)
        buttonMiniPlayPause = findViewById(R.id.buttonMiniPlayPause)

        miniPlayerContainer.visibility = View.GONE
        miniPlayerContainer.setOnClickListener {
            miniPlayerTrack?.let { openPlayer(it) }
        }
        buttonMiniPlayPause.setOnClickListener {
            toggleMiniPlayback()
        }

        if (savedInstanceState == null) {
            openFragment(HomeFragment())
        }

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> openFragment(HomeFragment())
                R.id.nav_search -> openFragment(SearchFragment())
                R.id.nav_account -> openFragment(AccountFragment())
                else -> false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseMiniPlayer()
    }

    fun playTrackInMiniPlayer(track: SearchTrack) {
        miniPlayerTrack = track
        miniPlayerContainer.visibility = View.VISIBLE
        imageMiniCover.load(track.albumImageUrl)
        textMiniSong.text = track.name
        textMiniArtist.text = track.artist
        prepareMiniPreview(track.previewUrl)
    }

    fun openPlayer(track: SearchTrack) {
        releaseMiniPlayer()
        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra(PlayerActivity.extraTrackId, track.id)
                .putExtra(PlayerActivity.extraTrackName, track.name)
                .putExtra(PlayerActivity.extraTrackArtist, track.artist)
                .putExtra(PlayerActivity.extraTrackAlbum, track.album)
                .putExtra(PlayerActivity.extraTrackImage, track.albumImageUrl)
                .putExtra(PlayerActivity.extraTrackPreview, track.previewUrl)
                .putExtra(PlayerActivity.extraTrackKeyLabel, track.keyLabel)
                .putExtra(PlayerActivity.extraTrackTempo, track.tempoBpm ?: -1.0)
        )
    }

    fun openArtistPage(artist: SearchArtist) {
        startActivity(
            Intent(this, ArtistActivity::class.java)
                .putExtra(ArtistActivity.extraArtistId, artist.id)
                .putExtra(ArtistActivity.extraArtistName, artist.name)
                .putExtra(ArtistActivity.extraArtistImage, artist.imageUrl)
        )
    }

    fun openAlbumPage(album: SearchAlbum) {
        startActivity(
            Intent(this, AlbumActivity::class.java)
                .putExtra(AlbumActivity.extraAlbumId, album.id)
                .putExtra(AlbumActivity.extraAlbumName, album.name)
                .putExtra(AlbumActivity.extraAlbumArtist, album.artist)
                .putExtra(AlbumActivity.extraAlbumImage, album.imageUrl)
                .putExtra(AlbumActivity.extraAlbumReleaseDate, album.releaseDate)
        )
    }

    fun openSpotifyLogin() {
        startActivity(Intent(this, SpotifyLoginActivity::class.java))
    }

    private fun prepareMiniPreview(previewUrl: String?) {
        releaseMiniPlayer()
        if (previewUrl.isNullOrBlank()) {
            buttonMiniPlayPause.isEnabled = false
            buttonMiniPlayPause.setImageResource(android.R.drawable.ic_media_play)
            return
        }

        buttonMiniPlayPause.isEnabled = true
        try {
            miniPlayerMediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(previewUrl)
                setOnPreparedListener { mediaPlayer ->
                    isMiniPlayerPrepared = true
                    mediaPlayer.start()
                    buttonMiniPlayPause.setImageResource(android.R.drawable.ic_media_pause)
                }
                setOnCompletionListener {
                    buttonMiniPlayPause.setImageResource(android.R.drawable.ic_media_play)
                }
                setOnErrorListener { _, _, _ ->
                    buttonMiniPlayPause.isEnabled = false
                    buttonMiniPlayPause.setImageResource(android.R.drawable.ic_media_play)
                    true
                }
                prepareAsync()
            }
        } catch (_: IOException) {
            buttonMiniPlayPause.isEnabled = false
            buttonMiniPlayPause.setImageResource(android.R.drawable.ic_media_play)
        }
    }

    private fun toggleMiniPlayback() {
        val mediaPlayer = miniPlayerMediaPlayer ?: return
        if (!isMiniPlayerPrepared) return
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
            buttonMiniPlayPause.setImageResource(android.R.drawable.ic_media_play)
        } else {
            mediaPlayer.start()
            buttonMiniPlayPause.setImageResource(android.R.drawable.ic_media_pause)
        }
    }

    private fun releaseMiniPlayer() {
        miniPlayerMediaPlayer?.release()
        miniPlayerMediaPlayer = null
        isMiniPlayerPrepared = false
        buttonMiniPlayPause.setImageResource(android.R.drawable.ic_media_play)
    }

    private fun openFragment(fragment: Fragment): Boolean {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
        return true
    }
}

package com.example.spotifytochords

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.util.concurrent.Executors

class SpotifyLoginActivity : AppCompatActivity() {
    private val worker = Executors.newSingleThreadExecutor()

    private lateinit var textStatus: TextView
    private lateinit var buttonLogin: MaterialButton
    private lateinit var buttonLogout: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_spotify_login)

        findViewById<ImageButton>(R.id.buttonSpotifyLoginBack).setOnClickListener { finish() }
        textStatus = findViewById(R.id.textSpotifyLoginStatus)
        buttonLogin = findViewById(R.id.buttonSpotifyLogin)
        buttonLogout = findViewById(R.id.buttonSpotifyLogout)

        buttonLogin.setOnClickListener {
            try {
                textStatus.text = getString(R.string.spotify_login_opening_browser)
                SpotifyAuthManager.startLogin(this)
            } catch (exception: Exception) {
                updateUi(exception.message ?: getString(R.string.error_generic))
            }
        }

        buttonLogout.setOnClickListener {
            ChordyRepository.clearSpotifySession(this)
            updateUi(getString(R.string.spotify_login_required))
        }

        updateUi()
        handleCallbackIntentIfNeeded(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleCallbackIntentIfNeeded(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        worker.shutdownNow()
    }

    private fun handleCallbackIntentIfNeeded(intent: android.content.Intent?) {
        val callbackUri = intent?.data ?: return
        textStatus.text = getString(R.string.spotify_login_finishing)

        worker.execute {
            try {
                val token = SpotifyAuthManager.completeLogin(this, callbackUri)
                ChordyRepository.saveSpotifyAuthToken(this, token)
                runOnUiThread {
                    updateUi(getString(R.string.spotify_login_success))
                }
            } catch (exception: Exception) {
                runOnUiThread {
                    updateUi(exception.message ?: getString(R.string.error_generic))
                }
            }
        }
    }

    private fun updateUi(statusMessage: String? = null) {
        val loggedIn = ChordyRepository.hasSpotifySession(this)
        buttonLogout.visibility = if (loggedIn) View.VISIBLE else View.GONE
        buttonLogin.text = if (loggedIn) {
            getString(R.string.spotify_reconnect)
        } else {
            getString(R.string.spotify_login)
        }
        textStatus.text = statusMessage ?: if (loggedIn) {
            getString(R.string.spotify_logged_in)
        } else {
            getString(R.string.spotify_login_required)
        }
    }
}

package com.example.spotifytochords

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    private lateinit var bottomNavigation: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bottomNavigation = findViewById(R.id.bottomNavigation)

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

    fun openPlayer(track: SearchTrack) {
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

    fun showCredentialsDialog(onSaved: (() -> Unit)? = null) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_credentials, null, false)
        val editClientId = dialogView.findViewById<EditText>(R.id.editDialogClientId)
        val editClientSecret = dialogView.findViewById<EditText>(R.id.editDialogClientSecret)
        val (storedId, storedSecret) = ChordyRepository.getCredentials(this)
        editClientId.setText(storedId)
        editClientSecret.setText(storedSecret)

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton(getString(R.string.button_save)) { _, _ ->
                ChordyRepository.saveCredentials(
                    this,
                    editClientId.text?.toString().orEmpty(),
                    editClientSecret.text?.toString().orEmpty()
                )
                onSaved?.invoke()
            }
            .setNegativeButton(getString(R.string.button_cancel), null)
            .show()
    }

    private fun openFragment(fragment: Fragment): Boolean {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
        return true
    }
}

package com.example.spotifytochords

import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import coil.load

class AlbumActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_album)

        findViewById<ImageButton>(R.id.buttonAlbumBack).setOnClickListener { finish() }

        val imageAlbum = findViewById<ImageView>(R.id.imageAlbumHeader)
        val textName = findViewById<TextView>(R.id.textAlbumName)
        val textArtist = findViewById<TextView>(R.id.textAlbumArtist)
        val textTodo = findViewById<TextView>(R.id.textAlbumTodo)

        val name = intent.getStringExtra(extraAlbumName).orEmpty().ifBlank {
            getString(R.string.album_label)
        }
        val artist = intent.getStringExtra(extraAlbumArtist).orEmpty().ifBlank {
            getString(R.string.unknown_artist)
        }

        textName.text = name
        textArtist.text = artist
        textTodo.text = getString(R.string.todo_album_page, name)
        imageAlbum.load(intent.getStringExtra(extraAlbumImage))
    }

    companion object {
        const val extraAlbumId = "extraAlbumId"
        const val extraAlbumName = "extraAlbumName"
        const val extraAlbumArtist = "extraAlbumArtist"
        const val extraAlbumImage = "extraAlbumImage"
        const val extraAlbumReleaseDate = "extraAlbumReleaseDate"
    }
}

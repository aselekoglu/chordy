package com.example.spotifytochords

import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import coil.load

class ArtistActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_artist)

        findViewById<ImageButton>(R.id.buttonArtistBack).setOnClickListener { finish() }

        val imageArtist = findViewById<ImageView>(R.id.imageArtistHeader)
        val textName = findViewById<TextView>(R.id.textArtistName)
        val textTodo = findViewById<TextView>(R.id.textArtistTodo)

        val name = intent.getStringExtra(extraArtistName).orEmpty().ifBlank {
            getString(R.string.artist_label)
        }
        textName.text = name
        imageArtist.load(intent.getStringExtra(extraArtistImage))
        textTodo.text = getString(R.string.todo_artist_page, name)
    }

    companion object {
        const val extraArtistId = "extraArtistId"
        const val extraArtistName = "extraArtistName"
        const val extraArtistImage = "extraArtistImage"
    }
}

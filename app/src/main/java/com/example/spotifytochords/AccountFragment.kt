package com.example.spotifytochords

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.button.MaterialButton

class AccountFragment : Fragment(R.layout.fragment_account) {
    private lateinit var imageAccountAvatar: ImageView
    private lateinit var buttonSettings: ImageButton
    private lateinit var buttonEditCredentials: MaterialButton
    private lateinit var textAccountMessage: TextView
    private lateinit var recyclerLibrary: RecyclerView
    private lateinit var adapter: LibraryAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        imageAccountAvatar = view.findViewById(R.id.imageAccountAvatar)
        buttonSettings = view.findViewById(R.id.buttonAccountSettings)
        buttonEditCredentials = view.findViewById(R.id.buttonEditCredentials)
        textAccountMessage = view.findViewById(R.id.textAccountMessage)
        recyclerLibrary = view.findViewById(R.id.recyclerLibrary)

        adapter = LibraryAdapter { track ->
            (activity as? MainActivity)?.openPlayer(track)
        }
        recyclerLibrary.layoutManager = GridLayoutManager(requireContext(), 2)
        recyclerLibrary.adapter = adapter

        val credentialsAction = {
            (activity as? MainActivity)?.showCredentialsDialog {
                refreshContent()
            }
        }
        buttonSettings.setOnClickListener { credentialsAction() }
        buttonEditCredentials.setOnClickListener { credentialsAction() }

        refreshContent()
    }

    override fun onResume() {
        super.onResume()
        refreshContent()
    }

    private fun refreshContent() {
        val tracks = ChordyRepository.getLibraryTracks()
        adapter.submitList(tracks)
        if (tracks.isNotEmpty()) {
            imageAccountAvatar.load(tracks.first().albumImageUrl)
        }
        textAccountMessage.text = if (ChordyRepository.hasCredentials(requireContext())) {
            getString(R.string.feature_limited_preview)
        } else {
            getString(R.string.credentials_required)
        }
    }
}

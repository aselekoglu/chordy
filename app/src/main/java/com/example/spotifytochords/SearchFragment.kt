package com.example.spotifytochords

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

class SearchFragment : Fragment(R.layout.fragment_search) {
    private val worker = Executors.newSingleThreadExecutor()

    private lateinit var editSearch: EditText
    private lateinit var buttonCancel: TextView
    private lateinit var textState: TextView
    private lateinit var recyclerResults: RecyclerView
    private lateinit var adapter: SearchResultsAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        editSearch = view.findViewById(R.id.editSearch)
        buttonCancel = view.findViewById(R.id.buttonSearchCancel)
        textState = view.findViewById(R.id.textSearchState)
        recyclerResults = view.findViewById(R.id.recyclerSearchResults)

        adapter = SearchResultsAdapter(
            onPlayClicked = { track -> (activity as? MainActivity)?.openPlayer(track) },
            onAddClicked = { track ->
                ChordyRepository.addToLibrary(track)
                textState.text = "${track.name} added to library."
            }
        )
        recyclerResults.layoutManager = LinearLayoutManager(requireContext())
        recyclerResults.adapter = adapter

        editSearch.setOnEditorActionListener { _, actionId, event ->
            val searchAction = actionId == EditorInfo.IME_ACTION_SEARCH
            val enterPressed = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (searchAction || enterPressed) {
                runSearch(editSearch.text?.toString().orEmpty())
                true
            } else {
                false
            }
        }

        buttonCancel.setOnClickListener {
            editSearch.setText("")
            textState.text = getString(R.string.search_empty)
            adapter.submitList(emptyList())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        worker.shutdownNow()
    }

    private fun runSearch(query: String) {
        val normalized = query.trim()
        if (normalized.length < 2) {
            textState.text = getString(R.string.search_empty)
            adapter.submitList(emptyList())
            return
        }

        textState.text = "Searching \"$normalized\"..."
        worker.execute {
            try {
                val results = ChordyRepository.searchTracks(requireContext(), normalized, limit = 16)
                requireActivity().runOnUiThread {
                    textState.text = if (results.isEmpty()) {
                        getString(R.string.search_no_results)
                    } else {
                        getString(R.string.feature_limited_preview)
                    }
                    adapter.submitList(results)
                }
            } catch (_: IllegalStateException) {
                requireActivity().runOnUiThread {
                    textState.text = getString(R.string.credentials_required)
                    (activity as? MainActivity)?.showCredentialsDialog {
                        runSearch(normalized)
                    }
                }
            } catch (_: Exception) {
                requireActivity().runOnUiThread {
                    textState.text = getString(R.string.error_generic)
                }
            }
        }
    }
}

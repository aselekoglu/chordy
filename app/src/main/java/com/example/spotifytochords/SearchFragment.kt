package com.example.spotifytochords

import android.graphics.Typeface
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.ContextCompat
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
    private lateinit var textTabSong: TextView
    private lateinit var textTabArtist: TextView
    private lateinit var textTabAlbum: TextView
    private lateinit var adapter: SearchResultsAdapter

    private var selectedTab: SearchTab = SearchTab.SONG
    private var searchRequestId: Int = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        editSearch = view.findViewById(R.id.editSearch)
        buttonCancel = view.findViewById(R.id.buttonSearchCancel)
        textState = view.findViewById(R.id.textSearchState)
        recyclerResults = view.findViewById(R.id.recyclerSearchResults)
        textTabSong = view.findViewById(R.id.textTabSong)
        textTabArtist = view.findViewById(R.id.textTabArtist)
        textTabAlbum = view.findViewById(R.id.textTabAlbum)

        adapter = SearchResultsAdapter(
            onTrackClicked = { track -> (activity as? MainActivity)?.playTrackInMiniPlayer(track) },
            onTrackAddClicked = { track ->
                ChordyRepository.addToLibrary(track)
                textState.text = "${track.name} added to library."
            },
            onArtistClicked = { artist -> (activity as? MainActivity)?.openArtistPage(artist) },
            onAlbumClicked = { album -> (activity as? MainActivity)?.openAlbumPage(album) }
        )
        recyclerResults.layoutManager = LinearLayoutManager(requireContext())
        recyclerResults.adapter = adapter

        textTabSong.setOnClickListener { selectTab(SearchTab.SONG) }
        textTabArtist.setOnClickListener { selectTab(SearchTab.ARTIST) }
        textTabAlbum.setOnClickListener { selectTab(SearchTab.ALBUM) }
        selectTab(SearchTab.SONG, rerunSearch = false)

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
            searchRequestId += 1
            editSearch.setText("")
            textState.text = getEmptyStateFor(selectedTab)
            adapter.submitList(emptyList())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        worker.shutdownNow()
    }

    private fun selectTab(tab: SearchTab, rerunSearch: Boolean = true) {
        selectedTab = tab
        setTabStyle(textTabSong, tab == SearchTab.SONG)
        setTabStyle(textTabArtist, tab == SearchTab.ARTIST)
        setTabStyle(textTabAlbum, tab == SearchTab.ALBUM)

        if (!rerunSearch) {
            textState.text = getEmptyStateFor(tab)
            return
        }

        runSearch(editSearch.text?.toString().orEmpty())
    }

    private fun setTabStyle(view: TextView, isSelected: Boolean) {
        view.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (isSelected) R.color.text_primary_dark else R.color.text_muted_dark
            )
        )
        view.setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
        view.setBackgroundResource(
            if (isSelected) R.drawable.bg_search_tab_selected else R.drawable.bg_search_tab_unselected
        )
    }

    private fun runSearch(query: String) {
        val normalized = query.trim()
        val tabForRequest = selectedTab
        val requestId = ++searchRequestId
        if (normalized.length < 2) {
            textState.text = getEmptyStateFor(tabForRequest)
            adapter.submitList(emptyList())
            return
        }

        textState.text = getString(R.string.searching_tab, normalized, getTabLabel(tabForRequest))
        worker.execute {
            val context = context ?: return@execute
            try {
                val results = when (tabForRequest) {
                    SearchTab.SONG -> ChordyRepository.searchTracks(context, normalized, limit = 16)
                        .map { SearchListItem.TrackItem(it) }

                    SearchTab.ARTIST -> ChordyRepository.searchArtists(context, normalized, limit = 20)
                        .map { SearchListItem.ArtistItem(it) }

                    SearchTab.ALBUM -> ChordyRepository.searchAlbums(context, normalized, limit = 20)
                        .map { SearchListItem.AlbumItem(it) }
                }

                activity?.runOnUiThread {
                    if (!isAdded || requestId != searchRequestId || tabForRequest != selectedTab) return@runOnUiThread
                    textState.text = if (results.isEmpty()) {
                        getString(R.string.search_no_results)
                    } else {
                        getString(R.string.search_results_count, results.size, getTabLabel(tabForRequest))
                    }
                    adapter.submitList(results)
                }
            } catch (_: IllegalStateException) {
                activity?.runOnUiThread {
                    if (!isAdded || requestId != searchRequestId) return@runOnUiThread
                    textState.text = getString(R.string.spotify_login_required)
                    (activity as? MainActivity)?.openSpotifyLogin()
                }
            } catch (exception: SpotifyApiException) {
                activity?.runOnUiThread {
                    if (!isAdded || requestId != searchRequestId) return@runOnUiThread
                    textState.text = when (exception.statusCode) {
                        401 -> getString(R.string.spotify_login_required)
                        403 -> getString(R.string.audio_api_forbidden)
                        else -> exception.message ?: getString(R.string.error_generic)
                    }
                    if (exception.statusCode == 401) {
                        (activity as? MainActivity)?.openSpotifyLogin()
                    }
                }
            } catch (_: Exception) {
                activity?.runOnUiThread {
                    if (!isAdded || requestId != searchRequestId) return@runOnUiThread
                    textState.text = getString(R.string.error_generic)
                }
            }
        }
    }

    private fun getEmptyStateFor(tab: SearchTab): String {
        return when (tab) {
            SearchTab.SONG -> getString(R.string.search_empty_song)
            SearchTab.ARTIST -> getString(R.string.search_empty_artist)
            SearchTab.ALBUM -> getString(R.string.search_empty_album)
        }
    }

    private fun getTabLabel(tab: SearchTab): String {
        return when (tab) {
            SearchTab.SONG -> getString(R.string.tab_song)
            SearchTab.ARTIST -> getString(R.string.tab_artist)
            SearchTab.ALBUM -> getString(R.string.tab_album)
        }
    }

    private enum class SearchTab {
        SONG,
        ARTIST,
        ALBUM
    }
}

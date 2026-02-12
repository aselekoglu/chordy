package com.example.spotifytochords

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

class HomeFragment : Fragment(R.layout.fragment_home) {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var textHomeMessage: TextView
    private lateinit var recyclerHome: RecyclerView
    private lateinit var adapter: HomeFeedAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        textHomeMessage = view.findViewById(R.id.textHomeMessage)
        recyclerHome = view.findViewById(R.id.recyclerHome)

        adapter = HomeFeedAdapter(
            onPlayClicked = { track -> (activity as? MainActivity)?.openPlayer(track) },
            onSaveClicked = { track ->
                ChordyRepository.addToLibrary(track)
                textHomeMessage.text = "${track.name} added to library."
            }
        )
        recyclerHome.layoutManager = LinearLayoutManager(requireContext())
        recyclerHome.adapter = adapter

        loadFeed()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        worker.shutdownNow()
    }

    private fun loadFeed() {
        textHomeMessage.text = getString(R.string.home_loading)
        worker.execute {
            try {
                val posts = ChordyRepository.loadHomePosts(requireContext())
                requireActivity().runOnUiThread {
                    if (posts.isEmpty()) {
                        textHomeMessage.text = getString(R.string.search_no_results)
                    } else {
                        textHomeMessage.text = getString(R.string.feature_limited_preview)
                    }
                    adapter.submitList(posts)
                }
            } catch (_: IllegalStateException) {
                requireActivity().runOnUiThread {
                    textHomeMessage.text = getString(R.string.spotify_login_required)
                    adapter.submitList(emptyList())
                }
            } catch (exception: SpotifyApiException) {
                requireActivity().runOnUiThread {
                    textHomeMessage.text = if (exception.statusCode == 401) {
                        getString(R.string.spotify_login_required)
                    } else {
                        exception.message ?: getString(R.string.error_generic)
                    }
                    adapter.submitList(emptyList())
                }
            } catch (_: Exception) {
                requireActivity().runOnUiThread {
                    textHomeMessage.text = getString(R.string.error_generic)
                    adapter.submitList(emptyList())
                }
            }
        }
    }
}

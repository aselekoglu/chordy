package com.example.spotifytochords

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load

class SearchResultsAdapter(
    private val onPlayClicked: (SearchTrack) -> Unit,
    private val onAddClicked: (SearchTrack) -> Unit
) : RecyclerView.Adapter<SearchResultsAdapter.TrackViewHolder>() {

    private val tracks = mutableListOf<SearchTrack>()

    fun submitList(newTracks: List<SearchTrack>) {
        tracks.clear()
        tracks.addAll(newTracks)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_search_track, parent, false)
        return TrackViewHolder(view)
    }

    override fun getItemCount(): Int = tracks.size

    override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
        holder.bind(tracks[position])
    }

    inner class TrackViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageCover: ImageView = itemView.findViewById(R.id.imageSearchCover)
        private val textSong: TextView = itemView.findViewById(R.id.textSearchSong)
        private val textArtist: TextView = itemView.findViewById(R.id.textSearchArtist)
        private val textMeta: TextView = itemView.findViewById(R.id.textSearchMeta)
        private val buttonPlay: ImageButton = itemView.findViewById(R.id.buttonSearchPlay)
        private val buttonAdd: ImageButton = itemView.findViewById(R.id.buttonSearchAdd)

        fun bind(track: SearchTrack) {
            textSong.text = track.name
            textArtist.text = track.artist
            textMeta.text = if (track.keyLabel != null && track.tempoBpm != null) {
                "${track.keyLabel} | ${track.tempoBpm.toInt()} bpm"
            } else {
                itemView.context.getString(R.string.unknown_key_bpm)
            }
            imageCover.load(track.albumImageUrl)

            buttonPlay.setOnClickListener { onPlayClicked(track) }
            buttonAdd.setOnClickListener { onAddClicked(track) }
        }
    }
}

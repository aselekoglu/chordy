package com.example.spotifytochords

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load

class LibraryAdapter(
    private val onTrackClicked: (SearchTrack) -> Unit
) : RecyclerView.Adapter<LibraryAdapter.LibraryViewHolder>() {

    private val tracks = mutableListOf<SearchTrack>()

    fun submitList(newTracks: List<SearchTrack>) {
        tracks.clear()
        tracks.addAll(newTracks)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LibraryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_library_track, parent, false)
        return LibraryViewHolder(view)
    }

    override fun getItemCount(): Int = tracks.size

    override fun onBindViewHolder(holder: LibraryViewHolder, position: Int) {
        holder.bind(tracks[position])
    }

    inner class LibraryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageCover: ImageView = itemView.findViewById(R.id.imageLibraryCover)
        private val textSong: TextView = itemView.findViewById(R.id.textLibrarySong)
        private val textArtist: TextView = itemView.findViewById(R.id.textLibraryArtist)
        private val textMeta: TextView = itemView.findViewById(R.id.textLibraryMeta)

        fun bind(track: SearchTrack) {
            imageCover.load(track.albumImageUrl)
            textSong.text = track.name
            textArtist.text = track.artist
            textMeta.text = if (track.keyLabel != null && track.tempoBpm != null) {
                "${track.keyLabel} | ${track.tempoBpm.toInt()} bpm"
            } else {
                itemView.context.getString(R.string.unknown_key_bpm)
            }
            itemView.setOnClickListener { onTrackClicked(track) }
        }
    }
}

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
    private val onTrackClicked: (SearchTrack) -> Unit,
    private val onTrackAddClicked: (SearchTrack) -> Unit,
    private val onArtistClicked: (SearchArtist) -> Unit,
    private val onAlbumClicked: (SearchAlbum) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<SearchListItem>()

    fun submitList(newItems: List<SearchListItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is SearchListItem.TrackItem -> viewTypeTrack
            is SearchListItem.ArtistItem -> viewTypeArtist
            is SearchListItem.AlbumItem -> viewTypeAlbum
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            viewTypeTrack -> TrackViewHolder(
                inflater.inflate(R.layout.item_search_track, parent, false)
            )

            viewTypeArtist -> ArtistViewHolder(
                inflater.inflate(R.layout.item_search_artist, parent, false)
            )

            else -> AlbumViewHolder(
                inflater.inflate(R.layout.item_search_album, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is SearchListItem.TrackItem -> (holder as TrackViewHolder).bind(item.track)
            is SearchListItem.ArtistItem -> (holder as ArtistViewHolder).bind(item.artist)
            is SearchListItem.AlbumItem -> (holder as AlbumViewHolder).bind(item.album)
        }
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

            itemView.setOnClickListener { onTrackClicked(track) }
            buttonPlay.setOnClickListener { onTrackClicked(track) }
            buttonAdd.setOnClickListener { onTrackAddClicked(track) }
        }
    }

    inner class ArtistViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageCover: ImageView = itemView.findViewById(R.id.imageSearchArtist)
        private val textName: TextView = itemView.findViewById(R.id.textSearchArtistName)
        private val textMeta: TextView = itemView.findViewById(R.id.textSearchArtistMeta)

        fun bind(artist: SearchArtist) {
            imageCover.load(artist.imageUrl)
            textName.text = artist.name
            textMeta.text = artist.followers?.let {
                itemView.context.getString(R.string.followers_label, it)
            } ?: itemView.context.getString(R.string.artist_label)
            itemView.setOnClickListener { onArtistClicked(artist) }
        }
    }

    inner class AlbumViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageCover: ImageView = itemView.findViewById(R.id.imageSearchAlbum)
        private val textName: TextView = itemView.findViewById(R.id.textSearchAlbumName)
        private val textArtist: TextView = itemView.findViewById(R.id.textSearchAlbumArtist)
        private val textMeta: TextView = itemView.findViewById(R.id.textSearchAlbumMeta)

        fun bind(album: SearchAlbum) {
            imageCover.load(album.imageUrl)
            textName.text = album.name
            textArtist.text = album.artist
            val releaseDate = album.releaseDate?.takeIf { it.isNotBlank() } ?: "--"
            val tracks = album.totalTracks?.toString() ?: "--"
            textMeta.text = itemView.context.getString(R.string.album_meta_format, releaseDate, tracks)
            itemView.setOnClickListener { onAlbumClicked(album) }
        }
    }

    companion object {
        private const val viewTypeTrack = 0
        private const val viewTypeArtist = 1
        private const val viewTypeAlbum = 2
    }
}

package com.example.spotifytochords

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load

class HomeFeedAdapter(
    private val onPlayClicked: (SearchTrack) -> Unit,
    private val onSaveClicked: (SearchTrack) -> Unit
) : RecyclerView.Adapter<HomeFeedAdapter.HomePostViewHolder>() {

    private val posts = mutableListOf<FeedPost>()

    fun submitList(newPosts: List<FeedPost>) {
        posts.clear()
        posts.addAll(newPosts)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HomePostViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_home_post, parent, false)
        return HomePostViewHolder(view)
    }

    override fun getItemCount(): Int = posts.size

    override fun onBindViewHolder(holder: HomePostViewHolder, position: Int) {
        holder.bind(posts[position])
    }

    inner class HomePostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageUserAvatar: ImageView = itemView.findViewById(R.id.imageUserAvatar)
        private val textUsername: TextView = itemView.findViewById(R.id.textUsername)
        private val textPostedAgo: TextView = itemView.findViewById(R.id.textPostedAgo)
        private val imageCover: ImageView = itemView.findViewById(R.id.imageCover)
        private val textSong: TextView = itemView.findViewById(R.id.textSong)
        private val textArtist: TextView = itemView.findViewById(R.id.textArtist)
        private val textMeta: TextView = itemView.findViewById(R.id.textMeta)
        private val textLikeCount: TextView = itemView.findViewById(R.id.textLikeCount)
        private val textCommentCount: TextView = itemView.findViewById(R.id.textCommentCount)
        private val buttonLike: ImageButton = itemView.findViewById(R.id.buttonLike)
        private val buttonPlay: ImageButton = itemView.findViewById(R.id.buttonPlay)
        private val buttonSave: ImageButton = itemView.findViewById(R.id.buttonSave)

        fun bind(post: FeedPost) {
            textUsername.text = post.username
            textPostedAgo.text = post.postedAgo
            textSong.text = post.track.name
            textArtist.text = post.track.artist
            textLikeCount.text = post.likeCount.toString()
            textCommentCount.text = post.commentCount.toString()
            textMeta.text = if (post.track.keyLabel != null && post.track.tempoBpm != null) {
                "${post.track.keyLabel} | ${post.track.tempoBpm.toInt()} bpm"
            } else {
                itemView.context.getString(R.string.unknown_key_bpm)
            }

            imageCover.load(post.track.albumImageUrl)
            imageUserAvatar.load(post.track.albumImageUrl)

            buttonLike.setColorFilter(Color.WHITE)
            buttonPlay.setOnClickListener { onPlayClicked(post.track) }
            buttonSave.setOnClickListener { onSaveClicked(post.track) }
        }
    }
}

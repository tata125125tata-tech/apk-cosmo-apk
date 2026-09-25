package com.cosmogamestore.app.ui.video

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.cosmogamestore.app.R
import com.cosmogamestore.app.data.db.VideoEntity
import java.io.File

class VideoTubeAdapter(
    private val onVideoClick: (VideoEntity) -> Unit,
    private val onShareClick: (VideoEntity) -> Unit
) : ListAdapter<VideoEntity, VideoTubeAdapter.VideoViewHolder>(VideoDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_video_card, parent, false)
        return VideoViewHolder(view)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivCover: ImageView = itemView.findViewById(R.id.iv_video_cover)
        private val tvCategory: TextView = itemView.findViewById(R.id.tv_card_category)
        private val tvOfflineBadge: TextView = itemView.findViewById(R.id.tv_card_offline_badge)
        private val tvTitle: TextView = itemView.findViewById(R.id.tv_card_title)
        private val tvViews: TextView = itemView.findViewById(R.id.tv_card_views)
        private val tvLikes: TextView = itemView.findViewById(R.id.tv_card_likes)
        private val ivLikeIcon: ImageView = itemView.findViewById(R.id.iv_card_like_icon)
        private val btnShare: ImageView = itemView.findViewById(R.id.btn_card_share)

        fun bind(video: VideoEntity) {
            tvTitle.text = video.title
            tvCategory.text = video.category.uppercase()
            tvViews.text = "${video.views} views"
            tvLikes.text = "${video.likes}"

            // Check if downloaded offline
            val isLocallySaved = video.isDownloaded && video.offlinePath != null && File(video.offlinePath).exists()
            tvOfflineBadge.visibility = if (isLocallySaved) View.VISIBLE else View.GONE

            if (video.isLiked) {
                ivLikeIcon.setImageResource(R.drawable.ic_liked)
                ivLikeIcon.imageTintList = null
            } else {
                ivLikeIcon.setImageResource(R.drawable.ic_like)
                ivLikeIcon.imageTintList = ContextCompat.getColorStateList(itemView.context, R.color.text_muted)
            }

            // Load Cover Image with Coil
            ivCover.load(video.coverUrl) {
                crossfade(true)
                placeholder(R.drawable.bg_video_thumbnail_placeholder)
                error(R.drawable.bg_video_thumbnail_placeholder)
            }

            itemView.setOnClickListener {
                onVideoClick(video)
            }

            btnShare.setOnClickListener {
                onShareClick(video)
            }
        }
    }

    class VideoDiffCallback : DiffUtil.ItemCallback<VideoEntity>() {
        override fun areItemsTheSame(oldItem: VideoEntity, newItem: VideoEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: VideoEntity, newItem: VideoEntity): Boolean {
            return oldItem == newItem
        }
    }
}

package com.cosmogamestore.app.ui.library

import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.cosmogamestore.app.R
import com.cosmogamestore.app.data.db.VideoEntity
import java.io.File

class DownloadedVideoAdapter(
    private val onPlayClick: (VideoEntity) -> Unit,
    private val onDeleteClick: (VideoEntity) -> Unit
) : ListAdapter<VideoEntity, DownloadedVideoAdapter.OfflineVideoViewHolder>(VideoDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OfflineVideoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_offline_video, parent, false)
        return OfflineVideoViewHolder(view)
    }

    override fun onBindViewHolder(holder: OfflineVideoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class OfflineVideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivCover: ImageView = itemView.findViewById(R.id.iv_offline_video_cover)
        private val tvTitle: TextView = itemView.findViewById(R.id.tv_offline_video_title)
        private val tvCategory: TextView = itemView.findViewById(R.id.tv_offline_video_category)
        private val tvSize: TextView = itemView.findViewById(R.id.tv_offline_video_size)
        private val btnPlay: Button = itemView.findViewById(R.id.btn_play_offline)
        private val btnDelete: ImageView = itemView.findViewById(R.id.btn_delete_offline_video)

        fun bind(video: VideoEntity) {
            tvTitle.text = video.title
            tvCategory.text = video.category.uppercase()

            val sizeBytes = if (video.fileSize > 0) {
                video.fileSize
            } else if (video.offlinePath != null) {
                File(video.offlinePath).length()
            } else {
                0L
            }
            tvSize.text = if (sizeBytes > 0) Formatter.formatFileSize(itemView.context, sizeBytes) else "Offline"

            ivCover.load(video.coverUrl) {
                crossfade(true)
                placeholder(R.drawable.bg_video_thumbnail_placeholder)
                error(R.drawable.bg_video_thumbnail_placeholder)
            }

            btnPlay.setOnClickListener {
                onPlayClick(video)
            }

            btnDelete.setOnClickListener {
                onDeleteClick(video)
            }

            itemView.setOnClickListener {
                onPlayClick(video)
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

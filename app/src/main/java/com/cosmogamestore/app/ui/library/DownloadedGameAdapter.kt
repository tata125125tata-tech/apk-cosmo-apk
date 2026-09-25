package com.cosmogamestore.app.ui.library

import android.content.Context
import android.text.format.DateFormat
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cosmogamestore.app.R
import com.cosmogamestore.app.data.ApkMetadataHelper
import com.cosmogamestore.app.data.db.DownloadedGameEntity
import java.io.File
import java.util.Date

class DownloadedGameAdapter(
    private val context: Context,
    private val onInstallClick: (DownloadedGameEntity) -> Unit,
    private val onDeleteClick: (DownloadedGameEntity) -> Unit,
    private val onCancelClick: (DownloadedGameEntity) -> Unit,
    private val onRetryClick: (DownloadedGameEntity) -> Unit
) : ListAdapter<DownloadedGameEntity, DownloadedGameAdapter.GameViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GameViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_downloaded_game, parent, false)
        return GameViewHolder(view)
    }

    override fun onBindViewHolder(holder: GameViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class GameViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivIcon: ImageView = itemView.findViewById(R.id.iv_game_icon)
        private val tvTitle: TextView = itemView.findViewById(R.id.tv_game_title)
        private val tvPackage: TextView = itemView.findViewById(R.id.tv_game_package)
        private val tvSizeDate: TextView = itemView.findViewById(R.id.tv_game_size_date)
        private val tvStatus: TextView = itemView.findViewById(R.id.tv_game_status)
        private val pbProgress: ProgressBar = itemView.findViewById(R.id.pb_item_download_progress)
        private val btnInstall: Button = itemView.findViewById(R.id.btn_install_game)
        private val btnDelete: Button = itemView.findViewById(R.id.btn_delete_game)

        fun bind(game: DownloadedGameEntity) {
            tvTitle.text = game.title

            when {
                game.isDownloading -> {
                    tvPackage.text = "Downloading from Cosmo Store..."
                    val formattedSize = if (game.fileSize > 0) Formatter.formatFileSize(context, game.fileSize) else "Connecting..."
                    tvSizeDate.text = "$formattedSize • In Progress"

                    pbProgress.visibility = View.VISIBLE
                    pbProgress.progress = game.progress
                    pbProgress.isIndeterminate = (game.progress <= 0 && game.fileSize <= 0)

                    tvStatus.text = "Downloading ${game.progress}%"
                    tvStatus.setTextColor(ContextCompat.getColor(context, R.color.secondary))

                    btnInstall.visibility = View.GONE

                    btnDelete.visibility = View.VISIBLE
                    btnDelete.text = "Cancel"
                    btnDelete.setTextColor(ContextCompat.getColor(context, R.color.accent_rose))
                    btnDelete.setOnClickListener {
                        onCancelClick(game)
                    }

                    ivIcon.setImageResource(R.drawable.ic_download)
                }

                game.isFailed -> {
                    tvPackage.text = "Download Interrupted"
                    tvSizeDate.text = "Failed to complete download"

                    pbProgress.visibility = View.GONE
                    tvStatus.text = "Download Failed"
                    tvStatus.setTextColor(ContextCompat.getColor(context, R.color.accent_rose))

                    btnInstall.visibility = View.VISIBLE
                    btnInstall.text = "Retry"
                    btnInstall.isEnabled = true
                    btnInstall.setOnClickListener {
                        onRetryClick(game)
                    }

                    btnDelete.visibility = View.VISIBLE
                    btnDelete.text = "Remove"
                    btnDelete.setTextColor(ContextCompat.getColor(context, R.color.text_muted))
                    btnDelete.setOnClickListener {
                        onDeleteClick(game)
                    }

                    ivIcon.setImageResource(R.drawable.ic_games)
                }

                else -> {
                    // Completed status
                    pbProgress.visibility = View.GONE
                    tvPackage.text = "${game.packageName} • v${game.versionName}"

                    val formattedSize = Formatter.formatFileSize(context, game.fileSize)
                    val formattedDate = DateFormat.getDateFormat(context).format(Date(game.downloadDate))
                    tvSizeDate.text = "$formattedSize • $formattedDate"

                    val file = File(game.filePath)
                    if (!file.exists()) {
                        tvStatus.text = "File Missing from Disk"
                        tvStatus.setTextColor(ContextCompat.getColor(context, R.color.accent_rose))
                        btnInstall.visibility = View.VISIBLE
                        btnInstall.isEnabled = false
                        btnInstall.text = "Missing"
                        ivIcon.setImageResource(R.drawable.ic_games)
                    } else {
                        tvStatus.text = "Ready to Install"
                        tvStatus.setTextColor(ContextCompat.getColor(context, R.color.accent_green))
                        btnInstall.visibility = View.VISIBLE
                        btnInstall.isEnabled = true
                        btnInstall.text = "Install"

                        // Extract and set real App Icon from APK
                        val extractedIcon = ApkMetadataHelper.loadIcon(context, file)
                        if (extractedIcon != null) {
                            ivIcon.setImageDrawable(extractedIcon)
                        } else {
                            ivIcon.setImageResource(R.drawable.ic_games)
                        }
                    }

                    btnInstall.setOnClickListener {
                        onInstallClick(game)
                    }

                    btnDelete.visibility = View.VISIBLE
                    btnDelete.text = "Delete"
                    btnDelete.setTextColor(ContextCompat.getColor(context, R.color.accent_rose))
                    btnDelete.setOnClickListener {
                        onDeleteClick(game)
                    }
                }
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<DownloadedGameEntity>() {
            override fun areItemsTheSame(oldItem: DownloadedGameEntity, newItem: DownloadedGameEntity): Boolean {
                return oldItem.packageName == newItem.packageName
            }

            override fun areContentsTheSame(oldItem: DownloadedGameEntity, newItem: DownloadedGameEntity): Boolean {
                return oldItem == newItem
            }
        }
    }
}

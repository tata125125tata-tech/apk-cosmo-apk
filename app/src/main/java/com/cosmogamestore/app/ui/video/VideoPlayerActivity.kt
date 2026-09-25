package com.cosmogamestore.app.ui.video

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.cosmogamestore.app.R
import com.cosmogamestore.app.core.downloader.VideoDownloadWorker
import com.cosmogamestore.app.data.db.VideoEntity
import com.cosmogamestore.app.data.repository.VideoRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

class VideoPlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VIDEO_ID = "extra_video_id"
        const val EXTRA_VIDEO_URL = "extra_video_url"
        const val EXTRA_VIDEO_TITLE = "extra_video_title"
        const val EXTRA_COVER_URL = "extra_cover_url"
        const val EXTRA_CATEGORY = "extra_category"
        const val EXTRA_VIEWS = "extra_views"
        const val EXTRA_LIKES = "extra_likes"

        fun createIntent(
            context: Context,
            id: Long,
            videoUrl: String,
            title: String,
            coverUrl: String,
            category: String,
            views: Long,
            likes: Long
        ): Intent {
            return Intent(context, VideoPlayerActivity::class.java).apply {
                putExtra(EXTRA_VIDEO_ID, id)
                putExtra(EXTRA_VIDEO_URL, videoUrl)
                putExtra(EXTRA_VIDEO_TITLE, title)
                putExtra(EXTRA_COVER_URL, coverUrl)
                putExtra(EXTRA_CATEGORY, category)
                putExtra(EXTRA_VIEWS, views)
                putExtra(EXTRA_LIKES, likes)
            }
        }
    }

    private var player: ExoPlayer? = null
    private lateinit var playerFrame: FrameLayout
    private lateinit var playerView: PlayerView
    private lateinit var playerLoading: ProgressBar
    private lateinit var playerErrorLayout: LinearLayout
    private lateinit var btnPlayerRetry: Button
    private lateinit var detailsScrollView: View
    private lateinit var btnFullscreenToggle: ImageView

    private lateinit var tvHeaderTitle: TextView
    private lateinit var tvVideoTitle: TextView
    private lateinit var tvVideoCategory: TextView
    private lateinit var tvVideoViews: TextView
    private lateinit var tvVideoSource: TextView
    private lateinit var tvVideoDesc: TextView
    private lateinit var tvOfflineNotice: TextView

    private lateinit var btnActionLike: LinearLayout
    private lateinit var ivActionLike: ImageView
    private lateinit var tvActionLikesCount: TextView

    private lateinit var btnActionDownload: LinearLayout
    private lateinit var ivActionDownload: ImageView
    private lateinit var tvActionDownload: TextView
    private lateinit var layoutDownloadProgress: LinearLayout
    private lateinit var tvDownloadPercent: TextView
    private lateinit var pbDownloadProgress: ProgressBar

    private lateinit var repository: VideoRepository
    private var videoId: Long = -1L
    private var videoUrl: String = ""
    private var videoTitle: String = ""
    private var currentEntity: VideoEntity? = null
    private var hasRecordedView: Boolean = false
    private var isFullscreen: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)

        repository = VideoRepository.getInstance(this)
        parseIntentData()
        initViews()
        setupListeners()
        setupBackHandling()
        observeVideoEntity()
        initializePlayer()

        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            applyFullscreenLayout(true)
        }
    }

    private fun parseIntentData() {
        val appLinkData: Uri? = intent.data
        if (appLinkData != null) {
            val lastSegment = appLinkData.lastPathSegment
            val parsedId = lastSegment?.toLongOrNull()
            if (parsedId != null) {
                videoId = parsedId
            }
        }

        if (videoId == -1L) {
            videoId = intent.getLongExtra(EXTRA_VIDEO_ID, -1L)
        }
        videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL) ?: ""
        videoTitle = intent.getStringExtra(EXTRA_VIDEO_TITLE) ?: "Cosmo Video"
    }

    private fun initViews() {
        playerFrame = findViewById(R.id.player_frame)
        playerView = findViewById(R.id.player_view)
        playerLoading = findViewById(R.id.player_loading)
        playerErrorLayout = findViewById(R.id.player_error_layout)
        btnPlayerRetry = findViewById(R.id.btn_player_retry)
        detailsScrollView = findViewById(R.id.details_scroll_view)
        btnFullscreenToggle = findViewById(R.id.btn_fullscreen_toggle)

        tvHeaderTitle = findViewById(R.id.tv_player_header_title)
        tvVideoTitle = findViewById(R.id.tv_video_title)
        tvVideoCategory = findViewById(R.id.tv_video_category)
        tvVideoViews = findViewById(R.id.tv_video_views)
        tvVideoSource = findViewById(R.id.tv_video_source)
        tvVideoDesc = findViewById(R.id.tv_video_desc)
        tvOfflineNotice = findViewById(R.id.tv_offline_location_notice)

        btnActionLike = findViewById(R.id.btn_action_like)
        ivActionLike = findViewById(R.id.iv_action_like)
        tvActionLikesCount = findViewById(R.id.tv_action_likes_count)

        btnActionDownload = findViewById(R.id.btn_action_download)
        ivActionDownload = findViewById(R.id.iv_action_download)
        tvActionDownload = findViewById(R.id.tv_action_download)
        layoutDownloadProgress = findViewById(R.id.layout_download_progress)
        tvDownloadPercent = findViewById(R.id.tv_download_percent)
        pbDownloadProgress = findViewById(R.id.pb_download_progress)

        tvHeaderTitle.text = videoTitle
        tvVideoTitle.text = videoTitle
        val category = intent.getStringExtra(EXTRA_CATEGORY) ?: "Gaming"
        tvVideoCategory.text = category.uppercase()
        val views = intent.getLongExtra(EXTRA_VIEWS, 0L)
        tvVideoViews.text = "$views views"
        val likes = intent.getLongExtra(EXTRA_LIKES, 0L)
        tvActionLikesCount.text = likes.toString()
    }

    private fun setupListeners() {
        findViewById<View>(R.id.btn_player_back).setOnClickListener {
            handleBackAction()
        }

        findViewById<View>(R.id.btn_player_header_share).setOnClickListener {
            shareVideo()
        }

        findViewById<View>(R.id.btn_action_share).setOnClickListener {
            shareVideo()
        }

        btnFullscreenToggle.setOnClickListener {
            toggleFullscreen()
        }

        btnActionLike.setOnClickListener {
            handleLikeAction()
        }

        btnActionDownload.setOnClickListener {
            handleDownloadAction()
        }

        btnPlayerRetry.setOnClickListener {
            playerErrorLayout.visibility = View.GONE
            initializePlayer()
        }
    }

    private fun setupBackHandling() {
        onBackPressedDispatcher.addCallback(this) {
            handleBackAction()
        }
    }

    private fun handleBackAction() {
        if (isFullscreen) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            applyFullscreenLayout(false)
        } else {
            finish()
        }
    }

    private fun toggleFullscreen() {
        if (isFullscreen) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            applyFullscreenLayout(false)
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            applyFullscreenLayout(true)
        }
    }

    private fun applyFullscreenLayout(fullscreen: Boolean) {
        isFullscreen = fullscreen
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)

        if (fullscreen) {
            windowInsetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

            val params = playerFrame.layoutParams as LinearLayout.LayoutParams
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            params.height = ViewGroup.LayoutParams.MATCH_PARENT
            params.weight = 1f
            playerFrame.layoutParams = params

            detailsScrollView.visibility = View.GONE
            btnFullscreenToggle.setImageResource(R.drawable.ic_fullscreen_exit)
        } else {
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())

            val params = playerFrame.layoutParams as LinearLayout.LayoutParams
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            params.height = (240 * resources.displayMetrics.density).toInt()
            params.weight = 0f
            playerFrame.layoutParams = params

            detailsScrollView.visibility = View.VISIBLE
            btnFullscreenToggle.setImageResource(R.drawable.ic_fullscreen_enter)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            applyFullscreenLayout(true)
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            applyFullscreenLayout(false)
        }
    }

    private fun observeVideoEntity() {
        if (videoId <= 0) return

        lifecycleScope.launch {
            repository.getVideoFlowById(videoId).collectLatest { entity ->
                if (entity != null) {
                    currentEntity = entity
                    updateUIWithEntity(entity)
                } else {
                    val fetched = repository.getVideoById(videoId)
                    if (fetched != null) {
                        currentEntity = fetched
                        updateUIWithEntity(fetched)
                    }
                }
            }
        }

        val workManager = WorkManager.getInstance(this)
        workManager.getWorkInfosForUniqueWorkLiveData("video_download_$videoId")
            .observe(this) { workInfos ->
                val workInfo = workInfos?.firstOrNull() ?: return@observe
                when (workInfo.state) {
                    WorkInfo.State.RUNNING -> {
                        val progress = workInfo.progress.getInt("progress", 0)
                        layoutDownloadProgress.visibility = View.VISIBLE
                        pbDownloadProgress.progress = progress
                        tvDownloadPercent.text = "$progress%"
                        tvActionDownload.text = "Saving..."
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        layoutDownloadProgress.visibility = View.GONE
                        tvActionDownload.text = "Offline Ready"
                        ivActionDownload.setImageResource(R.drawable.ic_nav_library)
                        ivActionDownload.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.secondary))
                        tvOfflineNotice.visibility = View.VISIBLE
                        tvOfflineNotice.text = "Saved in My Library > Videos for offline viewing without internet."
                    }
                    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                        layoutDownloadProgress.visibility = View.GONE
                        tvActionDownload.text = "Offline"
                        ivActionDownload.setImageResource(R.drawable.ic_download)
                        ivActionDownload.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.white))
                    }
                    else -> {}
                }
            }
    }

    private fun updateUIWithEntity(entity: VideoEntity) {
        tvHeaderTitle.text = entity.title
        tvVideoTitle.text = entity.title
        tvVideoCategory.text = entity.category.uppercase()
        tvVideoViews.text = "${entity.views} views"
        tvActionLikesCount.text = "${entity.likes}"

        if (entity.description.isNotBlank()) {
            tvVideoDesc.text = entity.description
        } else {
            tvVideoDesc.text = "Explore exclusive indie gaming content, developer breakdowns, and trailers on Cosmo Video Tube."
        }

        if (entity.isLiked) {
            ivActionLike.setImageResource(R.drawable.ic_liked)
            ivActionLike.imageTintList = null
            tvActionLikesCount.setTextColor(ContextCompat.getColor(this, R.color.secondary))
        } else {
            ivActionLike.setImageResource(R.drawable.ic_like)
            ivActionLike.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.white))
            tvActionLikesCount.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        }

        if (entity.isDownloaded && entity.offlinePath != null && File(entity.offlinePath).exists()) {
            tvVideoSource.text = "Offline Storage"
            tvVideoSource.setTextColor(ContextCompat.getColor(this, R.color.secondary))
            tvActionDownload.text = "Downloaded"
            ivActionDownload.setImageResource(R.drawable.ic_nav_library)
            ivActionDownload.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.secondary))
            tvOfflineNotice.visibility = View.VISIBLE
            layoutDownloadProgress.visibility = View.GONE
        } else if (entity.isDownloading) {
            tvActionDownload.text = "Saving (${entity.downloadProgress}%)"
            layoutDownloadProgress.visibility = View.VISIBLE
            pbDownloadProgress.progress = entity.downloadProgress
            tvDownloadPercent.text = "${entity.downloadProgress}%"
        } else {
            tvVideoSource.text = "Online Stream"
            tvActionDownload.text = "Offline"
            ivActionDownload.setImageResource(R.drawable.ic_download)
            ivActionDownload.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.white))
            tvOfflineNotice.visibility = View.GONE
            layoutDownloadProgress.visibility = View.GONE
        }

        if (videoUrl.isBlank()) {
            videoUrl = entity.videoUrl
            initializePlayer()
        }
    }

    private fun initializePlayer() {
        if (videoUrl.isBlank() && (currentEntity?.offlinePath == null)) return

        player?.release()
        playerLoading.visibility = View.VISIBLE
        playerErrorLayout.visibility = View.GONE

        val newPlayer = ExoPlayer.Builder(this).build()
        player = newPlayer
        playerView.player = newPlayer

        val uriToPlay = if (currentEntity?.isDownloaded == true && currentEntity?.offlinePath != null && File(currentEntity!!.offlinePath!!).exists()) {
            Uri.fromFile(File(currentEntity!!.offlinePath!!))
        } else {
            Uri.parse(videoUrl)
        }

        val mediaItem = MediaItem.fromUri(uriToPlay)
        newPlayer.setMediaItem(mediaItem)
        newPlayer.prepare()
        newPlayer.playWhenReady = true

        newPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        playerLoading.visibility = View.VISIBLE
                    }
                    Player.STATE_READY -> {
                        playerLoading.visibility = View.GONE
                        if (!hasRecordedView && videoId > 0) {
                            hasRecordedView = true
                            lifecycleScope.launch {
                                repository.incrementView(videoId)
                            }
                        }
                    }
                    Player.STATE_ENDED -> {
                        playerLoading.visibility = View.GONE
                    }
                    Player.STATE_IDLE -> {
                        playerLoading.visibility = View.GONE
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                playerLoading.visibility = View.GONE
                playerErrorLayout.visibility = View.VISIBLE
                findViewById<TextView>(R.id.tv_player_error_msg).text = "Error streaming video: ${error.message}"
            }
        })
    }

    private fun handleLikeAction() {
        if (videoId <= 0) return

        if (currentEntity?.isLiked == true) {
            Toast.makeText(this, "You liked this video!", Toast.LENGTH_SHORT).show()
            return
        }

        ivActionLike.setImageResource(R.drawable.ic_liked)
        ivActionLike.imageTintList = null
        val newLikes = (currentEntity?.likes ?: 0L) + 1
        tvActionLikesCount.text = "$newLikes"
        tvActionLikesCount.setTextColor(ContextCompat.getColor(this, R.color.secondary))

        lifecycleScope.launch {
            repository.incrementLike(videoId)
        }
    }

    private fun handleDownloadAction() {
        if (videoId <= 0 || videoUrl.isBlank()) return

        if (currentEntity?.isDownloaded == true && currentEntity?.offlinePath != null && File(currentEntity!!.offlinePath!!).exists()) {
            Toast.makeText(this, "Video is already downloaded for offline playback!", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Starting offline video download in background...", Toast.LENGTH_SHORT).show()
        layoutDownloadProgress.visibility = View.VISIBLE
        tvActionDownload.text = "Saving..."

        val inputData = Data.Builder()
            .putLong(VideoDownloadWorker.KEY_VIDEO_ID, videoId)
            .putString(VideoDownloadWorker.KEY_VIDEO_URL, videoUrl)
            .putString(VideoDownloadWorker.KEY_VIDEO_TITLE, videoTitle)
            .build()

        val downloadRequest = OneTimeWorkRequestBuilder<VideoDownloadWorker>()
            .setInputData(inputData)
            .addTag("video_$videoId")
            .build()

        WorkManager.getInstance(this).enqueueUniqueWork(
            "video_download_$videoId",
            ExistingWorkPolicy.KEEP,
            downloadRequest
        )
    }

    private fun shareVideo() {
        val shareTitle = currentEntity?.title ?: videoTitle
        val deepLinkUrl = "https://cosmo-game.pages.dev/video/$videoId"
        val customUri = "cosmo://video/$videoId"

        val shareText = "🎮 Watch \"$shareTitle\" on Cosmo Video Tube!\n" +
                "Direct Link: $deepLinkUrl\n" +
                "App Deep Link: $customUri"

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_SUBJECT, shareTitle)
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }

        val shareIntent = Intent.createChooser(sendIntent, "Share Video via")
        startActivity(shareIntent)
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
        playerView.player = null
    }
}

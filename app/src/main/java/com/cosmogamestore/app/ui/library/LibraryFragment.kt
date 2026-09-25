package com.cosmogamestore.app.ui.library

import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cosmogamestore.app.MainActivity
import com.cosmogamestore.app.R
import com.cosmogamestore.app.data.ApkMetadataHelper
import com.cosmogamestore.app.data.db.AppDatabase
import com.cosmogamestore.app.data.db.DownloadedGameEntity
import com.cosmogamestore.app.data.db.VideoEntity
import com.cosmogamestore.app.data.repository.VideoRepository
import com.cosmogamestore.app.ui.video.VideoPlayerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * LibraryFragment:
 * Dual-tab offline content hub managing downloaded APK games and offline cached videos.
 */
class LibraryFragment : Fragment() {

    private lateinit var tabBtnApks: TextView
    private lateinit var tabBtnVideos: TextView
    private lateinit var containerApks: View
    private lateinit var containerVideos: View

    // APK Games Tab Views
    private lateinit var rvGames: RecyclerView
    private lateinit var emptyGamesView: View
    private lateinit var tvLibraryCount: TextView
    private lateinit var btnScan: Button
    private lateinit var btnEmptyBrowse: Button
    private lateinit var gameAdapter: DownloadedGameAdapter

    // Videos Tab Views
    private lateinit var rvVideos: RecyclerView
    private lateinit var emptyVideosView: View
    private lateinit var btnEmptyBrowseVideos: Button
    private lateinit var videoAdapter: DownloadedVideoAdapter

    private var activeTab: Int = TAB_APKS
    private val db by lazy { AppDatabase.getDatabase(requireContext()) }
    private val videoRepository by lazy { VideoRepository.getInstance(requireContext()) }

    companion object {
        private const val TAB_APKS = 0
        private const val TAB_VIDEOS = 1

        fun newInstance() = LibraryFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_library, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupGameRecyclerView()
        setupVideoRecyclerView()
        setupTabSwitching()
        observeGames()
        observeVideos()
        scanLocalDownloads()
    }

    override fun onResume() {
        super.onResume()
        scanLocalDownloads()
    }

    private fun initViews(view: View) {
        tabBtnApks = view.findViewById(R.id.tab_btn_apks)
        tabBtnVideos = view.findViewById(R.id.tab_btn_videos)
        containerApks = view.findViewById(R.id.container_apks)
        containerVideos = view.findViewById(R.id.container_videos)

        // APK Views
        rvGames = view.findViewById(R.id.rv_library_games)
        emptyGamesView = view.findViewById(R.id.library_empty_view)
        tvLibraryCount = view.findViewById(R.id.tv_library_count)
        btnScan = view.findViewById(R.id.btn_scan_library)
        btnEmptyBrowse = view.findViewById(R.id.btn_empty_browse)

        // Video Views
        rvVideos = view.findViewById(R.id.rv_library_videos)
        emptyVideosView = view.findViewById(R.id.library_videos_empty_view)
        btnEmptyBrowseVideos = view.findViewById(R.id.btn_empty_browse_videos)

        btnScan.setOnClickListener {
            scanLocalDownloads(showToast = true)
        }

        btnEmptyBrowse.setOnClickListener {
            (activity as? MainActivity)?.switchToBrowseTab()
        }

        btnEmptyBrowseVideos.setOnClickListener {
            (activity as? MainActivity)?.switchToVideoTubeTab()
        }
    }

    private fun setupTabSwitching() {
        tabBtnApks.setOnClickListener {
            selectTab(TAB_APKS)
        }

        tabBtnVideos.setOnClickListener {
            selectTab(TAB_VIDEOS)
        }
    }

    private fun selectTab(tab: Int) {
        activeTab = tab
        val context = requireContext()

        if (tab == TAB_APKS) {
            tabBtnApks.setBackgroundResource(R.drawable.bg_badge)
            tabBtnApks.setTextColor(ContextCompat.getColor(context, R.color.secondary))

            tabBtnVideos.setBackgroundResource(0)
            tabBtnVideos.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))

            containerApks.visibility = View.VISIBLE
            containerVideos.visibility = View.GONE
            btnScan.visibility = View.VISIBLE
        } else {
            tabBtnVideos.setBackgroundResource(R.drawable.bg_badge)
            tabBtnVideos.setTextColor(ContextCompat.getColor(context, R.color.secondary))

            tabBtnApks.setBackgroundResource(0)
            tabBtnApks.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))

            containerApks.visibility = View.GONE
            containerVideos.visibility = View.VISIBLE
            btnScan.visibility = View.GONE
        }
    }

    private fun setupGameRecyclerView() {
        gameAdapter = DownloadedGameAdapter(
            context = requireContext(),
            onInstallClick = { game ->
                val file = File(game.filePath)
                if (file.exists()) {
                    (activity as? MainActivity)?.launchPackageInstaller(file)
                } else {
                    Toast.makeText(requireContext(), "Package file not found on disk", Toast.LENGTH_SHORT).show()
                }
            },
            onDeleteClick = { game ->
                confirmDeleteGame(game)
            },
            onCancelClick = { game ->
                (activity as? MainActivity)?.cancelActiveDownload(game.downloadId)
            },
            onRetryClick = { game ->
                if (game.downloadUrl.isNotBlank()) {
                    (activity as? MainActivity)?.retryDownload(game.downloadUrl, game.title)
                } else {
                    Toast.makeText(requireContext(), "Download URL not available", Toast.LENGTH_SHORT).show()
                }
            }
        )

        rvGames.layoutManager = LinearLayoutManager(requireContext())
        rvGames.adapter = gameAdapter
    }

    private fun setupVideoRecyclerView() {
        videoAdapter = DownloadedVideoAdapter(
            onPlayClick = { video ->
                val intent = VideoPlayerActivity.createIntent(
                    context = requireContext(),
                    id = video.id,
                    videoUrl = video.videoUrl,
                    title = video.title,
                    coverUrl = video.coverUrl,
                    category = video.category,
                    views = video.views,
                    likes = video.likes
                )
                startActivity(intent)
            },
            onDeleteClick = { video ->
                confirmDeleteVideo(video)
            }
        )

        rvVideos.layoutManager = LinearLayoutManager(requireContext())
        rvVideos.adapter = videoAdapter
    }

    private fun observeGames() {
        viewLifecycleOwner.lifecycleScope.launch {
            db.gameDao().getAllGamesFlow().collectLatest { games ->
                gameAdapter.submitList(games)
                updateGameEmptyState(games)
            }
        }
    }

    private fun observeVideos() {
        viewLifecycleOwner.lifecycleScope.launch {
            videoRepository.getDownloadedVideosFlow().collectLatest { videos ->
                videoAdapter.submitList(videos)
                if (videos.isEmpty()) {
                    emptyVideosView.visibility = View.VISIBLE
                    rvVideos.visibility = View.GONE
                } else {
                    emptyVideosView.visibility = View.GONE
                    rvVideos.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun updateGameEmptyState(games: List<DownloadedGameEntity>) {
        if (games.isEmpty()) {
            emptyGamesView.visibility = View.VISIBLE
            rvGames.visibility = View.GONE
            tvLibraryCount.text = "0 Games in Library"
        } else {
            emptyGamesView.visibility = View.GONE
            rvGames.visibility = View.VISIBLE
            val downloadingCount = games.count { it.isDownloading }
            val completedCount = games.count { it.isCompleted }

            val statusText = when {
                downloadingCount > 0 && completedCount > 0 ->
                    "$downloadingCount downloading • $completedCount ready"
                downloadingCount > 0 ->
                    "$downloadingCount downloading"
                else ->
                    "$completedCount Game${if (completedCount > 1) "s" else ""} Ready"
            }
            tvLibraryCount.text = statusText
        }
    }

    fun scanLocalDownloads(showToast: Boolean = false) {
        viewLifecycleOwner.lifecycleScope.launch {
            val context = context ?: return@launch
            withContext(Dispatchers.IO) {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val apkFiles = downloadsDir?.listFiles { file ->
                    file.isFile && file.name.endsWith(".apk", ignoreCase = true)
                } ?: emptyArray()

                for (apk in apkFiles) {
                    val extracted = ApkMetadataHelper.extract(context, apk)
                    if (extracted != null) {
                        db.gameDao().insertGame(extracted.entity)
                    }
                }

                val existingInDb = db.gameDao().getAllGames()
                for (record in existingInDb) {
                    if (record.isCompleted && !File(record.filePath).exists()) {
                        db.gameDao().deleteByFilePath(record.filePath)
                    }
                }
            }

            if (showToast && isAdded) {
                Toast.makeText(context, "Library synchronized with storage", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmDeleteGame(game: DownloadedGameEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Game")
            .setMessage("Are you sure you want to remove ${game.title} from library?")
            .setPositiveButton("Delete") { _, _ ->
                deleteGame(game)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteGame(game: DownloadedGameEntity) {
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val file = File(game.filePath)
                    if (file.exists()) {
                        file.delete()
                    }
                    db.gameDao().deleteByPackageName(game.packageName)
                    if (game.downloadId != -1L) {
                        db.gameDao().deleteByDownloadId(game.downloadId)
                    }
                } catch (e: Exception) {
                    Log.e("LibraryFragment", "Error deleting game", e)
                }
            }
            Toast.makeText(requireContext(), "${game.title} removed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDeleteVideo(video: VideoEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Offline Video")
            .setMessage("Are you sure you want to delete \"${video.title}\" from offline storage?")
            .setPositiveButton("Delete") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    videoRepository.deleteOfflineVideo(video)
                    Toast.makeText(requireContext(), "Offline video deleted", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

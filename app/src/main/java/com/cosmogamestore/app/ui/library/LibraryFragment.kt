package com.cosmogamestore.app.ui.library

import android.content.Context
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
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cosmogamestore.app.MainActivity
import com.cosmogamestore.app.R
import com.cosmogamestore.app.data.ApkMetadataHelper
import com.cosmogamestore.app.data.db.AppDatabase
import com.cosmogamestore.app.data.db.DownloadedGameEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * LibraryFragment:
 * Displays downloaded and in-progress games with app icons extracted directly from the local APK files,
 * real-time download status, persistent metadata stored via Room Database, and Install/Delete actions.
 */
class LibraryFragment : Fragment() {

    private lateinit var rvGames: RecyclerView
    private lateinit var emptyView: View
    private lateinit var tvLibraryCount: TextView
    private lateinit var btnScan: Button
    private lateinit var btnEmptyBrowse: Button
    private lateinit var adapter: DownloadedGameAdapter

    private val db by lazy { AppDatabase.getDatabase(requireContext()) }

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
        setupRecyclerView()
        observeGames()
        scanLocalDownloads()
    }

    override fun onResume() {
        super.onResume()
        scanLocalDownloads()
    }

    private fun initViews(view: View) {
        rvGames = view.findViewById(R.id.rv_library_games)
        emptyView = view.findViewById(R.id.library_empty_view)
        tvLibraryCount = view.findViewById(R.id.tv_library_count)
        btnScan = view.findViewById(R.id.btn_scan_library)
        btnEmptyBrowse = view.findViewById(R.id.btn_empty_browse)

        btnScan.setOnClickListener {
            scanLocalDownloads(showToast = true)
        }

        btnEmptyBrowse.setOnClickListener {
            (activity as? MainActivity)?.switchToBrowseTab()
        }
    }

    private fun setupRecyclerView() {
        adapter = DownloadedGameAdapter(
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
                confirmDelete(game)
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
        rvGames.adapter = adapter
    }

    private fun observeGames() {
        viewLifecycleOwner.lifecycleScope.launch {
            db.gameDao().getAllGamesFlow().collectLatest { games ->
                adapter.submitList(games)
                updateEmptyState(games)
            }
        }
    }

    private fun updateEmptyState(games: List<DownloadedGameEntity>) {
        if (games.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            rvGames.visibility = View.GONE
            tvLibraryCount.text = "0 Games in Library"
        } else {
            emptyView.visibility = View.GONE
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

    /**
     * Scans the system Downloads folder for any .apk files, extracts metadata and icons,
     * and saves/synchronizes them in Room Database.
     */
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

                // Clean up database entries for files that no longer exist on disk (only for completed items)
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

    private fun confirmDelete(game: DownloadedGameEntity) {
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

    companion object {
        fun newInstance() = LibraryFragment()
    }
}

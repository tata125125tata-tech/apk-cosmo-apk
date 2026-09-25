package com.cosmogamestore.app.ui.video

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.cosmogamestore.app.R
import com.cosmogamestore.app.data.db.VideoEntity
import com.cosmogamestore.app.data.repository.VideoRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class VideoTubeFragment : Fragment() {

    companion object {
        fun newInstance(): VideoTubeFragment {
            return VideoTubeFragment()
        }
    }

    private lateinit var repository: VideoRepository
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var rvVideos: RecyclerView
    private lateinit var rvCategories: RecyclerView
    private lateinit var etSearch: EditText
    private lateinit var btnClearSearch: ImageView
    private lateinit var emptyView: LinearLayout
    private lateinit var tvEmptyMsg: TextView
    private lateinit var btnRetry: Button
    private lateinit var tvFeedStatus: TextView

    private lateinit var videoAdapter: VideoTubeAdapter
    private lateinit var categoryAdapter: VideoCategoryAdapter

    private var selectedCategory: String = "All"
    private var currentSearchQuery: String = ""
    private var videosJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_video_tube, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = VideoRepository.getInstance(requireContext())

        initViews(view)
        setupAdapters()
        setupListeners()
        loadCategories()
        observeVideos()
        refreshFeed(showLoading = true)
    }

    private fun initViews(root: View) {
        swipeRefresh = root.findViewById(R.id.swipe_refresh_videos)
        rvVideos = root.findViewById(R.id.rv_video_tube_feed)
        rvCategories = root.findViewById(R.id.rv_video_categories)
        etSearch = root.findViewById(R.id.et_video_search)
        btnClearSearch = root.findViewById(R.id.btn_clear_video_search)
        emptyView = root.findViewById(R.id.video_tube_empty_view)
        tvEmptyMsg = root.findViewById(R.id.tv_empty_video_msg)
        btnRetry = root.findViewById(R.id.btn_retry_video_feed)
        tvFeedStatus = root.findViewById(R.id.tv_feed_status)

        swipeRefresh.setColorSchemeResources(R.color.secondary, R.color.primary)
    }

    private fun setupAdapters() {
        videoAdapter = VideoTubeAdapter(
            onVideoClick = { video ->
                openVideoPlayer(video)
            },
            onShareClick = { video ->
                shareVideo(video)
            }
        )
        rvVideos.layoutManager = LinearLayoutManager(requireContext())
        rvVideos.adapter = videoAdapter

        categoryAdapter = VideoCategoryAdapter { category ->
            selectedCategory = category
            observeVideos()
            refreshFeed(showLoading = false)
        }
        rvCategories.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rvCategories.adapter = categoryAdapter
    }

    private fun setupListeners() {
        swipeRefresh.setOnRefreshListener {
            refreshFeed(showLoading = false)
        }

        btnRetry.setOnClickListener {
            refreshFeed(showLoading = true)
        }

        btnClearSearch.setOnClickListener {
            etSearch.setText("")
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                if (currentSearchQuery != query) {
                    currentSearchQuery = query
                    observeVideos()
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                etSearch.clearFocus()
                true
            } else {
                false
            }
        }
    }

    private fun loadCategories() {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = repository.getCategories()
            val categoryList = mutableListOf("All")
            result.onSuccess { dtoList ->
                dtoList.forEach { categoryList.add(it.name) }
            }.onFailure {
                // Fallback default gaming categories
                categoryList.addAll(listOf("Trailers", "Esports", "Indie Dev", "Solo", "Gameplay"))
            }
            categoryAdapter.setCategories(categoryList.distinct(), selectedCategory)
        }
    }

    private fun observeVideos() {
        videosJob?.cancel()
        videosJob = viewLifecycleOwner.lifecycleScope.launch {
            repository.getVideosFlow(selectedCategory, currentSearchQuery).collectLatest { list ->
                videoAdapter.submitList(list)
                if (list.isEmpty()) {
                    emptyView.visibility = View.VISIBLE
                    if (currentSearchQuery.isNotBlank()) {
                        tvEmptyMsg.text = "No gaming videos found matching \"$currentSearchQuery\"."
                    } else if (selectedCategory != "All") {
                        tvEmptyMsg.text = "No videos available in \"$selectedCategory\" yet."
                    } else {
                        tvEmptyMsg.text = "Pull down to refresh or check connection to stream from Cloudflare."
                    }
                } else {
                    emptyView.visibility = View.GONE
                }
            }
        }
    }

    private fun refreshFeed(showLoading: Boolean) {
        if (showLoading) {
            swipeRefresh.isRefreshing = true
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val result = repository.refreshVideos(selectedCategory, currentSearchQuery)
            swipeRefresh.isRefreshing = false

            result.onSuccess {
                tvFeedStatus.text = "CLOUDFLARE LIVE"
            }.onFailure {
                tvFeedStatus.text = "OFFLINE CACHE"
                if (videoAdapter.itemCount == 0) {
                    emptyView.visibility = View.VISIBLE
                    tvEmptyMsg.text = "Unable to connect to Cloudflare Worker API. Showing cached videos if available."
                }
            }
        }
    }

    private fun openVideoPlayer(video: VideoEntity) {
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
    }

    private fun shareVideo(video: VideoEntity) {
        val deepLinkUrl = "https://cosmo-game.pages.dev/video/${video.id}"
        val customUri = "cosmo://video/${video.id}"
        val shareText = "🎮 Watch \"${video.title}\" on Cosmo Video Tube!\n" +
                "Direct Link: $deepLinkUrl\n" +
                "App Deep Link: $customUri"

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_SUBJECT, video.title)
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        startActivity(Intent.createChooser(sendIntent, "Share Video via"))
    }
}

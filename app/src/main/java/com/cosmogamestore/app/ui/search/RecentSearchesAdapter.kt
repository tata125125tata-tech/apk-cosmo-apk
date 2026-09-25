package com.cosmogamestore.app.ui.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cosmogamestore.app.R
import com.cosmogamestore.app.data.db.SearchHistoryEntity

class RecentSearchesAdapter(
    private val onItemClick: (String) -> Unit,
    private val onDeleteClick: (String) -> Unit
) : ListAdapter<SearchHistoryEntity, RecentSearchesAdapter.SearchViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recent_search, parent, false)
        return SearchViewHolder(view)
    }

    override fun onBindViewHolder(holder: SearchViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SearchViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvQuery: TextView = itemView.findViewById(R.id.tv_search_query)
        private val btnDelete: ImageView = itemView.findViewById(R.id.btn_delete_search)

        fun bind(item: SearchHistoryEntity) {
            tvQuery.text = item.query

            itemView.setOnClickListener {
                onItemClick(item.query)
            }

            btnDelete.setOnClickListener {
                onDeleteClick(item.query)
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<SearchHistoryEntity>() {
            override fun areItemsTheSame(oldItem: SearchHistoryEntity, newItem: SearchHistoryEntity): Boolean {
                return oldItem.query == newItem.query
            }

            override fun areContentsTheSame(oldItem: SearchHistoryEntity, newItem: SearchHistoryEntity): Boolean {
                return oldItem == newItem
            }
        }
    }
}

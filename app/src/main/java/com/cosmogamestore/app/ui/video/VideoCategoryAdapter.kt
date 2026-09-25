package com.cosmogamestore.app.ui.video

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.cosmogamestore.app.R

class VideoCategoryAdapter(
    private val onCategorySelected: (String) -> Unit
) : RecyclerView.Adapter<VideoCategoryAdapter.CategoryViewHolder>() {

    private val categories = mutableListOf<String>()
    private var selectedCategory: String = "All"

    fun setCategories(newCategories: List<String>, currentSelected: String = selectedCategory) {
        categories.clear()
        categories.addAll(newCategories)
        selectedCategory = currentSelected
        notifyDataSetChanged()
    }

    fun setSelected(category: String) {
        selectedCategory = category
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_category_chip, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = categories[position]
        holder.bind(category, category.equals(selectedCategory, ignoreCase = true))
    }

    override fun getItemCount(): Int = categories.size

    inner class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvCategoryName: TextView = itemView.findViewById(R.id.tv_category_name)

        fun bind(category: String, isSelected: Boolean) {
            tvCategoryName.text = category
            val context = itemView.context

            if (isSelected) {
                tvCategoryName.setBackgroundResource(R.drawable.bg_badge)
                tvCategoryName.setTextColor(ContextCompat.getColor(context, R.color.secondary))
            } else {
                tvCategoryName.setBackgroundResource(R.drawable.bg_card)
                tvCategoryName.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            }

            itemView.setOnClickListener {
                if (selectedCategory != category) {
                    selectedCategory = category
                    notifyDataSetChanged()
                    onCategorySelected(category)
                }
            }
        }
    }
}

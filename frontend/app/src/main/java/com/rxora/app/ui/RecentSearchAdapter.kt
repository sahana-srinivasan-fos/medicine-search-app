package com.rxora.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.rxora.app.databinding.ItemRecentSearchBinding
import com.rxora.app.models.RecentSearch

class RecentSearchAdapter(
    private val onSearchClick: (String) -> Unit
) : ListAdapter<RecentSearch, RecentSearchAdapter.RecentSearchViewHolder>(RecentSearchDiffCallback()) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).query.hashCode().toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecentSearchViewHolder {
        val binding = ItemRecentSearchBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return RecentSearchViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecentSearchViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class RecentSearchViewHolder(private val binding: ItemRecentSearchBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(search: RecentSearch) {
            binding.recentSearchText.text = search.query.uppercase()
            binding.presetBadge.visibility = if (search.isPreset) View.VISIBLE else View.GONE
            binding.root.setOnClickListener {
                onSearchClick(search.query)
            }
        }
    }

    class RecentSearchDiffCallback : DiffUtil.ItemCallback<RecentSearch>() {
        override fun areItemsTheSame(oldItem: RecentSearch, newItem: RecentSearch): Boolean {
            return oldItem.query == newItem.query && oldItem.isPreset == newItem.isPreset
        }

        override fun areContentsTheSame(oldItem: RecentSearch, newItem: RecentSearch): Boolean {
            return oldItem == newItem
        }
    }
}

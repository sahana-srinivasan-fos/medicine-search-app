package com.rxora.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.rxora.app.databinding.ItemRecentSearchBinding
import com.rxora.app.models.RecentSearch

class RecentSearchAdapter(
    private val recentSearches: MutableList<RecentSearch> = mutableListOf(),
    private val onSearchClick: (String) -> Unit
) : RecyclerView.Adapter<RecentSearchAdapter.RecentSearchViewHolder>() {

    fun setData(newSearches: List<RecentSearch>) {
        recentSearches.clear()
        recentSearches.addAll(newSearches)
        notifyDataSetChanged()
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
        holder.bind(recentSearches[position])
    }

    override fun getItemCount(): Int = recentSearches.size

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
}

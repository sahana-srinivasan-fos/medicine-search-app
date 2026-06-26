package com.example.myapplication.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R

class FormatTagAdapter(
    private val onRemoveTag: (position: Int) -> Unit
) : RecyclerView.Adapter<FormatTagAdapter.ViewHolder>() {

    private val tags = mutableListOf<String>()

    fun submitList(newList: List<String>) {
        tags.clear()
        tags.addAll(newList)
        notifyDataSetChanged()
    }

    fun getItems(): List<String> = tags

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_format_tag, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(tags[position], position)
    }

    override fun getItemCount() = tags.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvTagName: TextView = view.findViewById(R.id.tvTagName)

        fun bind(code: String, position: Int) {
            tvTagName.text = getTagDisplayName(code)
            itemView.setOnClickListener {
                onRemoveTag(adapterPosition)
            }
        }
    }

    companion object {
        fun getTagDisplayName(code: String): String {
            return when (code) {
                "NAME" -> "Medicine Name"
                "TAB" -> "Tab Key"
                "QTY" -> "Quantity"
                "ENTER" -> "Enter Key"
                "SPACE" -> "Space Key"
                "COMMA" -> "Comma (,)"
                else -> code
            }
        }
    }
}

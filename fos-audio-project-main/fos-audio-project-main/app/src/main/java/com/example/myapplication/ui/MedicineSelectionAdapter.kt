package com.example.myapplication.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.AutoCompleteTextView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class MedicineSelectionAdapter(
    private val onSelected: (keyword: String, medicineName: String) -> Unit
) : RecyclerView.Adapter<MedicineSelectionAdapter.ViewHolder>() {

    private var items: List<MainViewModel.KeywordSelection> = emptyList()
    private var allMedicines: List<String> = emptyList()

    fun submitList(newList: List<MainViewModel.KeywordSelection>) {
        items = newList
        notifyDataSetChanged()
    }

    fun updateMedicineList(newList: List<String>) {
        allMedicines = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_medicine_selection, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvKeyword: TextView = view.findViewById(R.id.tvKeyword)
        private val chipGroup: ChipGroup = view.findViewById(R.id.chipGroup)
        private val actvCustomSearch: AutoCompleteTextView = view.findViewById(R.id.actvCustomSearch)

        fun bind(item: MainViewModel.KeywordSelection) {
            tvKeyword.text = "Heard: \"${item.keyword}\""
            chipGroup.removeAllViews()

            for (cand in item.candidates) {
                val chip = Chip(itemView.context).apply {
                    text = cand
                    isCheckable = true
                    isChecked = cand == item.selectedMedicine
                    
                    // Style
                    setChipBackgroundColorResource(android.R.color.transparent)
                    chipStrokeWidth = 2f
                    chipStrokeColor = ColorStateList.valueOf(
                        if (isChecked) Color.parseColor("#22C55E") else Color.parseColor("#475569")
                    )
                    setTextColor(if (isChecked) Color.WHITE else Color.parseColor("#CBD5E1"))
                    if (isChecked) chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#14532D"))

                    setOnClickListener {
                        actvCustomSearch.setText("", false)
                        onSelected(item.keyword, cand)
                    }
                }
                chipGroup.addView(chip)
            }

            // Set up AutoCompleteTextView
            val adapter = CustomAutoCompleteAdapter(itemView.context, allMedicines)
            actvCustomSearch.setAdapter(adapter)

            // Show selected medicine in custom search if it's not in the chips
            if (item.selectedMedicine != null && !item.candidates.contains(item.selectedMedicine)) {
                actvCustomSearch.setText(item.selectedMedicine, false)
            } else {
                actvCustomSearch.setText("", false)
            }

            actvCustomSearch.setOnItemClickListener { parent, _, position, _ ->
                val selected = parent.getItemAtPosition(position) as String
                // Uncheck all chips since custom medicine is selected
                for (i in 0 until chipGroup.childCount) {
                    val child = chipGroup.getChildAt(i) as? Chip
                    if (child != null && child.isChecked) {
                        child.isChecked = false
                        child.chipStrokeColor = ColorStateList.valueOf(Color.parseColor("#475569"))
                        child.setTextColor(Color.parseColor("#CBD5E1"))
                    }
                }
                onSelected(item.keyword, selected)
            }

            actvCustomSearch.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    val text = actvCustomSearch.text.toString().trim()
                    if (text.isNotEmpty() && text != item.selectedMedicine) {
                        onSelected(item.keyword, text)
                    }
                }
            }

            actvCustomSearch.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                    val text = actvCustomSearch.text.toString().trim()
                    if (text.isNotEmpty() && text != item.selectedMedicine) {
                        onSelected(item.keyword, text)
                    }
                    actvCustomSearch.clearFocus()
                    true
                } else {
                    false
                }
            }
        }
    }
}

class CustomAutoCompleteAdapter(
    context: android.content.Context,
    private val originalList: List<String>
) : android.widget.ArrayAdapter<String>(context, R.layout.item_dropdown_medicine, R.id.text1), android.widget.Filterable {

    private var filteredList: List<String> = emptyList()

    override fun getCount(): Int = filteredList.size

    override fun getItem(position: Int): String? {
        return if (position in filteredList.indices) filteredList[position] else null
    }

    override fun getFilter(): android.widget.Filter {
        return object : android.widget.Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val results = FilterResults()
                if (constraint.isNullOrEmpty()) {
                    results.values = emptyList<String>()
                    results.count = 0
                } else {
                    val prefix = constraint.toString().lowercase().trim()
                    val matches = originalList.filter {
                        it.lowercase().startsWith(prefix) || 
                        it.lowercase().contains(" $prefix")
                    }.take(100)
                    results.values = matches
                    results.count = matches.size
                }
                return results
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                filteredList = (results?.values as? List<String>) ?: emptyList()
                notifyDataSetChanged()
            }
        }
    }
}

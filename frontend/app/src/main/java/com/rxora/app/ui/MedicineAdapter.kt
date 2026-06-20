package com.rxora.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.rxora.app.databinding.ItemMedicineBinding
import com.rxora.app.models.Medicine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MedicineAdapter(
    private var medicines: List<Medicine> = emptyList()
) : RecyclerView.Adapter<MedicineAdapter.MedicineViewHolder>() {

    suspend fun setData(newMedicines: List<Medicine>) {
        val oldMedicines = medicines
        val diffResult = withContext(Dispatchers.Default) {
            val diffCallback = MedicineDiffCallback(oldMedicines, newMedicines)
            DiffUtil.calculateDiff(diffCallback)
        }
        medicines = newMedicines
        diffResult.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MedicineViewHolder {
        val binding = ItemMedicineBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MedicineViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MedicineViewHolder, position: Int) {
        holder.bind(medicines[position])
    }

    override fun getItemCount(): Int = medicines.size

    class MedicineDiffCallback(
        private val oldList: List<Medicine>,
        private val newList: List<Medicine>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].id == newList[newItemPosition].id
        }
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }

    inner class MedicineViewHolder(private val binding: ItemMedicineBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(medicine: Medicine) {
            binding.medicineName.text = medicine.name
            binding.medicineDescription.text = medicine.description ?: "No description available"
        }
    }
}

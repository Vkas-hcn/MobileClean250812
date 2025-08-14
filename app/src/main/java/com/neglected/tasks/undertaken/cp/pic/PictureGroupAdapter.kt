package com.neglected.tasks.undertaken.cp.pic


import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.facebook.drawee.view.SimpleDraweeView
import com.neglected.tasks.undertaken.R
import java.text.DecimalFormat


class PictureGroupAdapter(
    private val onGroupSelectionChanged: (Int) -> Unit,
    private val onPictureSelectionChanged: (Int, Int) -> Unit
) : RecyclerView.Adapter<PictureGroupAdapter.GroupViewHolder>() {

    private var groups: List<PictureGroup> = emptyList()

    fun submitList(newGroups: List<PictureGroup>) {
        groups = newGroups
        notifyDataSetChanged()
    }

    inner class GroupViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dateText: TextView = itemView.findViewById(R.id.tv_date)
        private val selectAllCheckbox: ImageView = itemView.findViewById(R.id.cb_select_all)
        private val picturesGrid: RecyclerView = itemView.findViewById(R.id.rv_pictures)

        fun bind(group: PictureGroup, position: Int) {
            dateText.text = group.date


            selectAllCheckbox.setOnClickListener {
                onGroupSelectionChanged(position)
            }
            selectAllCheckbox.setImageResource(
                if (group.isSelected) R.mipmap.ic_selete else R.mipmap.ic_not_selete
            )
            val pictureAdapter = PictureAdapter(group.pictures) { pictureIndex ->
                onPictureSelectionChanged(position, pictureIndex)
            }

            picturesGrid.apply {
                layoutManager = GridLayoutManager(itemView.context, 3)
                adapter = pictureAdapter
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_picture_group, parent, false)
        return GroupViewHolder(view)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        holder.bind(groups[position], position)
    }

    override fun getItemCount() = groups.size
}

class PictureAdapter(
    private val pictures: List<PictureItem>,
    private val onSelectionChanged: (Int) -> Unit
) : RecyclerView.Adapter<PictureAdapter.PictureViewHolder>() {

    inner class PictureViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: SimpleDraweeView = itemView.findViewById(R.id.iv_picture)
        private val selectCheckbox: ImageView = itemView.findViewById(R.id.cb_select)
        private val tvSize: TextView = itemView.findViewById(R.id.tv_size)

        fun bind(picture: PictureItem, position: Int) {
            imageView.setImageURI(picture.uri)

            val (sizeText, _) = formatStorageSize(picture.size)
            tvSize.text = sizeText


            selectCheckbox.setOnClickListener {
                onSelectionChanged(position)
            }
            selectCheckbox.setImageResource(
                if (picture.isSelected) R.mipmap.ic_selete else R.mipmap.ic_not_selete
            )
            itemView.setOnClickListener {
                onSelectionChanged(position)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PictureViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_picture, parent, false)
        return PictureViewHolder(view)
    }

    override fun onBindViewHolder(holder: PictureViewHolder, position: Int) {
        holder.bind(pictures[position], position)
    }

    override fun getItemCount() = pictures.size

    private fun formatStorageSize(bytes: Long): Pair<String, String> {
        return when {
            bytes >= 1000L * 1000L * 1000L -> {
                val gb = bytes.toDouble() / (1000L * 1000L * 1000L)
                val formatted = if (gb >= 10.0) {
                    DecimalFormat("#").format(gb)
                } else {
                    DecimalFormat("#.#").format(gb)
                }
                Pair("$formatted GB", "GB")
            }
            bytes >= 1000L * 1000L -> {
                val mb = bytes.toDouble() / (1000L * 1000L)
                val formatted = DecimalFormat("#").format(mb)
                Pair("$formatted MB", "MB")
            }
            bytes >= 1000L -> {
                val kb = bytes.toDouble() / 1000L
                val formatted = DecimalFormat("#").format(kb)
                Pair("$formatted KB", "KB")
            }
            else -> {
                Pair("$bytes B", "B")
            }
        }
    }
}
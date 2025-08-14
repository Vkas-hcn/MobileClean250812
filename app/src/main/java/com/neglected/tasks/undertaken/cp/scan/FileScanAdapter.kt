package com.neglected.tasks.undertaken.cp.scan

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.neglected.tasks.undertaken.R
import com.neglected.tasks.undertaken.databinding.ItemFileBinding

class FileScanAdapter(
    private val onFileSelectClick: (Int) -> Unit
) : ListAdapter<ScannedFile, FileScanAdapter.FileViewHolder>(FileDiffCallback()) {

    class FileViewHolder(
        val binding: ItemFileBinding
    ) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val binding = ItemFileBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return FileViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val file = getItem(position)

        with(holder.binding) {
            tvFileName.text = file.name
            tvFilePath.text = file.path
            tvFileSize.text = file.formatSize()

            imgFileSelect.setImageResource(
                if (file.isSelected) R.mipmap.ic_selete else R.mipmap.ic_not_selete
            )

            // 设置点击事件
            root.setOnClickListener {
                onFileSelectClick(position)
            }

            imgFileSelect.setOnClickListener {
                onFileSelectClick(position)
            }
        }
    }

    class FileDiffCallback : DiffUtil.ItemCallback<ScannedFile>() {
        override fun areItemsTheSame(oldItem: ScannedFile, newItem: ScannedFile): Boolean {
            return oldItem.path == newItem.path
        }

        override fun areContentsTheSame(oldItem: ScannedFile, newItem: ScannedFile): Boolean {
            return oldItem == newItem
        }
    }
}
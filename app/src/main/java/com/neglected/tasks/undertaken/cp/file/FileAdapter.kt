package com.neglected.tasks.undertaken.cp.file

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.drawee.view.SimpleDraweeView
import com.facebook.imagepipeline.request.ImageRequestBuilder
import com.neglected.tasks.undertaken.R
import com.neglected.tasks.undertaken.databinding.ItemFileCleanBinding
import java.io.File

class FileAdapter(
    private val onItemClick: (Int) -> Unit
) : ListAdapter<FileItem, FileAdapter.FileViewHolder>(FileDiffCallback()) {

    inner class FileViewHolder(
        private val binding: ItemFileCleanBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(file: FileItem, position: Int) {
            binding.tvFileName.text = file.name
            binding.tvFileSize.text = formatFileSize(file.size)

            binding.ivSelectStatus.setImageResource(
                if (file.isSelected) R.mipmap.ic_selete else R.mipmap.ic_not_selete
            )

            binding.root.setOnClickListener { onItemClick(position) }
            binding.ivSelectStatus.setOnClickListener { onItemClick(position) }

            loadFileIcon(file)
        }

        private fun loadFileIcon(file: FileItem) {
            when (file.type) {
                FileCategory.Image -> loadImageThumbnail(file.path)
                FileCategory.Video -> loadVideoThumbnail(file.path)
                else -> setDefaultIcon(file.type)
            }
        }

        private fun loadImageThumbnail(imagePath: String) {
            val imageRequest = ImageRequestBuilder.newBuilderWithSource(
                android.net.Uri.fromFile(File(imagePath))
            )
                .setResizeOptions(com.facebook.imagepipeline.common.ResizeOptions(200, 200))
                .build()

            (binding.ivFileIcon as? SimpleDraweeView)?.setImageRequest(imageRequest)
                ?: binding.ivFileIcon.setImageResource(R.mipmap.ic_item_file)
        }

        private fun loadVideoThumbnail(videoPath: String) {
            val imageRequest = ImageRequestBuilder.newBuilderWithSource(
                android.net.Uri.fromFile(File(videoPath))
            )
                .setResizeOptions(com.facebook.imagepipeline.common.ResizeOptions(200, 200))
                .build()

            (binding.ivFileIcon as? SimpleDraweeView)?.setImageRequest(imageRequest)
                ?: binding.ivFileIcon.setImageResource(R.mipmap.ic_item_file)
        }

        private fun setDefaultIcon(type: FileCategory) {
            (binding.ivFileIcon as? SimpleDraweeView)?.setImageResource(getFileIcon(type))
                ?: binding.ivFileIcon.setImageResource(getFileIcon(type))
        }

        private fun formatFileSize(size: Long): String {
            return when {
                size >= 1024 * 1024 * 1024 -> String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0))
                size >= 1024 * 1024 -> String.format("%.1f MB", size / (1024.0 * 1024.0))
                else -> String.format("%.1f KB", size / 1024.0)
            }
        }

        private fun getFileIcon(type: FileCategory): Int {
            return when (type) {
                FileCategory.Audio -> R.mipmap.ic_item_file
                FileCategory.Documents -> R.mipmap.ic_item_file
                FileCategory.Download -> R.mipmap.ic_item_file
                FileCategory.Archive -> R.mipmap.ic_item_file
                else -> R.mipmap.ic_item_file
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val binding = ItemFileCleanBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FileViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    class FileDiffCallback : DiffUtil.ItemCallback<FileItem>() {
        override fun areItemsTheSame(oldItem: FileItem, newItem: FileItem): Boolean {
            return oldItem.path == newItem.path
        }

        override fun areContentsTheSame(oldItem: FileItem, newItem: FileItem): Boolean {
            return oldItem == newItem
        }
    }
}
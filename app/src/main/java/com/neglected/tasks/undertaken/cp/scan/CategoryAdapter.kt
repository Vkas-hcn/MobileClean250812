package com.neglected.tasks.undertaken.cp.scan

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.neglected.tasks.undertaken.R
import com.neglected.tasks.undertaken.databinding.ItemCategoryBinding

class CategoryAdapter(
    private val onCategoryExpandClick: (Int) -> Unit,
    private val onCategorySelectClick: (Int) -> Unit,
    private val onFileSelectClick: (Int, Int) -> Unit
) : ListAdapter<CategoryGroup, CategoryAdapter.CategoryViewHolder>(CategoryDiffCallback()) {

    companion object {
        private const val TAG = "CategoryAdapter"
        private const val MAX_DISPLAY_FILES = 300 // 最多显示300个文件
    }

    class CategoryViewHolder(
        val binding: ItemCategoryBinding
    ) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemCategoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return CategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val categoryGroup = getItem(position)

        with(holder.binding) {
            ivIcon.setImageResource(categoryGroup.category.iconRes)
            tvTitle.text = categoryGroup.category.name

            if (categoryGroup.hasFiles) {
                val totalFiles = categoryGroup.files.size
                val displayedFiles = minOf(totalFiles, MAX_DISPLAY_FILES)

                val sizeText = if (totalFiles > MAX_DISPLAY_FILES) {
                    "${categoryGroup.formatTotalSize()} (${displayedFiles}/$totalFiles files)"
                } else {
                    "${categoryGroup.formatTotalSize()} ($totalFiles files)"
                }
                tvSize.text = sizeText

            } else {
                tvSize.text = "0 KB (0 files)"
            }

            imgInstruct.setImageResource(
                if (categoryGroup.isExpanded) R.mipmap.ic_xia else R.mipmap.ic_below
            )

            if (categoryGroup.hasFiles) {
                imgSelect.setImageResource(
                    if (categoryGroup.isSelected) R.mipmap.ic_selete else R.mipmap.ic_not_selete
                )
                imgSelect.visibility = View.VISIBLE
                imgSelect.alpha = 1.0f
                imgSelect.isClickable = true
            } else {
                imgSelect.setImageResource(R.mipmap.ic_not_selete)
                imgSelect.visibility = View.VISIBLE
                imgSelect.alpha = 0.3f
                imgSelect.isClickable = false
            }

            if (categoryGroup.isExpanded && categoryGroup.hasFiles) {
                rvItemFile.visibility = View.VISIBLE

                val filesToDisplay = categoryGroup.files.take(MAX_DISPLAY_FILES)
                setupFileRecyclerView(rvItemFile, categoryGroup.copy(files = filesToDisplay), position)

            } else {
                rvItemFile.visibility = View.GONE
            }

            llCategory.setOnClickListener {
                onCategoryExpandClick(position)
            }

            if (categoryGroup.hasFiles) {
                imgSelect.setOnClickListener {
                    onCategorySelectClick(position)
                }
            } else {
                imgSelect.setOnClickListener(null)
            }

            if (categoryGroup.hasFiles) {
                llCategory.alpha = 1.0f
                tvTitle.alpha = 1.0f
                tvSize.alpha = 1.0f
                ivIcon.alpha = 1.0f
                llCategory.isClickable = true
            } else {
                llCategory.alpha = 0.6f
                tvTitle.alpha = 0.6f
                tvSize.alpha = 0.6f
                ivIcon.alpha = 0.6f
                llCategory.isClickable = true
            }
        }
    }

    private fun setupFileRecyclerView(
        recyclerView: RecyclerView,
        categoryGroup: CategoryGroup,
        categoryPosition: Int
    ) {

        val fileAdapter = if (recyclerView.adapter is FileScanAdapter) {
            recyclerView.adapter as FileScanAdapter
        } else {
            FileScanAdapter { filePosition ->
                onFileSelectClick(categoryPosition, filePosition)
            }.also { adapter ->
                recyclerView.apply {
                    layoutManager = LinearLayoutManager(context)
                    this.adapter = adapter

                    setHasFixedSize(true)
                    setItemViewCacheSize(20) // 增加缓存大小

                    isNestedScrollingEnabled = false
                }
            }
        }


    }

    override fun submitList(list: List<CategoryGroup>?) {

        super.submitList(list?.toList())
    }

    class CategoryDiffCallback : DiffUtil.ItemCallback<CategoryGroup>() {
        override fun areItemsTheSame(oldItem: CategoryGroup, newItem: CategoryGroup): Boolean {
            return oldItem.category == newItem.category
        }

        override fun areContentsTheSame(oldItem: CategoryGroup, newItem: CategoryGroup): Boolean {
            val result = oldItem.category == newItem.category &&
                    oldItem.files.size == newItem.files.size &&
                    oldItem.isSelected == newItem.isSelected &&
                    oldItem.isExpanded == newItem.isExpanded &&
                    oldItem.totalSize == newItem.totalSize &&
                    oldItem.files.take(MAX_DISPLAY_FILES).zip(newItem.files.take(MAX_DISPLAY_FILES))
                        .all { (old, new) -> old.path == new.path && old.isSelected == new.isSelected }


            return result
        }
    }
}
package com.neglected.tasks.undertaken.cp.file

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

@Parcelize
data class FileItem(
    val name: String,
    val path: String,
    val size: Long,
    val type: @RawValue FileCategory,
    val dateAdded: Long,
    val isSelected: Boolean = false
) : Parcelable

sealed class FileCategory(val displayName: String) {
    object Image : FileCategory("Image")
    object Video : FileCategory("Video")
    object Audio : FileCategory("Audio")
    object Documents : FileCategory("Docs")
    object Download : FileCategory("Download")
    object Archive : FileCategory("Zip")

    companion object {
        fun fromString(type: String): FileCategory = when (type.lowercase()) {
            "image" -> Image
            "video" -> Video
            "audio" -> Audio
            "docs", "documents" -> Documents
            "download" -> Download
            "zip", "archive" -> Archive
            else -> Documents
        }

        fun getAllTypes(): List<FileCategory> = listOf(Image, Video, Audio, Documents, Download, Archive)
    }
}

data class FilterState(
    val selectedType: String = "All types",
    val selectedSize: String = "All Size",
    val selectedTime: String = "All Time"
)

data class CleanFileUiState(
    val isScanning: Boolean = false,
    val files: List<FileItem> = emptyList(),
    val filteredFiles: List<FileItem> = emptyList(),
    val filterState: FilterState = FilterState(),
    val selectedCount: Int = 0,
    val isDeleteEnabled: Boolean = false,
    val error: String? = null
)
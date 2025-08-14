package com.neglected.tasks.undertaken.cp.pic

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import java.util.Date

sealed class PictureItem {
    abstract val id: Long
    abstract val name: String
    abstract val path: String
    abstract val uri: Uri
    abstract val size: Long
    abstract val dateAdded: Date
    abstract val isSelected: Boolean

    data class Image(
        override val id: Long,
        override val name: String,
        override val path: String,
        override val uri: Uri,
        override val size: Long,
        override val dateAdded: Date,
        override val isSelected: Boolean = false
    ) : PictureItem()

    data class Video(
        override val id: Long,
        override val name: String,
        override val path: String,
        override val uri: Uri,
        override val size: Long,
        override val dateAdded: Date,
        override val isSelected: Boolean = false,
        val duration: Long
    ) : PictureItem()
}

data class PictureGroup(
    val date: String,
    val pictures: List<PictureItem>,
    val isSelected: Boolean = false,
    val totalSize: Long = pictures.sumOf { it.size }
)

data class CleanPictureUiState(
    val isScanning: Boolean = false,
    val scanProgress: Int = 0,
    val pictureGroups: List<PictureGroup> = emptyList(),
    val totalSelectedSize: Long = 0L,
    val selectedCount: Int = 0,
    val isAllSelected: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

interface PictureRepository {
    suspend fun scanPictures(): Flow<ScanResult>
    suspend fun deletePictures(pictures: List<PictureItem>): Flow<DeleteResult>
}

sealed class ScanResult {
    object Started : ScanResult()
    data class Progress(val progress: Int, val currentCount: Int, val totalCount: Int) : ScanResult()
    data class Success(val groups: List<PictureGroup>) : ScanResult()
    data class Error(val exception: Throwable) : ScanResult()
}

sealed class DeleteResult {
    data class Progress(val deletedCount: Int, val totalCount: Int) : DeleteResult()
    data class Success(val deletedCount: Int, val deletedSize: Long) : DeleteResult()
    data class Error(val exception: Throwable) : DeleteResult()
}
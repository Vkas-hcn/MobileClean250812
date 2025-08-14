package com.neglected.tasks.undertaken.cp.scan


import androidx.annotation.DrawableRes
import com.neglected.tasks.undertaken.R


sealed class FileCategory(
    val name: String,
    @DrawableRes val iconRes: Int,
    val patterns: List<String> = emptyList(),
    val pathPatterns: List<String> = emptyList()
) {
    object AppCache : FileCategory(
        name = "App Cache",
        iconRes = R.mipmap.ic_cache,
        patterns = listOf(".cache", ".dex"),
        pathPatterns = listOf("/cache/", "/app_cache/", "/webview/")
    )

    object ApkFiles : FileCategory(
        name = "Apk Files",
        iconRes = R.mipmap.ic_apk,
        patterns = listOf(".apk", ".xapk", ".apks")
    )

    object LogFiles : FileCategory(
        name = "Log Files",
        iconRes = R.mipmap.ic_log,
        patterns = listOf(".log", ".crash"),
        pathPatterns = listOf("/logs/")
    )

    object TempFiles : FileCategory(
        name = "Temp Files",
        iconRes = R.mipmap.ic_temp,
        patterns = listOf(".tmp", ".temp"),
        pathPatterns = listOf("/temp/", "/.temp", "/temporary/", "/.thumbnails/")
    )

    object Other : FileCategory(
        name = "Other",
        iconRes = R.mipmap.ic_ad,
        patterns = listOf(".bak", ".old", ".swp", ".swo"),
        pathPatterns = listOf("/trash/", "/recycle/")
    )

    companion object {
        fun getAllCategories(): List<FileCategory> = listOf(
            AppCache, ApkFiles, LogFiles, TempFiles, Other
        )

        fun categorizeFile(fileName: String, filePath: String, fileSize: Long): FileCategory? {
            val lowerFileName = fileName.lowercase()
            val lowerFilePath = filePath.lowercase()

            return getAllCategories().find { category ->
                category.patterns.any { pattern -> lowerFileName.contains(pattern) } ||
                        category.pathPatterns.any { pattern -> lowerFilePath.contains(pattern) } ||
                        category.matchesSpecialRules(lowerFileName, lowerFilePath, fileSize)
            }
        }
    }

    private fun matchesSpecialRules(fileName: String, filePath: String, fileSize: Long): Boolean {
        return when (this) {
            is AppCache -> fileName.contains("cache") || (fileName.endsWith(".dex") && filePath.contains("cache"))
            is LogFiles -> (fileName.endsWith(".txt") && (filePath.contains("log") || fileName.contains("log"))) ||
                    fileName.startsWith("log")
            is TempFiles -> fileName.startsWith("tmp") || fileName.startsWith("temp")
            is Other -> fileName.startsWith("~") || fileName.contains("backup") ||
                    (fileName.startsWith(".") && fileName.length > 10) ||
                    (fileSize > 10 * 1024 * 1024 && filePath.contains("/download"))
            else -> false
        }
    }
}

data class ScannedFile(
    val name: String,
    val path: String,
    val size: Long,
    val category: FileCategory,
    val isSelected: Boolean = false
) {
    fun toggleSelection(): ScannedFile = copy(isSelected = !isSelected)

    fun formatSize(): String {
        return when {
            size >= 1024 * 1024 -> String.format("%.2fMB", size / (1024.0 * 1024.0))
            else -> String.format("%.2fKB", size / 1024.0)
        }
    }
}


data class CategoryGroup(
    val category: FileCategory,
    val files: List<ScannedFile>,
    val isSelected: Boolean = false,
    val isExpanded: Boolean = false
) {
    // 计算属性
    val hasFiles: Boolean get() = files.isNotEmpty()

    val totalSize: Long get() = files.sumOf { it.size }

    val selectedFiles: List<ScannedFile> get() = files.filter { it.isSelected }

    val selectedSize: Long get() = selectedFiles.sumOf { it.size }


    fun formatTotalSize(): String {
        return formatFileSize(totalSize)
    }


    fun toggleExpansion(): CategoryGroup {
        return this.copy(isExpanded = !isExpanded)
    }


    fun toggleAllSelection(): CategoryGroup {
        if (!hasFiles) return this

        val newSelectionState = !isSelected
        val updatedFiles = files.map { it.copy(isSelected = newSelectionState) }

        return this.copy(
            isSelected = newSelectionState,
            files = updatedFiles
        )
    }


    fun updateFileSelection(fileIndex: Int): CategoryGroup {
        if (fileIndex !in files.indices) return this

        val updatedFiles = files.toMutableList()
        val currentFile = updatedFiles[fileIndex]
        updatedFiles[fileIndex] = currentFile.copy(isSelected = !currentFile.isSelected)

        // 检查是否所有文件都被选中
        val allSelected = updatedFiles.all { it.isSelected }
        val anySelected = updatedFiles.any { it.isSelected }

        return this.copy(
            files = updatedFiles,
            isSelected = allSelected && anySelected
        )
    }

    private fun formatFileSize(size: Long): String {
        return when {
            size >= 1024 * 1024 * 1024 -> {
                String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0))
            }
            size >= 1024 * 1024 -> {
                String.format("%.1f MB", size / (1024.0 * 1024.0))
            }
            size >= 1024 -> {
                String.format("%.1f KB", size / 1024.0)
            }
            else -> {
                "$size B"
            }
        }
    }
}

sealed class ScanState {
    object Idle : ScanState()
    data class Scanning(val currentPath: String, val currentSize: Long) : ScanState()
    data class Progress(val scannedFiles: List<ScannedFile>, val totalSize: Long) : ScanState()
    data class Completed(val totalSize: Long, val totalFiles: Int) : ScanState()
    data class CompletedWithFiles(val totalSize: Long, val totalFiles: Int, val files: List<ScannedFile>) : ScanState()
    data class Error(val message: String) : ScanState()
}


sealed class CleanState {
    object Idle : CleanState()
    data class Cleaning(val progress: Int, val total: Int) : CleanState()
    data class Completed(val deletedCount: Int, val deletedSize: Long) : CleanState()
    data class Error(val message: String) : CleanState()
}
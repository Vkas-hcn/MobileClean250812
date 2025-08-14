package com.neglected.tasks.undertaken.cp.file

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

class FileRepositoryOptimized(private val context: Context) {

    private val fileSizeThresholds = mapOf(
        FileCategory.Image to 1 * 1024L,
        FileCategory.Video to 10 * 1024L,
        FileCategory.Audio to 1 * 1024L,
        FileCategory.Documents to 1 * 1024L,
        FileCategory.Download to 1 * 1024L,
        FileCategory.Archive to 1 * 1024L
    )

    fun scanAllFiles(): Flow<List<FileItem>> = flow {
        val allFiles = buildList {
            addAll(scanMediaFiles())
            addAll(scanDocumentFiles())
            addAll(scanDownloadFiles())
            addAll(scanArchiveFiles())
        }
        emit(allFiles)
    }.flowOn(Dispatchers.IO)

    private suspend fun scanMediaFiles(): List<FileItem> = buildList {
        addAll(scanMediaStore(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, FileCategory.Image))
        addAll(scanMediaStore(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, FileCategory.Video))
        addAll(scanMediaStore(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, FileCategory.Audio))
    }

    private suspend fun scanMediaStore(uri: Uri, category: FileCategory): List<FileItem> = buildList {
        val projection = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED
        )

        runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameColumn) ?: continue
                    val path = cursor.getString(pathColumn) ?: continue
                    val size = cursor.getLong(sizeColumn)
                    val dateAdded = cursor.getLong(dateColumn) * 1000L

                    val minSize = fileSizeThresholds[category] ?: 0L
                    if (size > minSize && File(path).exists()) {
                        add(FileItem(name, path, size, category, dateAdded))
                    }
                }
            }
        }.onFailure { it.printStackTrace() }
    }

    private suspend fun scanDocumentFiles(): List<FileItem> {
        val documentExtensions = listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt")
        return scanFilesByExtensions(documentExtensions, FileCategory.Documents)
    }

    private suspend fun scanDownloadFiles(): List<FileItem> {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return scanDirectory(downloadDir, FileCategory.Download)
    }

    private suspend fun scanArchiveFiles(): List<FileItem> {
        val archiveExtensions = listOf("zip", "rar", "7z", "tar", "gz")
        return scanFilesByExtensions(archiveExtensions, FileCategory.Archive)
    }

    private suspend fun scanFilesByExtensions(extensions: List<String>, category: FileCategory): List<FileItem> {
        val files = mutableListOf<FileItem>()
        val externalStorage = Environment.getExternalStorageDirectory()
        scanDirectoryForExtensions(externalStorage, extensions, category, files)
        return files
    }

    private fun scanDirectoryForExtensions(
        dir: File,
        extensions: List<String>,
        category: FileCategory,
        files: MutableList<FileItem>
    ) {
        runCatching {
            dir.listFiles()?.forEach { file ->
                when {
                    file.isDirectory && !file.name.startsWith(".") &&
                            !file.name.equals("Android", ignoreCase = true) -> {
                        scanDirectoryForExtensions(file, extensions, category, files)
                    }
                    file.isFile -> {
                        val extension = file.extension.lowercase()
                        val minSize = fileSizeThresholds[category] ?: 0L
                        if (extensions.contains(extension) && file.length() > minSize) {
                            files.add(FileItem(
                                file.name,
                                file.absolutePath,
                                file.length(),
                                category,
                                file.lastModified()
                            ))
                        }
                    }
                }
            }
        }
    }

    private suspend fun scanDirectory(dir: File, category: FileCategory): List<FileItem> = buildList {
        runCatching {
            dir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    val minSize = fileSizeThresholds[category] ?: 0L
                    if (file.length() > minSize) {
                        add(FileItem(
                            file.name,
                            file.absolutePath,
                            file.length(),
                            category,
                            file.lastModified()
                        ))
                    }
                }
            }
        }
    }

    fun deleteFiles(files: List<FileItem>): Flow<Pair<Long, Int>> = flow {
        var totalDeletedSize = 0L
        var deletedCount = 0

        files.forEach { fileItem ->
            runCatching {
                val file = File(fileItem.path)
                if (file.exists() && file.safeDelete()) {
                    totalDeletedSize += fileItem.size
                    deletedCount++
                }
            }
        }

        emit(Pair(totalDeletedSize, deletedCount))
    }.flowOn(Dispatchers.IO)
}
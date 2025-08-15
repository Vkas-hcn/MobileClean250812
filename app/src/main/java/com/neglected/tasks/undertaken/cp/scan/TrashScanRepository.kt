package com.neglected.tasks.undertaken.cp.scan

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.util.regex.Pattern

class TrashScanRepository(private val context: Context) {

    companion object {
        private const val TAG = "TrashScanRepository"

        private val filterStrArr = arrayOf(
            ".(/|\\\\)crashlytics(/|\\\\|\$).",
            ".(/|\\\\)firebase(/|\\\\|\$).",
            ".(/|\\\\)bugly(/|\\\\|\$).",
            ".(/|\\\\)umeng(/|\\\\|\$).",
            ".(/|\\\\)backup(/|\\\\|\$).",
            ".(/|\\\\)downloads?(/|\\\\|\$).\\.part\$",
            ".(/|\\\\)downloads?(/|\\\\|\$).\\.crdownload\$",
            ".(/|\\\\)downloads?(/|\\\\|\$).\\.tmp\$",
            ".(/|\\\\)webview(/|\\\\|\$).",
            ".(/|\\\\)webviewcache(/|\\\\|\$).",
            ".(/|\\\\)analytics(/|\\\\|\$).",
            ".(/|\\\\)advertising(/|\\\\|\$).",
            ".(/|\\\\)logfiles?(/|\\\\|\$).",
            ".(/|\\\\)errorlogs?(/|\\\\|\$).",
            ".(/|\\\\)telemetry(/|\\\\|\$).",
            ".(/|\\\\)thumbnails?(/|\\\\|\$).",
            ".(/|\\\\)imageloader(/|\\\\|\$).",
            ".(/|\\\\)okhttp(/|\\\\|\$).",
            ".(/|\\\\)picasso(/|\\\\|\$).",
            ".(/|\\\\)fresco(/|\\\\|\$)."
        )

        private val filterPatterns = filterStrArr.map { Pattern.compile(it, Pattern.CASE_INSENSITIVE) }

        private val trashExtensions = setOf(
            "tmp", "temp", "cache", "bak", "backup", "old", "log", "part",
            "crdownload", "download", "incomplete", "partial", "thumbs",
            "~", "swp", "swo", "orig", "rej", "crash", "dmp", "trace"
        )

        private val trashFileNames = setOf(
            "thumbs.db", "desktop.ini", ".ds_store", "icon\r", "ehthumbs.db",
            "ehthumbs_vista.db", ".spotlight-v100", ".trashes", ".fseventsd",
            ".temporaryitems", ".apdisk", "network trash folder", "temporary items",
            "recycled", "recycle.bin", ".recycle", "\$recycle.bin"
        )

        private val trashFolderNames = setOf(
            "temp", "tmp", "cache", "caches", "temporary", "trash", "recycle",
            "backup", "old", "logs", "log", "analytics", "telemetry", "crashlogs",
            "errorlogs", "thumbnails", "thumb", "preview", "previews", ".trash"
        )
    }

    fun scanTrashFiles(): Flow<ScanState> = flow {
        emit(ScanState.Idle)

        try {
            emit(ScanState.Scanning("Check permissions...", 0L))

            // 检查权限状态
            if (!checkStoragePermission()) {
                emit(ScanState.Error("Storage permissions are required to scan files"))
                return@flow
            }

            emit(ScanState.Scanning("scanning...", 0L))

            val scannedFiles = mutableListOf<ScannedFile>()
            var totalScannedSize = 0L

            val scanDirectories = getScanDirectories()

            if (scanDirectories.isEmpty()) {
                emit(ScanState.Completed(0L, 0))
                return@flow
            }

            for (directory in scanDirectories) {
                if (directory.exists() && directory.isDirectory) {
                    emit(ScanState.Scanning("scan: ${directory.name}", totalScannedSize))

                    try {
                        if (!directory.canRead()) {
                            continue
                        }

                        val filesInDirectory = scanDirectory(directory)

                        scannedFiles.addAll(filesInDirectory)
                        totalScannedSize += filesInDirectory.sumOf { it.size }

                        delay(100)

                        emit(ScanState.Scanning("scan: ${directory.name}", totalScannedSize))
                        emit(ScanState.Progress(scannedFiles.toList(), totalScannedSize))

                    } catch (e: Exception) {
                        Log.e(TAG, "Scan the catalog ${directory.absolutePath} wrong", e)
                    }
                }
            }

            emit(ScanState.CompletedWithFiles(totalScannedSize, scannedFiles.size, scannedFiles.toList()))

        } catch (e: Exception) {
            emit(ScanState.Error("The scan failed: ${e.localizedMessage}"))
        }
    }.flowOn(Dispatchers.IO)

    fun deleteSelectedFiles(files: List<ScannedFile>): Flow<CleanState> = flow {
        emit(CleanState.Idle)

        try {
            emit(CleanState.Cleaning(0, files.size))

            var deletedCount = 0
            var deletedSize = 0L

            files.forEachIndexed { index, scannedFile ->
                try {
                    val file = File(scannedFile.path)

                    if (file.exists() && file.delete()) {
                        deletedCount++
                        deletedSize += scannedFile.size
                    }

                    emit(CleanState.Cleaning(index + 1, files.size))
                    delay(50)

                } catch (e: Exception) {
                    Log.e(TAG, "Delete files ${scannedFile.path} wrong", e)
                }
            }

            emit(CleanState.Completed(deletedCount, deletedSize))

        } catch (e: Exception) {
            emit(CleanState.Error("Cleanup failed: ${e.localizedMessage}"))
        }
    }.flowOn(Dispatchers.IO)

    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val hasManagePermission = Environment.isExternalStorageManager()
            Log.d(TAG, "Android 11+, MANAGE_EXTERNAL_STORAGE权限状态: $hasManagePermission")
            hasManagePermission
        } else {
            val hasReadPermission = context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "Android 10及以下, READ_EXTERNAL_STORAGE权限状态: $hasReadPermission")
            hasReadPermission
        }
    }

    private fun getScanDirectories(): List<File> {
        val directories = mutableListOf<File>()

        try {
            context.cacheDir?.let {
                directories.add(it)
            }

            context.externalCacheDir?.let {
                directories.add(it)
            }

            context.filesDir?.let {
                val cacheSubDir = File(it, "cache")
                if (cacheSubDir.exists()) {
                    directories.add(cacheSubDir)
                }
            }

            context.getExternalFilesDir(null)?.let { externalFiles ->
                directories.add(externalFiles)

                val externalCache = File(externalFiles, "cache")
                if (externalCache.exists()) {
                    directories.add(externalCache)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                try {
                    val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (downloadDir?.exists() == true && downloadDir.canRead()) {
                        directories.add(downloadDir)
                    }

                    val dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
                    if (dcimDir?.exists() == true) {
                        val thumbnailsDir = File(dcimDir, ".thumbnails")
                        if (thumbnailsDir.exists() && thumbnailsDir.canRead()) {
                            directories.add(thumbnailsDir)
                        }
                    }

                    // 外部存储根目录的临时文件
                    val externalStorageDir = Environment.getExternalStorageDirectory()
                    if (externalStorageDir?.exists() == true && externalStorageDir.canRead()) {
                        directories.add(externalStorageDir)
                        Log.d(TAG, "Add an external storage root: ${externalStorageDir.absolutePath}")
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error checking public directory", e)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error getting the scan directory", e)
        }

        val validDirectories = directories.filter {
            val canAccess = it.exists() && it.canRead()
            if (!canAccess) {
                Log.w(TAG, "The directory is inaccessible: ${it.absolutePath}")
            }
            canAccess
        }

        return validDirectories
    }

    private fun scanDirectory(directory: File): List<ScannedFile> {
        val scannedFiles = mutableListOf<ScannedFile>()
        try {
            val files = directory.listFiles()
            if (files == null) {
                return scannedFiles
            }

            files.forEach { file ->
                try {
                    if (file.isFile) {
                        if (isTrashFile(file)) {
                            val category = categorizeTrashFile(file)
                            if (category != null) {
                                val scannedFile = ScannedFile(
                                    name = file.name,
                                    path = file.absolutePath,
                                    size = file.length(),
                                    category = category,
                                    isSelected = false
                                )
                                scannedFiles.add(scannedFile)
                            }
                        } else {
                            val category = FileCategory.Companion.categorizeFile(
                                fileName = file.name,
                                filePath = file.absolutePath,
                                fileSize = file.length()
                            )

                            if (category != null) {
                                val scannedFile = ScannedFile(
                                    name = file.name,
                                    path = file.absolutePath,
                                    size = file.length(),
                                    category = category,
                                    isSelected = false
                                )
                                scannedFiles.add(scannedFile)
                            }
                        }
                    } else if (file.isDirectory) {
                        if (isTrashDirectory(file)) {
                            val trashDirFiles = scanTrashDirectory(file)
                            scannedFiles.addAll(trashDirFiles)
                        } else {
                            try {
                                if (getDirectoryDepth(file, directory) < 2) {
                                    scannedFiles.addAll(scanDirectory(file))
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Recursively scan the catalog ${file.absolutePath} wrong", e)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Process files ${file.absolutePath} wrong", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Scan the catalog ${directory.absolutePath} wrong", e)
        }

        return scannedFiles
    }

    private fun isTrashFile(file: File): Boolean {
        val filePath = file.absolutePath
        val fileName = file.name.lowercase()
        val fileExtension = if (fileName.contains('.')) {
            fileName.substringAfterLast('.')
        } else {
            ""
        }

        for (pattern in filterPatterns) {
            if (pattern.matcher(filePath).find()) {
                return true
            }
        }

        if (fileExtension in trashExtensions) {
            return true
        }

        if (fileName in trashFileNames) {
            return true
        }

        if (isSpecialTrashPattern(fileName, filePath)) {
            return true
        }

        return false
    }

    private fun isTrashDirectory(directory: File): Boolean {
        val dirName = directory.name.lowercase()
        return dirName in trashFolderNames
    }

    private fun scanTrashDirectory(directory: File): List<ScannedFile> {
        val trashFiles = mutableListOf<ScannedFile>()

        try {
            val files = directory.listFiles()
            files?.forEach { file ->
                if (file.isFile) {
                    val category = categorizeTrashFile(file) ?: FileCategory.Companion.categorizeFile(
                        fileName = file.name,
                        filePath = file.absolutePath,
                        fileSize = file.length()
                    )
                    if (category != null) {
                        val scannedFile = ScannedFile(
                            name = file.name,
                            path = file.absolutePath,
                            size = file.length(),
                            category = category,
                            isSelected = false
                        )
                        trashFiles.add(scannedFile)
                    }
                } else if (file.isDirectory && getDirectoryDepth(file, directory) < 2) {
                    trashFiles.addAll(scanTrashDirectory(file))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "扫描垃圾目录 ${directory.absolutePath} 时出错", e)
        }

        return trashFiles
    }

    private fun isSpecialTrashPattern(fileName: String, filePath: String): Boolean {
        // 检查临时文件模式
        if (fileName.startsWith("tmp") || fileName.startsWith("temp") ||
            fileName.endsWith("~") || fileName.contains(".tmp.")) {
            return true
        }

        // 检查备份文件模式
        if (fileName.endsWith(".bak") || fileName.endsWith(".backup") ||
            fileName.endsWith(".old") || fileName.contains(".backup.")) {
            return true
        }

        // 检查日志文件模式
        if (fileName.endsWith(".log") || fileName.contains("log") &&
            (fileName.endsWith(".txt") || fileName.endsWith(".out"))) {
            return true
        }

        // 检查崩溃报告
        if (fileName.contains("crash") || fileName.contains("dump") ||
            fileName.contains("error") && fileName.endsWith(".txt")) {
            return true
        }

        // 检查网络缓存
        if (filePath.contains("http") && (filePath.contains("cache") || filePath.contains("temp"))) {
            return true
        }

        return false
    }

    private fun categorizeTrashFile(file: File): FileCategory? {
        return FileCategory.Companion.categorizeFile(
            fileName = file.name,
            filePath = file.absolutePath,
            fileSize = file.length()
        )
    }

    private fun getDirectoryDepth(current: File, root: File): Int {
        var depth = 0
        var temp = current
        while (temp != root && temp.parent != null) {
            depth++
            temp = temp.parentFile ?: break
            if (depth > 10) break // 防止无限循环
        }
        return depth
    }
}
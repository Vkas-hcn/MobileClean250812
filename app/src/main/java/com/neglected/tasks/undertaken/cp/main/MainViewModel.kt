package com.neglected.tasks.undertaken.cp.main


import android.app.Application
import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import kotlin.math.max

data class StorageInfo(
    val totalBytes: Long,
    val usedBytes: Long,
    val freeBytes: Long,
    val usedPercentage: Int,
    val freeFormatted: Pair<String, String>,
    val usedFormatted: Pair<String, String>
)

enum class ScanType {
    GENERAL_CLEAN,
    PICTURE_CLEAN,
    FILE_CLEAN
}

sealed class MainViewState {
    object Loading : MainViewState()
    data class Success(val storageInfo: StorageInfo) : MainViewState()
    data class Error(val message: String) : MainViewState()
}

sealed class MainViewEvent {
    data class StartScan(val scanType: ScanType) : MainViewEvent()
    object OpenSettings : MainViewEvent()
    object ShowPermissionDialog : MainViewEvent()
    object HidePermissionDialog : MainViewEvent()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _viewState = MutableStateFlow<MainViewState>(MainViewState.Loading)
    val viewState: StateFlow<MainViewState> = _viewState.asStateFlow()

    private val _viewEvent = MutableLiveData<MainViewEvent>()
    val viewEvent: LiveData<MainViewEvent> = _viewEvent

    private val _showPermissionDialog = MutableStateFlow(false)
    val showPermissionDialog: StateFlow<Boolean> = _showPermissionDialog.asStateFlow()

    private val context = getApplication<Application>().applicationContext

    init {
        updateStorageInfo()
    }

    fun updateStorageInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _viewState.value = MainViewState.Loading
                val storageInfo = calculateStorageInfo()
                _viewState.value = MainViewState.Success(storageInfo)
            } catch (e: Exception) {
                e.printStackTrace()
                _viewState.value = MainViewState.Error("Failed to load storage information")
            }
        }
    }

    private suspend fun calculateStorageInfo(): StorageInfo {
        val internalStat = StatFs(Environment.getDataDirectory().path)

        val blockSize = internalStat.blockSizeLong
        val totalBlocks = internalStat.blockCountLong
        val availableBlocks = internalStat.availableBlocksLong

        val totalUserBytes = totalBlocks * blockSize
        val availableBytes = availableBlocks * blockSize
        val usedBytes = totalUserBytes - availableBytes

        val actualTotalBytes = getTotalDeviceStorageAccurate()
        val displayTotalBytes = max(actualTotalBytes, totalUserBytes)
        val displayFreeBytes = availableBytes
        val displayUsedBytes = displayTotalBytes - displayFreeBytes

        val usedPercentage = if (displayTotalBytes > 0) {
            ((displayUsedBytes.toDouble() / displayTotalBytes.toDouble()) * 100).toInt()
        } else {
            0
        }

        return StorageInfo(
            totalBytes = displayTotalBytes,
            usedBytes = displayUsedBytes,
            freeBytes = displayFreeBytes,
            usedPercentage = usedPercentage,
            freeFormatted = formatStorageSize(displayFreeBytes),
            usedFormatted = formatStorageSize(displayUsedBytes)
        )
    }

    private fun getTotalDeviceStorageAccurate(): Long {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val storageStatsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
                return storageStatsManager.getTotalBytes(StorageManager.UUID_DEFAULT)
            }

            val internalStat = StatFs(Environment.getDataDirectory().path)
            val internalTotal = internalStat.blockCountLong * internalStat.blockSizeLong

            val storagePaths = arrayOf(
                Environment.getRootDirectory().absolutePath,
                Environment.getDataDirectory().absolutePath,
                Environment.getDownloadCacheDirectory().absolutePath
            )

            var total: Long = 0
            for (path in storagePaths) {
                val stat = StatFs(path)
                val blockSize = stat.blockSizeLong
                val blockCount = stat.blockCountLong
                total += blockSize * blockCount
            }

            val withSystemOverhead = total + (total * 0.07).toLong()
            max(internalTotal, withSystemOverhead)
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                val internalStat = StatFs(Environment.getDataDirectory().path)
                val internalTotal = internalStat.blockCountLong * internalStat.blockSizeLong
                internalTotal + (internalTotal * 0.12).toLong()
            } catch (innerException: Exception) {
                innerException.printStackTrace()
                0L
            }
        }
    }

     fun formatStorageSize(bytes: Long): Pair<String, String> {
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

    fun onCleanButtonClicked() {
        _viewEvent.value = MainViewEvent.StartScan(ScanType.GENERAL_CLEAN)
    }

    fun onPictureCleanClicked() {
        _viewEvent.value = MainViewEvent.StartScan(ScanType.PICTURE_CLEAN)
    }

    fun onFileCleanClicked() {
        _viewEvent.value = MainViewEvent.StartScan(ScanType.FILE_CLEAN)
    }

    fun onSettingsClicked() {
        _viewEvent.value = MainViewEvent.OpenSettings
    }

    fun showPermissionDialog() {
        _showPermissionDialog.value = true
        _viewEvent.value = MainViewEvent.ShowPermissionDialog
    }

    fun hidePermissionDialog() {
        _showPermissionDialog.value = false
        _viewEvent.value = MainViewEvent.HidePermissionDialog
    }

    fun onPermissionDialogConfirmed() {
        hidePermissionDialog()
        // Permission request will be handled by the view
    }

    fun onPermissionDialogCancelled() {
        hidePermissionDialog()
        // Handle permission denied logic will be in the view
    }
}
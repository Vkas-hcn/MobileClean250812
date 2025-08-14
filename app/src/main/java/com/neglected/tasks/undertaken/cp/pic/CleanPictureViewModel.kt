package com.neglected.tasks.undertaken.cp.pic

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.text.set
class PictureRepositoryImpl(private val context: Context) : PictureRepository {

    override suspend fun scanPictures(): Flow<ScanResult> = flow {
        emit(ScanResult.Started)

        try {
            val pictureMap = mutableMapOf<String, MutableList<PictureItem>>()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_ADDED
            )

            val cursor = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )

            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val pathColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

                var count = 0
                val totalCount = it.count

                while (it.moveToNext()) {
                    val id = it.getLong(idColumn)
                    val name = it.getString(nameColumn)
                    val path = it.getString(pathColumn)
                    val size = it.getLong(sizeColumn)
                    val dateAdded = it.getLong(dateColumn) * 1000

                    val file = File(path)
                    if (!file.exists()) continue

                    val date = Date(dateAdded)
                    val dateKey = dateFormat.format(date)
                    val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())

                    val pictureItem = PictureItem.Image(
                        id = id,
                        name = name,
                        path = path,
                        uri = uri,
                        size = size,
                        dateAdded = date
                    )

                    pictureMap.getOrPut(dateKey) { mutableListOf() }.add(pictureItem)

                    count++
                    val progress = (count * 100) / totalCount
                    emit(ScanResult.Progress(progress, count, totalCount))
                }
            }

            val sortedGroups = pictureMap.entries
                .sortedByDescending { it.key }
                .map { entry ->
                    PictureGroup(
                        date = entry.key,
                        pictures = entry.value
                    )
                }

            emit(ScanResult.Success(sortedGroups))
        } catch (e: Exception) {
            emit(ScanResult.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun deletePictures(pictures: List<PictureItem>): Flow<DeleteResult> = flow {
        try {
            var deletedCount = 0
            var deletedSize = 0L
            val totalCount = pictures.size

            pictures.forEach { picture ->
                try {
                    val file = File(picture.path)
                    var fileDeleted = false

                    if (file.exists() && file.delete()) {
                        fileDeleted = true
                    }

                    try {
                        val deletedRows = context.contentResolver.delete(picture.uri, null, null)
                        if (deletedRows > 0) {
                            fileDeleted = true
                        }
                    } catch (e: Exception) {
                        // MediaStore 删除失败但文件删除成功也算成功
                    }

                    if (fileDeleted) {
                        deletedCount++
                        deletedSize += picture.size
                        emit(DeleteResult.Progress(deletedCount, totalCount))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            emit(DeleteResult.Success(deletedCount, deletedSize))
        } catch (e: Exception) {
            emit(DeleteResult.Error(e))
        }
    }.flowOn(Dispatchers.IO)
}

class CleanPictureViewModel(
    private val repository: PictureRepository
) : ViewModel() {

    private val _uiState = MutableLiveData(CleanPictureUiState())
    val uiState: LiveData<CleanPictureUiState> = _uiState

    private val _navigationEvent = MutableLiveData<NavigationEvent>()
    val navigationEvent: LiveData<NavigationEvent> = _navigationEvent

    sealed class NavigationEvent {
        data class NavigateToComplete(val deletedCount: Int, val deletedSize: Long) : NavigationEvent()
        object NavigateBack : NavigationEvent()
    }

    init {
        startScanning()
    }

    fun startScanning() {
        viewModelScope.launch {
            repository.scanPictures()
                .onStart { updateUiState { copy(isScanning = true, scanProgress = 0) } }
                .collect { result ->
                    when (result) {
                        is ScanResult.Started -> {
                            updateUiState { copy(isScanning = true) }
                        }
                        is ScanResult.Progress -> {
                            updateUiState { copy(scanProgress = result.progress) }
                        }
                        is ScanResult.Success -> {
                            updateUiState {
                                copy(
                                    isScanning = false,
                                    pictureGroups = result.groups,
                                    scanProgress = 100
                                )
                            }
                            updateSelectionInfo()
                        }
                        is ScanResult.Error -> {
                            updateUiState {
                                copy(
                                    isScanning = false,
                                    error = result.exception.message
                                )
                            }
                        }
                    }
                }
        }
    }

    fun toggleSelectAll() {
        val currentState = _uiState.value ?: return
        val newSelectState = !currentState.isAllSelected

        val updatedGroups = currentState.pictureGroups.map { group ->
            group.copy(
                isSelected = newSelectState,
                pictures = group.pictures.map { picture ->
                    when (picture) {
                        is PictureItem.Image -> picture.copy(isSelected = newSelectState)
                        is PictureItem.Video -> picture.copy(isSelected = newSelectState)
                    }
                }
            )
        }

        updateUiState { copy(pictureGroups = updatedGroups) }
        updateSelectionInfo()
    }

    fun toggleGroupSelection(groupIndex: Int) {
        val currentState = _uiState.value ?: return
        val groups = currentState.pictureGroups.toMutableList()
        val group = groups[groupIndex]
        val newSelectState = !group.isSelected

        groups[groupIndex] = group.copy(
            isSelected = newSelectState,
            pictures = group.pictures.map { picture ->
                when (picture) {
                    is PictureItem.Image -> picture.copy(isSelected = newSelectState)
                    is PictureItem.Video -> picture.copy(isSelected = newSelectState)
                }
            }
        )

        updateUiState { copy(pictureGroups = groups) }
        updateSelectionInfo()
    }

    fun togglePictureSelection(groupIndex: Int, pictureIndex: Int) {
        val currentState = _uiState.value ?: return
        val groups = currentState.pictureGroups.toMutableList()
        val group = groups[groupIndex]
        val pictures = group.pictures.toMutableList()
        val picture = pictures[pictureIndex]

        pictures[pictureIndex] = when (picture) {
            is PictureItem.Image -> picture.copy(isSelected = !picture.isSelected)
            is PictureItem.Video -> picture.copy(isSelected = !picture.isSelected)
        }

        groups[groupIndex] = group.copy(
            pictures = pictures,
            isSelected = pictures.all { it.isSelected }
        )

        updateUiState { copy(pictureGroups = groups) }
        updateSelectionInfo()
    }

    fun deleteSelectedPictures() {
        val currentState = _uiState.value ?: return
        val selectedPictures = currentState.pictureGroups
            .flatMap { it.pictures }
            .filter { it.isSelected }

        if (selectedPictures.isEmpty()) {
            updateUiState { copy(error = "Please select pictures to delete") }
            return
        }

        viewModelScope.launch {
            updateUiState { copy(isLoading = true) }

            repository.deletePictures(selectedPictures)
                .collect { result ->
                    when (result) {
                        is DeleteResult.Progress -> {
                            // 可以显示删除进度
                        }
                        is DeleteResult.Success -> {
                            updateUiState { copy(isLoading = false) }
                            _navigationEvent.value = NavigationEvent.NavigateToComplete(
                                result.deletedCount,
                                result.deletedSize
                            )
                        }
                        is DeleteResult.Error -> {
                            updateUiState {
                                copy(
                                    isLoading = false,
                                    error = result.exception.message
                                )
                            }
                        }
                    }
                }
        }
    }

    fun onBackPressed() {
        _navigationEvent.value = NavigationEvent.NavigateBack
    }

    private fun updateSelectionInfo() {
        val currentState = _uiState.value ?: return

        var totalSelectedSize = 0L
        var selectedCount = 0
        var totalPictures = 0

        currentState.pictureGroups.forEach { group ->
            totalPictures += group.pictures.size
            group.pictures.forEach { picture ->
                if (picture.isSelected) {
                    totalSelectedSize += picture.size
                    selectedCount++
                }
            }
        }

        val isAllSelected = totalPictures > 0 && selectedCount == totalPictures

        updateUiState {
            copy(
                totalSelectedSize = totalSelectedSize,
                selectedCount = selectedCount,
                isAllSelected = isAllSelected
            )
        }
    }

    private fun updateUiState(update: CleanPictureUiState.() -> CleanPictureUiState) {
        _uiState.value = _uiState.value?.update() ?: CleanPictureUiState()
    }
}

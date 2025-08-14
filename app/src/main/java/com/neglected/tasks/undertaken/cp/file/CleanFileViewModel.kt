package com.neglected.tasks.undertaken.cp.file


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

class CleanFileViewModel(
    private val repository: FileRepositoryOptimized
) : ViewModel() {

    private val _uiState = MutableStateFlow(CleanFileUiState())
    val uiState: StateFlow<CleanFileUiState> = _uiState.asStateFlow()

    private val _allFiles = MutableStateFlow<List<FileItem>>(emptyList())
    private val _filterState = MutableStateFlow(FilterState())
    private val _deletedSize = MutableStateFlow(0L)

    val deletedSize: StateFlow<Long> = _deletedSize.asStateFlow()

    init {
        setupFiltering()
        startScanning()
    }

    private fun setupFiltering() {
        combine(_allFiles, _filterState) { files, filter ->
            applyFilters(files, filter)
        }.onEach { filteredFiles ->
            val selectedCount = filteredFiles.count { it.isSelected }
            _uiState.value = _uiState.value.copy(
                files = _allFiles.value,
                filteredFiles = filteredFiles,
                filterState = _filterState.value,
                selectedCount = selectedCount,
                isDeleteEnabled = selectedCount > 0
            )
        }.launchIn(viewModelScope)
    }

    fun startScanning() {
        repository.scanAllFiles()
            .onStart {
                _uiState.value = _uiState.value.copy(isScanning = true, error = null)
            }
            .catch { exception ->
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    error = exception.message ?: "The scan failed"
                )
            }
            .onEach { files ->
                _allFiles.value = files
                _uiState.value = _uiState.value.copy(isScanning = false)
            }
            .launchIn(viewModelScope)
    }

    fun toggleFileSelection(position: Int) {
        val currentFiles = _uiState.value.filteredFiles.toMutableList()
        if (position in currentFiles.indices) {
            val file = currentFiles[position]
            currentFiles[position] = file.copy(isSelected = !file.isSelected)

            val updatedAllFiles = _allFiles.value.map { originalFile ->
                if (originalFile.path == file.path) {
                    originalFile.copy(isSelected = !file.isSelected)
                } else {
                    originalFile
                }
            }
            _allFiles.value = updatedAllFiles
        }
    }

    fun updateTypeFilter(type: String) {
        _filterState.value = _filterState.value.copy(selectedType = type)
    }

    fun updateSizeFilter(size: String) {
        _filterState.value = _filterState.value.copy(selectedSize = size)
    }

    fun updateTimeFilter(time: String) {
        _filterState.value = _filterState.value.copy(selectedTime = time)
    }

    fun deleteSelectedFiles() {
        val selectedFiles = _uiState.value.filteredFiles.filter { it.isSelected }
        if (selectedFiles.isEmpty()) return

        viewModelScope.launch {
            repository.deleteFiles(selectedFiles)
                .catch { exception ->
                    _uiState.value = _uiState.value.copy(
                        error = exception.message ?: "Delete failed"
                    )
                }
                .collect { (deletedSize, _) ->
                    _deletedSize.value = deletedSize
                }
        }
    }

    private fun applyFilters(files: List<FileItem>, filter: FilterState): List<FileItem> {
        val currentTime = System.currentTimeMillis()

        return files.filter { file ->
            val typeMatch = when (filter.selectedType) {
                "All types" -> true
                "Image" -> file.type == FileCategory.Image
                "Video" -> file.type == FileCategory.Video
                "Audio" -> file.type == FileCategory.Audio
                "Docs" -> file.type == FileCategory.Documents
                "Download" -> file.type == FileCategory.Download
                "Zip" -> file.type == FileCategory.Archive
                else -> true
            }

            val sizeMatch = when (filter.selectedSize) {
                "All Size" -> true
                ">1MB" -> file.size > 1 * 1024 * 1024L
                ">5MB" -> file.size > 5 * 1024 * 1024L
                ">10MB" -> file.size > 10 * 1024 * 1024L
                ">20MB" -> file.size > 20 * 1024 * 1024L
                ">50MB" -> file.size > 50 * 1024 * 1024L
                ">100MB" -> file.size > 100 * 1024 * 1024L
                ">200MB" -> file.size > 200 * 1024 * 1024L
                ">500MB" -> file.size > 500 * 1024 * 1024L
                else -> true
            }

            val timeMatch = when (filter.selectedTime) {
                "All Time" -> true
                "Within 1 day" -> file.dateAdded > currentTime - 24 * 60 * 60 * 1000L
                "Within 1 week" -> file.dateAdded > currentTime - 7 * 24 * 60 * 60 * 1000L
                "Within 1 month" -> file.dateAdded > currentTime - 30 * 24 * 60 * 60 * 1000L
                "Within 3 month" -> file.dateAdded > currentTime - 90 * 24 * 60 * 60 * 1000L
                "Within 6 month" -> file.dateAdded > currentTime - 180 * 24 * 60 * 60 * 1000L
                else -> true
            }

            typeMatch && sizeMatch && timeMatch
        }
    }

    fun getTypeFilterOptions(): List<String> {
        return listOf("All types", "Image", "Video", "Audio", "Docs", "Download", "Zip")
    }

    fun getSizeFilterOptions(): List<String> {
        return listOf("All Size", ">1MB", ">5MB", ">10MB", ">20MB", ">50MB", ">100MB", ">200MB", ">500MB")
    }

    fun getTimeFilterOptions(): List<String> {
        return listOf("All Time", "Within 1 day", "Within 1 week", "Within 1 month", "Within 3 month", "Within 6 month")
    }
}
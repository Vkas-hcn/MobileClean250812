package com.neglected.tasks.undertaken.cp.scan

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CleanTrashViewModel(
    private val repository: TrashScanRepository
) : ViewModel() {

    companion object {
        private const val TAG = "CleanTrashViewModel"
    }

    // 私有可变状态
    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    private val _cleanState = MutableStateFlow<CleanState>(CleanState.Idle)
    private val _categoryGroups = MutableStateFlow<List<CategoryGroup>>(emptyList())
    private val _isScanning = MutableStateFlow(false)
    private val _allScannedFiles = MutableStateFlow<List<ScannedFile>>(emptyList())

    // 公开只读状态
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()
    val cleanState: StateFlow<CleanState> = _cleanState.asStateFlow()
    val categoryGroups: StateFlow<List<CategoryGroup>> = _categoryGroups.asStateFlow()
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // 衍生状态
    val totalScannedSize: StateFlow<Long> = _categoryGroups
        .map { groups -> groups.sumOf { it.totalSize } }
        .stateIn(viewModelScope, SharingStarted.Companion.Lazily, 0L)

    val selectedFilesCount: StateFlow<Int> = _categoryGroups
        .map { groups -> groups.sumOf { it.selectedFiles.size } }
        .stateIn(viewModelScope, SharingStarted.Companion.Lazily, 0)

    val selectedSize: StateFlow<Long> = _categoryGroups
        .map { groups -> groups.sumOf { it.selectedSize } }
        .stateIn(viewModelScope, SharingStarted.Companion.Lazily, 0L)

    val hasSelectedFiles: StateFlow<Boolean> = selectedFilesCount
        .map { it > 0 }
        .stateIn(viewModelScope, SharingStarted.Companion.Lazily, false)

    val scanningPath: StateFlow<String> = _scanState
        .map { state ->
            when (state) {
                is ScanState.Scanning -> state.currentPath
                is ScanState.Completed -> "Scan completed"
                is ScanState.Error -> "Scan failed"
                else -> ""
            }
        }
        .stateIn(viewModelScope, SharingStarted.Companion.Lazily, "")

    val formattedTotalSize: StateFlow<Pair<String, String>> = totalScannedSize
        .map { size -> formatFileSize(size) }
        .stateIn(viewModelScope, SharingStarted.Companion.Lazily, Pair("0", "KB"))

    init {
        initializeEmptyCategories()
    }


    private fun initializeEmptyCategories() {
        val emptyGroups = FileCategory.Companion.getAllCategories().map { category ->
            CategoryGroup(
                category = category,
                files = emptyList(),
                isSelected = false,
                isExpanded = false
            )
        }
        _categoryGroups.value = emptyGroups
    }



    fun startScanning() {
        if (_isScanning.value) {
            return
        }

        viewModelScope.launch {
            _isScanning.value = true
            _cleanState.value = CleanState.Idle
            _allScannedFiles.value = emptyList()

            initializeEmptyCategories()

            try {
                val scannedFilesCollector = mutableListOf<ScannedFile>()

                repository.scanTrashFiles().collect { state ->
                    _scanState.value = state
                    when (state) {
                        is ScanState.Scanning -> {
                        }
                        is ScanState.Progress -> {
                            scannedFilesCollector.clear()
                            scannedFilesCollector.addAll(state.scannedFiles)
                            updateCategoryGroups(scannedFilesCollector.toList())
                        }
                        is ScanState.CompletedWithFiles -> {
                            _allScannedFiles.value = state.files
                            updateCategoryGroups(state.files)
                            _isScanning.value = false
                        }
                        is ScanState.Completed -> {
                            _allScannedFiles.value = scannedFilesCollector.toList()
                            updateCategoryGroups(scannedFilesCollector.toList())
                            _isScanning.value = false
                        }
                        is ScanState.Error -> {
                            _isScanning.value = false
                        }
                        else -> {
                        }
                    }
                }
            } catch (e: Exception) {
                _scanState.value = ScanState.Error("Scan failed: ${e.localizedMessage}")
                _isScanning.value = false
            }
        }
    }


    fun toggleCategoryExpansion(categoryIndex: Int) {
        val currentGroups = _categoryGroups.value.toMutableList()
        if (categoryIndex in currentGroups.indices) {
            val currentGroup = currentGroups[categoryIndex]

            currentGroups[categoryIndex] = currentGroup.copy(isExpanded = !currentGroup.isExpanded)

            _categoryGroups.value = currentGroups

        } else {
        }
    }


    fun toggleCategorySelection(categoryIndex: Int) {
        val currentGroups = _categoryGroups.value.toMutableList()
        if (categoryIndex in currentGroups.indices) {
            val currentGroup = currentGroups[categoryIndex]

            if (currentGroup.hasFiles) {
                val newSelectionState = !currentGroup.isSelected

                val updatedFiles = currentGroup.files.map { file ->
                    file.copy(isSelected = newSelectionState)
                }

                currentGroups[categoryIndex] = currentGroup.copy(
                    isSelected = newSelectionState,
                    files = updatedFiles
                )

                _categoryGroups.value = currentGroups

            }
        }
    }


    fun toggleFileSelection(categoryIndex: Int, fileIndex: Int) {
        val currentGroups = _categoryGroups.value.toMutableList()
        if (categoryIndex in currentGroups.indices) {
            val currentGroup = currentGroups[categoryIndex]
            if (fileIndex in currentGroup.files.indices) {
                val currentFile = currentGroup.files[fileIndex]
                val newSelectionState = !currentFile.isSelected


                val updatedFiles = currentGroup.files.toMutableList()
                updatedFiles[fileIndex] = currentFile.copy(isSelected = newSelectionState)

                val allSelected = updatedFiles.all { it.isSelected }
                val anySelected = updatedFiles.any { it.isSelected }

                val shouldExpand = !currentGroup.isExpanded || newSelectionState

                currentGroups[categoryIndex] = currentGroup.copy(
                    files = updatedFiles,
                    isSelected = allSelected && anySelected,
                    isExpanded = if (shouldExpand) true else currentGroup.isExpanded
                )

                _categoryGroups.value = currentGroups

            }
        }
    }


    private fun updateCategoryGroups(allFiles: List<ScannedFile>) {

        val currentGroups = _categoryGroups.value
        val currentStates = currentGroups.associateBy { it.category }.mapValues { (_, group) ->
            Pair(group.isExpanded, group.isSelected)
        }

        val groups = FileCategory.getAllCategories().map { category ->
            val categoryFiles = allFiles.filter { it.category == category }

            val (wasExpanded, wasSelected) = currentStates[category] ?: Pair(false, false)

            val shouldBeSelected = wasSelected && categoryFiles.isNotEmpty()

            val updatedFiles = if (shouldBeSelected) {
                categoryFiles.map { it.copy(isSelected = true) }
            } else {
                categoryFiles
            }

            CategoryGroup(
                category = category,
                files = updatedFiles,
                isSelected = shouldBeSelected,
                isExpanded = wasExpanded
            )
        }

        _categoryGroups.value = groups


    }

    fun cleanSelectedFiles() {
        if (!hasSelectedFiles.value) {
            return
        }

        val selectedFiles = _categoryGroups.value
            .flatMap { it.selectedFiles }


        viewModelScope.launch {
            repository.deleteSelectedFiles(selectedFiles).collect { state ->
                _cleanState.value = state
            }
        }
    }




    fun resetCleanState() {
        _cleanState.value = CleanState.Idle
    }


    private fun formatFileSize(size: Long): Pair<String, String> {
        return when {
            size >= 1024 * 1024 * 1024 -> {
                Pair(String.format("%.1f", size / (1024.0 * 1024.0 * 1024.0)), "GB")
            }
            size >= 1024 * 1024 -> {
                Pair(String.format("%.1f", size / (1024.0 * 1024.0)), "MB")
            }
            else -> {
                Pair(String.format("%.1f", size / 1024.0), "KB")
            }
        }
    }

    // ViewModelFactory
    class Factory(private val repository: TrashScanRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CleanTrashViewModel::class.java)) {
                return CleanTrashViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
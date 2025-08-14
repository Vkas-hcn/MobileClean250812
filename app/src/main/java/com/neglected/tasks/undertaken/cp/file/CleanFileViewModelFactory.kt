package com.neglected.tasks.undertaken.cp.file

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class CleanFileViewModelFactory(
    private val repository: FileRepositoryOptimized
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CleanFileViewModel::class.java)) {
            return CleanFileViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
package com.neglected.tasks.undertaken.cp.pic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class CleanPictureViewModelFactory(
    private val repository: PictureRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CleanPictureViewModel::class.java)) {
            return CleanPictureViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
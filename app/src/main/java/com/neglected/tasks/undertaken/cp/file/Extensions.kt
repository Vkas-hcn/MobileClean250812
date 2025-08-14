package com.neglected.tasks.undertaken.cp.file

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

fun Context.showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message, duration).show()
}

fun <T> LifecycleOwner.collectFlow(flow: Flow<T>, action: suspend (T) -> Unit) {
    lifecycleScope.launch {
        flow.collect(action)
    }
}

fun Long.formatFileSize(): String {
    return when {
        this >= 1024 * 1024 * 1024 -> String.format("%.1f GB", this / (1024.0 * 1024.0 * 1024.0))
        this >= 1024 * 1024 -> String.format("%.1f MB", this / (1024.0 * 1024.0))
        else -> String.format("%.1f KB", this / 1024.0)
    }
}

fun java.io.File.safeDelete(): Boolean {
    return try {
        if (isDirectory) {
            listFiles()?.forEach { it.safeDelete() }
        }
        delete()
    } catch (e: Exception) {
        false
    }
}
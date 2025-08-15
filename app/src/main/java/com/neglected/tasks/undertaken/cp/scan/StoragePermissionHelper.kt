package com.neglected.tasks.undertaken.cp.scan


import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class StoragePermissionHelper(private val activity: AppCompatActivity) {

    companion object {
        private const val TAG = "StoragePermissionHelper"
    }

    private var onPermissionResult: ((Boolean) -> Unit)? = null

    private val manageStorageLauncher: ActivityResultLauncher<Intent> =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val hasPermission = checkStoragePermission()
            onPermissionResult?.invoke(hasPermission)
        }

    private val legacyPermissionLauncher: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val hasPermission = permissions[android.Manifest.permission.READ_EXTERNAL_STORAGE] == true &&
                    permissions[android.Manifest.permission.WRITE_EXTERNAL_STORAGE] == true
            onPermissionResult?.invoke(hasPermission)
        }


    fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val hasPermission = Environment.isExternalStorageManager()
            hasPermission
        } else {
            val readPermission = ContextCompat.checkSelfPermission(
                activity, android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            val writePermission = ContextCompat.checkSelfPermission(
                activity, android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            val hasPermission = readPermission && writePermission
            hasPermission
        }
    }


    fun requestStoragePermission(onResult: (Boolean) -> Unit) {
        onPermissionResult = onResult

        if (checkStoragePermission()) {
            onResult(true)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requestManageExternalStoragePermission()
        } else {
            requestLegacyStoragePermission()
        }
    }


    private fun requestManageExternalStoragePermission() {
        AlertDialog.Builder(activity)
            .setTitle("File access is required")
            .setMessage("In order to scan and clean junk files, apps need permission to access your device's storage. Click OK to turn it on in Settings")
            .setPositiveButton("Go to Settings") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${activity.packageName}")
                    }
                    manageStorageLauncher.launch(intent)
                } catch (e: Exception) {
                    // fallback到通用设置页面
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    manageStorageLauncher.launch(intent)
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                onPermissionResult?.invoke(false)
            }
            .setCancelable(false)
            .show()
    }


    private fun requestLegacyStoragePermission() {
        AlertDialog.Builder(activity)
            .setTitle("Storage permissions are required")
            .setMessage("In order to scan and clean junk files, apps need permission to access your device's storage.")
            .setPositiveButton("Authorization") { _, _ ->
                legacyPermissionLauncher.launch(arrayOf(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ))
            }
            .setNegativeButton("Cancel") { _, _ ->
                onPermissionResult?.invoke(false)
            }
            .setCancelable(false)
            .show()
    }


    fun showPermissionDeniedDialog(onRetry: () -> Unit) {
        val message = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            "No storage access, no scanning of junk files. Please turn it on in the settings."
        } else {
            "No storage permission to scan junk files. Grant storage permissions."
        }

        AlertDialog.Builder(activity)
            .setTitle("Insufficient permissions")
            .setMessage(message)
            .setPositiveButton("Retry") { _, _ ->
                onRetry()
            }
            .setNegativeButton("Cancel") { _, _ ->
                activity.finish()
            }
            .setCancelable(false)
            .show()
    }


    fun getPermissionStatusDescription(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                "You have obtained access to all files"
            } else {
                "Full file access is required"
            }
        } else {
            val readPermission = ContextCompat.checkSelfPermission(
                activity, android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            val writePermission = ContextCompat.checkSelfPermission(
                activity, android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            when {
                readPermission && writePermission -> "Storage permission has been granted"
                readPermission -> "Read permission has been granted, write permission is required"
                writePermission -> "Write permission has been granted, read permission is required"
                else -> "Storage permissions are required"
            }
        }
    }
}
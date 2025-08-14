package com.neglected.tasks.undertaken.cp.main


import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PermissionManager(
    private val activity: FragmentActivity,
    private val onPermissionGranted: () -> Unit,
    private val onPermissionDenied: () -> Unit
) : DefaultLifecycleObserver {

    companion object {
        private const val PREF_NAME = "permission_prefs"
        private const val KEY_PERMISSION_DENIED_COUNT = "permission_denied_count"
    }

    private val _permissionState = MutableStateFlow(false)
    val permissionState: StateFlow<Boolean> = _permissionState.asStateFlow()

    private val manageStoragePermissionLauncher: ActivityResultLauncher<Intent> =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            checkPermissionResult()
        }

    private val storagePermissionLauncher: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.values.all { it }
            if (allGranted) {
                onPermissionGranted()
                _permissionState.value = true
            } else {
                handlePermissionDenied()
            }
        }

    init {
        activity.lifecycle.addObserver(this)
        updatePermissionState()
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        updatePermissionState()
    }

    private fun updatePermissionState() {
        val hasPermission = hasStoragePermission()
        _permissionState.value = hasPermission
        if (hasPermission) {
            onPermissionGranted()
        }
    }

    fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        activity,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requestManageExternalStoragePermission()
        } else {
            requestTraditionalStoragePermission()
        }
    }

    private fun requestManageExternalStoragePermission() {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${activity.packageName}")
            }
            manageStoragePermissionLauncher.launch(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                manageStoragePermissionLauncher.launch(intent)
            } catch (ex: Exception) {
                ex.printStackTrace()
                openAppSettings()
            }
        }
    }

    private fun requestTraditionalStoragePermission() {
        val permissions = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        storagePermissionLauncher.launch(permissions)
    }

    private fun checkPermissionResult() {
        if (hasStoragePermission()) {
            onPermissionGranted()
            _permissionState.value = true
        } else {
            handlePermissionDenied()
        }
    }

    private fun handlePermissionDenied() {
        onPermissionDenied()
        _permissionState.value = false

        val deniedCount = getPermissionDeniedCount()
        incrementPermissionDeniedCount()

        when {
            deniedCount == 0 -> {
                showSimplePermissionDeniedDialog()
            }
            deniedCount >= 1 -> {
                showDetailedPermissionDeniedDialog()
            }
        }
    }

    private fun showSimplePermissionDeniedDialog() {
        AlertDialog.Builder(activity)
            .setTitle("Requires storage permissions")
            .setMessage("To clean up your device, the app needs access to storage.")
            .setPositiveButton("Re-authorization") { _, _ ->
                requestStoragePermission()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDetailedPermissionDeniedDialog() {
        val message = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            "The application requires \"Manage All Files\" permission to clean up your device. Please find this app in the settings and enable the \"Allow management of all files\" permission."
        } else {
            "The app requires storage permission to clean up your device. Please find the app in settings and enable the \"Storage\" permission."
        }

        AlertDialog.Builder(activity)
            .setTitle("Requires storage permissions")
            .setMessage(message)
            .setPositiveButton("Go to Settings") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("Cancel", null)
            .setCancelable(false)
            .show()
    }

    private fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", activity.packageName, null)
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                activity.startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    private fun getPermissionDeniedCount(): Int {
        val prefs = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_PERMISSION_DENIED_COUNT, 0)
    }

    private fun incrementPermissionDeniedCount() {
        val prefs = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val currentCount = prefs.getInt(KEY_PERMISSION_DENIED_COUNT, 0)
        prefs.edit().putInt(KEY_PERMISSION_DENIED_COUNT, currentCount + 1).apply()
    }
}
package com.neglected.tasks.undertaken.cp.scan

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.neglected.tasks.undertaken.R
import com.neglected.tasks.undertaken.databinding.ActivityCleanTrashBinding
import com.neglected.tasks.undertaken.cp.load.CleanLoadActivity
import com.neglected.tasks.undertaken.cp.main.ScanType
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class CleanTrashActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "CleanTrashActivity"
    }

    private lateinit var binding: ActivityCleanTrashBinding
    private lateinit var repository: TrashScanRepository
    private lateinit var permissionHelper: StoragePermissionHelper
    private val viewModel: CleanTrashViewModel by viewModels {
        CleanTrashViewModel.Factory(repository)
    }
    private lateinit var categoryAdapter: CategoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "Activity onCreate")

        binding = ActivityCleanTrashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = TrashScanRepository(this)
        permissionHelper = StoragePermissionHelper(this)

        supportActionBar?.hide()
        setupViews()
        observeViewModel()

        checkPermissionAndStartScan()
    }

    private fun checkPermissionAndStartScan() {

        if (permissionHelper.checkStoragePermission()) {
            startScanning()
        } else {
            requestPermissionAndScan()
        }
    }

    private fun requestPermissionAndScan() {
        permissionHelper.requestStoragePermission { granted ->
            if (granted) {
                startScanning()
            } else {
                permissionHelper.showPermissionDeniedDialog {
                    requestPermissionAndScan()
                }
            }
        }
    }

    private fun startScanning() {
        binding.tvScanningPath.text = "Start scanning..."
        viewModel.startScanning()
    }

    private fun setupViews() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        categoryAdapter = CategoryAdapter(
            onCategoryExpandClick = { position ->
                viewModel.toggleCategoryExpansion(position)
            },
            onCategorySelectClick = { position ->
                viewModel.toggleCategorySelection(position)
            },
            onFileSelectClick = { categoryPosition, filePosition ->
                viewModel.toggleFileSelection(categoryPosition, filePosition)
            }
        )

        binding.rvCategories.apply {
            layoutManager = LinearLayoutManager(this@CleanTrashActivity)
            adapter = categoryAdapter
        }

        binding.btnCleanNow.setOnClickListener {
            viewModel.cleanSelectedFiles()
        }

        binding.btnCleanNow.visibility = View.VISIBLE
        binding.btnCleanNow.isEnabled = false
        updateSizeDisplay(0L)
    }

    private fun observeViewModel() {
        viewModel.scanState
            .onEach { state ->
                handleScanState(state)
            }
            .launchIn(lifecycleScope)

        viewModel.cleanState
            .onEach { state ->
                handleCleanState(state)
            }
            .launchIn(lifecycleScope)

        viewModel.categoryGroups
            .onEach { groups ->
                categoryAdapter.submitList(groups)
            }
            .launchIn(lifecycleScope)

        viewModel.formattedTotalSize
            .onEach { (size, unit) ->
                binding.tvScannedSize.text = size
                binding.tvScannedSizeUn.text = unit
            }
            .launchIn(lifecycleScope)

        viewModel.scanningPath
            .onEach { path ->
                binding.tvScanningPath.text = if (path.isNotEmpty()) path else "Wait for the scan..."
            }
            .launchIn(lifecycleScope)

        viewModel.hasSelectedFiles
            .onEach { hasSelected ->
                binding.btnCleanNow.isEnabled = hasSelected
            }
            .launchIn(lifecycleScope)

        viewModel.totalScannedSize
            .onEach { totalSize ->
                updateBackgroundBasedOnSize(totalSize)
            }
            .launchIn(lifecycleScope)

        viewModel.selectedFilesCount
            .onEach { count ->
            }
            .launchIn(lifecycleScope)

        viewModel.selectedSize
            .onEach { size ->
            }
            .launchIn(lifecycleScope)
    }

    private fun handleScanState(state: ScanState) {
        when (state) {
            is ScanState.Idle -> {
                binding.tvScanningPath.text = "Wait for the scan..."
            }
            is ScanState.Scanning -> {
                binding.tvScanningPath.text = state.currentPath
            }
            is ScanState.Progress -> {
                binding.tvScanningPath.text = "Scanning... found ${state.scannedFiles.size} files"
            }
            is ScanState.CompletedWithFiles -> {
                val message = if (state.totalFiles == 0) {
                    "No spam files found"
                } else {
                    "Scan complete - Discovered${state.totalFiles} files"
                }
                binding.tvScanningPath.text = message
            }
            is ScanState.Completed -> {
                val message = if (state.totalFiles == 0) {
                    "No spam files found"
                } else {
                    "Scan complete - Discovered${state.totalFiles} files"
                }
                binding.tvScanningPath.text = message
            }
            is ScanState.Error -> {
                binding.tvScanningPath.text = "Scan for errors: ${state.message}"
                Toast.makeText(this, "Scan for errors: ${state.message}", Toast.LENGTH_LONG).show()
                Log.e(TAG, "Scan for errors: ${state.message}")

                if (state.message.contains("Permissions")) {
                    permissionHelper.showPermissionDeniedDialog {
                        checkPermissionAndStartScan()
                    }
                }
            }
        }
    }

    private fun handleCleanState(state: CleanState) {
        when (state) {
            is CleanState.Idle -> {
                // 空闲状态
            }
            is CleanState.Cleaning -> {
                binding.btnCleanNow.isEnabled = false
                binding.tvScanningPath.text = "Cleaning up... ${state.progress}/${state.total}"
            }
            is CleanState.Completed -> {
                navigateToScanActivity(state.deletedSize)
            }
            is CleanState.Error -> {
                binding.btnCleanNow.isEnabled = true
                viewModel.resetCleanState()
            }
        }
    }

    private fun navigateToScanActivity(deletedSize: Long) {
        val intent = Intent(this, CleanLoadActivity::class.java).apply {
            putExtra("deleted_size", deletedSize)
            putExtra("SCAN_TYPE", ScanType.GENERAL_CLEAN.name)
        }
        startActivity(intent)
        finish()
    }

    private fun updateSizeDisplay(size: Long) {
        val (displaySize, unit) = formatFileSize(size)
        binding.tvScannedSize.text = displaySize
        binding.tvScannedSizeUn.text = unit
    }

    private fun updateBackgroundBasedOnSize(totalSize: Long) {
        try {
            if (totalSize > 0) {
                binding.scanningDetails.background = ContextCompat.getDrawable(this, R.mipmap.bg_junk)
            } else {
                binding.scanningDetails.background = ContextCompat.getDrawable(this, R.mipmap.bg_no_junk)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Updating the background image failed", e)
        }
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

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Activity onDestroy")
        viewModel.resetCleanState()
    }


}
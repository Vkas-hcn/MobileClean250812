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
    private val viewModel: CleanTrashViewModel by viewModels {
        CleanTrashViewModel.Factory(repository)
    }
    private lateinit var categoryAdapter: CategoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "Activity onCreate")

        binding = ActivityCleanTrashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 初始化Repository
        repository = TrashScanRepository(this)

        supportActionBar?.hide()
        setupViews()
        observeViewModel()

        // 开始扫描
        viewModel.startScanning()
    }

    private fun setupViews() {

        // 设置返回按钮
        binding.btnBack.setOnClickListener {
            finish()
        }

        // 设置RecyclerView
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

        // 设置清理按钮
        binding.btnCleanNow.setOnClickListener {
            viewModel.cleanSelectedFiles()
        }

        // 初始状态
        binding.btnCleanNow.visibility = View.VISIBLE // 始终显示清理按钮
        binding.btnCleanNow.isEnabled = false // 但默认禁用
        updateSizeDisplay(0L)
    }

    private fun observeViewModel() {

        // 观察扫描状态
        viewModel.scanState
            .onEach { state ->
                handleScanState(state)
            }
            .launchIn(lifecycleScope)

        // 观察清理状态
        viewModel.cleanState
            .onEach { state ->
                handleCleanState(state)
            }
            .launchIn(lifecycleScope)

        // 观察分类数据
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
                binding.tvScanningPath.text = if (path.isNotEmpty()) "Scanning: $path" else path
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
                // 空闲状态
            }
            is ScanState.Scanning -> {
                binding.tvScanningPath.text = "Scanning: ${state.currentPath}"
            }
            is ScanState.Progress -> {
                // 实时更新扫描进度
                binding.tvScanningPath.text = "Scanning... Found ${state.scannedFiles.size} files"
            }
            is ScanState.CompletedWithFiles -> {

                if (state.totalFiles == 0) {
                    binding.tvScanningPath.text = "No trash files found"
                } else {
                    binding.tvScanningPath.text = "Scanning completed - Found ${state.totalFiles} files"
                }

            }
            is ScanState.Completed -> {
                if (state.totalFiles == 0) {
                    binding.tvScanningPath.text = "No trash files found"
                } else {
                    binding.tvScanningPath.text = "Scanning completed - Found ${state.totalFiles} files"
                }
            }
            is ScanState.Error -> {
                binding.tvScanningPath.text = "Scan error: ${state.message}"
                Toast.makeText(this, "Scan failed: ${state.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleCleanState(state: CleanState) {
        when (state) {
            is CleanState.Idle -> {
            }
            is CleanState.Cleaning -> {
                binding.btnCleanNow.isEnabled = false
                binding.tvScanningPath.text = "Cleaning... ${state.progress}/${state.total}"
            }
            is CleanState.Completed -> {
                navigateToScanActivity(state.deletedSize)
            }
            is CleanState.Error -> {
                binding.btnCleanNow.isEnabled = true
                binding.tvScanningPath.text = "Clean error: ${state.message}"
                Toast.makeText(this, "Clean failed: ${state.message}", Toast.LENGTH_SHORT).show()
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
            // 忽略资源不存在的错误
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

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "Activity onResume")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "Activity onPause")
    }
}
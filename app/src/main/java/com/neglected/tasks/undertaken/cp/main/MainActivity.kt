package com.neglected.tasks.undertaken.cp.main

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.neglected.tasks.undertaken.R
import com.neglected.tasks.undertaken.cp.load.ScanLoadActivity
import com.neglected.tasks.undertaken.cp.one.ProShareActivity
import com.neglected.tasks.undertaken.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import okhttp3.internal.format

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var permissionManager: PermissionManager

    // 添加变量来记录待执行的扫描类型
    private var pendingScanType: ScanType? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupViews()
        setupPermissionManager()
        observeViewModel()
        setupClickListeners()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        supportActionBar?.hide()
    }

    private fun setupViews() {
        // Initial UI setup
        binding.permissDialog.isVisible = false
    }

    private fun setupPermissionManager() {
        permissionManager = PermissionManager(
            activity = this,
            onPermissionGranted = {
                pendingScanType?.let { scanType ->
                    navigateToScanActivity(scanType)
                    pendingScanType = null
                }
            },
            onPermissionDenied = {
                viewModel.showPermissionDialog()
                pendingScanType = null
            }
        )

        // Observe permission state
        lifecycleScope.launch {
            permissionManager.permissionState.collect { hasPermission ->
                if (hasPermission) {
                    viewModel.hidePermissionDialog()
                    pendingScanType?.let { scanType ->
                        navigateToScanActivity(scanType)
                        pendingScanType = null
                    }
                }
            }
        }
    }

    private fun observeViewModel() {
        // Observe view state
        lifecycleScope.launch {
            viewModel.viewState.collect { state ->
                when (state) {
                    is MainViewState.Loading -> {
                        // Show loading state if needed
                    }

                    is MainViewState.Success -> {
                        updateStorageUI(state.storageInfo)
                    }

                    is MainViewState.Error -> {
                        // Handle error state
                        binding.progressCircle.progress = 0
                    }
                }
            }
        }

        // Observe permission dialog state
        lifecycleScope.launch {
            viewModel.showPermissionDialog.collect { show ->
                binding.permissDialog.isVisible = show
            }
        }

        // Observe view events
        viewModel.viewEvent.observe(this) { event ->
            when (event) {
                is MainViewEvent.StartScan -> {
                    handleStartScan(event.scanType)
                }

                is MainViewEvent.OpenSettings -> {
                    startActivity(Intent(this, ProShareActivity::class.java))
                }

                is MainViewEvent.ShowPermissionDialog -> {
                    binding.permissDialog.isVisible = true
                }

                is MainViewEvent.HidePermissionDialog -> {
                    binding.permissDialog.isVisible = false
                }
            }
        }
    }

    private fun updateStorageUI(storageInfo: StorageInfo) {
        binding.apply {
            tvSizeUser.text = storageInfo.usedFormatted.first
            val size = viewModel.formatStorageSize(storageInfo.totalBytes).first
            tvSizeTotal.text = "of ${size} used"
            progressCircle.progress = storageInfo.usedPercentage
        }
    }

    private fun setupClickListeners() {
        binding.apply {
            cleanButton.setOnClickListener {
                viewModel.onCleanButtonClicked()
            }

            linearLayout3.setOnClickListener {
                viewModel.onPictureCleanClicked()
            }

            linearLayout2.setOnClickListener {
                viewModel.onFileCleanClicked()
            }

            settingsIcon.setOnClickListener {
                viewModel.onSettingsClicked()
            }

            permissDialog.setOnClickListener {
                // Prevent dialog dismissal on background click
            }

            tvCancel.setOnClickListener {
                viewModel.onPermissionDialogCancelled()
                pendingScanType = null
            }

            tvYes.setOnClickListener {
                viewModel.onPermissionDialogConfirmed()
                permissionManager.requestStoragePermission()
            }
        }
    }

    private fun handleStartScan(scanType: ScanType) {
        if (!permissionManager.hasStoragePermission()) {
            pendingScanType = scanType
            viewModel.showPermissionDialog()
            return
        }
        navigateToScanActivity(scanType)
    }

    private fun navigateToScanActivity(scanType: ScanType) {
        val intent = Intent(this, ScanLoadActivity::class.java).apply {
            putExtra("SCAN_TYPE", scanType.name)
        }
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.updateStorageInfo()
    }
}
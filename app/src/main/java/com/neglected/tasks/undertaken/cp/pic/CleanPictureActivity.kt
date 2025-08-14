package com.neglected.tasks.undertaken.cp.pic

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.facebook.drawee.backends.pipeline.Fresco
import com.neglected.tasks.undertaken.R
import com.neglected.tasks.undertaken.cp.complete.CleanCompleteActivity
import com.neglected.tasks.undertaken.cp.load.CleanLoadActivity
import com.neglected.tasks.undertaken.cp.main.ScanType
import com.neglected.tasks.undertaken.databinding.ActivityCleanPictureBinding

class CleanPictureActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCleanPictureBinding
    private lateinit var viewModel: CleanPictureViewModel
    private lateinit var pictureAdapter: PictureGroupAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityCleanPictureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!Fresco.hasBeenInitialized()) {
            Fresco.initialize(this)
        }

        setupViewModel()
        setupViews()
        observeViewModel()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.picture)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        supportActionBar?.hide()
    }

    private fun setupViewModel() {
        val repository = PictureRepositoryImpl(this)
        val factory = CleanPictureViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[CleanPictureViewModel::class.java]
    }

    private fun setupViews() {
        binding.btnBack.setOnClickListener { viewModel.onBackPressed() }
        binding.cbSelectAllGlobal.setOnClickListener { viewModel.toggleSelectAll() }
        binding.btnCleanNow.setOnClickListener { viewModel.deleteSelectedPictures() }

        pictureAdapter = PictureGroupAdapter(
            onGroupSelectionChanged = { groupIndex -> viewModel.toggleGroupSelection(groupIndex) },
            onPictureSelectionChanged = { groupIndex, pictureIndex ->
                viewModel.togglePictureSelection(groupIndex, pictureIndex)
            }
        )

        binding.rvCategories.apply {
            layoutManager = LinearLayoutManager(this@CleanPictureActivity)
            adapter = pictureAdapter
        }
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(this) { state ->
            updateUI(state)
        }

        viewModel.navigationEvent.observe(this) { event ->
            when (event) {
                is CleanPictureViewModel.NavigationEvent.NavigateToComplete -> {
                    navigateToScanActivity(event.deletedSize)
                }
                is CleanPictureViewModel.NavigationEvent.NavigateBack -> {
                    finish()
                }
            }
        }
    }
    private fun navigateToScanActivity(deletedSize: Long) {
        val intent = Intent(this, CleanLoadActivity::class.java).apply {
            putExtra("deleted_size", deletedSize)
            putExtra("SCAN_TYPE", ScanType.PICTURE_CLEAN.name)
        }
        startActivity(intent)
        finish()
    }
    private fun updateUI(state: CleanPictureUiState) {
        binding.progressScaning.visibility = if (state.isScanning) View.VISIBLE else View.GONE
        binding.progressScaning.progress = state.scanProgress

        val (displaySize, unit) = formatFileSize(state.totalSelectedSize)
        binding.tvScannedSize.text = displaySize
        binding.tvScannedSizeUn.text = unit

        binding.cbSelectAllGlobal.setImageResource(
            if (state.isAllSelected) R.mipmap.ic_selete else R.mipmap.ic_not_selete
        )

        binding.btnCleanNow.isEnabled = state.selectedCount > 0
        binding.btnCleanNow.text = if (state.selectedCount > 0) "Delete (${state.selectedCount})" else "Delete"
        binding.btnCleanNow.visibility = if (state.pictureGroups.isNotEmpty()) View.VISIBLE else View.GONE

        pictureAdapter.submitList(state.pictureGroups)

        state.error?.let { error ->
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatFileSize(size: Long): Pair<String, String> {
        return when {
            size >= 1000 * 1000 * 1000 -> {
                Pair(String.format("%.1f", size / (1000.0 * 1000.0 * 1000.0)), "GB")
            }
            size >= 1000 * 1000 -> {
                Pair(String.format("%.1f", size / (1000.0 * 1000.0)), "MB")
            }
            else -> {
                Pair(String.format("%.1f", size / 1000.0), "KB")
            }
        }
    }
}
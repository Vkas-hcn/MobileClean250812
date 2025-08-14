package com.neglected.tasks.undertaken.cp.file
import android.content.Intent
import android.os.Bundle
import android.widget.PopupMenu
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.neglected.tasks.undertaken.R
import com.neglected.tasks.undertaken.cp.load.CleanLoadActivity
import com.neglected.tasks.undertaken.cp.main.ScanType
import com.neglected.tasks.undertaken.databinding.ActivityCleanFileBinding

class CleanFileActivityOptimized : AppCompatActivity() {

    private lateinit var binding: ActivityCleanFileBinding
    private lateinit var fileAdapter: FileAdapter

    private val viewModel: CleanFileViewModel by viewModels {
        CleanFileViewModelFactory(FileRepositoryOptimized(this))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityCleanFileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupWindowInsets()
        setupBackPress()
        setupViews()
        observeViewModel()
    }
    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.file)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        supportActionBar?.hide()
    }
    private fun setupBackPress() {
        onBackPressedDispatcher.addCallback { finish() }
    }

    private fun setupViews() {
        binding.imgBack.setOnClickListener { finish() }

        fileAdapter = FileAdapter(viewModel::toggleFileSelection)

        binding.rvFiles.apply {
            layoutManager = LinearLayoutManager(this@CleanFileActivityOptimized)
            adapter = fileAdapter
        }

        setupFilterButtons()
        setupDeleteButton()
    }

    private fun setupFilterButtons() {
        binding.tvType.setOnClickListener {
            showFilterPopup(
                anchor = binding.tvType,
                options = viewModel.getTypeFilterOptions()
            ) { option ->
                binding.tvType.text = option
                viewModel.updateTypeFilter(option)
            }
        }

        binding.tvSize.setOnClickListener {
            showFilterPopup(
                anchor = binding.tvSize,
                options = viewModel.getSizeFilterOptions()
            ) { option ->
                binding.tvSize.text = option
                viewModel.updateSizeFilter(option)
            }
        }

        binding.tvTime.setOnClickListener {
            showFilterPopup(
                anchor = binding.tvTime,
                options = viewModel.getTimeFilterOptions()
            ) { option ->
                binding.tvTime.text = option
                viewModel.updateTimeFilter(option)
            }
        }
    }

    private fun setupDeleteButton() {
        binding.btnDelete.setOnClickListener {
            if (viewModel.uiState.value.selectedCount == 0) {
                showToast("Please select files to delete")
                return@setOnClickListener
            }
            viewModel.deleteSelectedFiles()
        }
    }

    private inline fun showFilterPopup(
        anchor: android.view.View,
        options: List<String>,
        crossinline onOptionSelected: (String) -> Unit
    ) {
        PopupMenu(this, anchor).apply {
            options.forEach { menu.add(it) }
            setOnMenuItemClickListener { item ->
                onOptionSelected(item.title.toString())
                true
            }
            show()
        }
    }

    private fun observeViewModel() {
        collectFlow(viewModel.uiState) { state ->
            updateUI(state)
        }

        collectFlow(viewModel.deletedSize) { deletedSize ->
            if (deletedSize > 0) {
                navigateToScanActivity(deletedSize)
            }
        }
    }

    private fun updateUI(state: CleanFileUiState) {
        fileAdapter.submitList(state.filteredFiles)

        with(binding) {
            tvType.text = state.filterState.selectedType
            tvSize.text = state.filterState.selectedSize
            tvTime.text = state.filterState.selectedTime

            btnDelete.isEnabled = state.isDeleteEnabled
            btnDelete.text = if (state.selectedCount > 0) {
                "Delete (${state.selectedCount})"
            } else {
                "Delete"
            }
            if (state.filteredFiles.isEmpty()) {
                tvNodata.visibility = android.view.View.VISIBLE
                rvFiles.visibility = android.view.View.GONE
            } else {
                tvNodata.visibility = android.view.View.GONE
                rvFiles.visibility = android.view.View.VISIBLE
            }
        }

        state.error?.let { showToast(it) }
    }

    private fun navigateToScanActivity(deletedSize: Long) {
        startActivity(Intent(this, CleanLoadActivity::class.java).apply {
            putExtra("deleted_size", deletedSize)
            putExtra("SCAN_TYPE", ScanType.FILE_CLEAN.name)
        })
        finish()
    }
}
package com.neglected.tasks.undertaken.cp.complete

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.animation.LinearInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.neglected.tasks.undertaken.R
import com.neglected.tasks.undertaken.cp.file.CleanFileActivityOptimized
import com.neglected.tasks.undertaken.cp.pic.CleanPictureActivity
import com.neglected.tasks.undertaken.cp.scan.CleanTrashActivity
import com.neglected.tasks.undertaken.databinding.ActivityCleanCompleteBinding

class CleanCompleteActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCleanCompleteBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCleanCompleteBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupWindowInsets()
        val deletedSize = intent.getLongExtra("deleted_size", 0)
        startCountdown()
        setupViews( deletedSize)
    }
    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.result)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        supportActionBar?.hide()
    }
    private fun startCountdown() {
        val animator = ValueAnimator.ofInt(0, 100)
        animator.duration = 1500
        animator.interpolator = LinearInterpolator()
        animator.addUpdateListener { animation ->
            val progress = animation.animatedValue as Int
            binding.pg.progress = progress
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                binding.conClean.isVisible = false
            }
        })
        animator.start()
    }

    private fun setupViews( deletedSize: Long) {
        binding.tvSaveData.text = "Saved ${formatFileSize(deletedSize)} space for you"
        binding.tvBack.setOnClickListener {
            finish()
        }
        binding.imgBack.setOnClickListener {
            finish()
        }
        binding.conClean.setOnClickListener {

        }
        binding.atvPicture.setOnClickListener {
            startActivity(Intent(this, CleanPictureActivity::class.java))
            finish()
        }
        binding.atvFile.setOnClickListener {
            startActivity(Intent(this, CleanFileActivityOptimized::class.java))
            finish()
        }
        binding.atvClean.setOnClickListener {
            startActivity(Intent(this, CleanTrashActivity::class.java))
            finish()
        }
    }

    private fun formatFileSize(size: Long): String {
        return when {
            size >= 1024 * 1024 * 1024 -> String.format("%.2fGB", size / (1024.0 * 1024.0 * 1024.0))
            size >= 1024 * 1024 -> String.format("%.2fMB", size / (1024.0 * 1024.0))
            else -> String.format("%.2fKB", size / 1024.0)
        }
    }
}
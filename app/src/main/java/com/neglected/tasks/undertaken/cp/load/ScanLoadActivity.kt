package com.neglected.tasks.undertaken.cp.load

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.animation.LinearInterpolator
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.neglected.tasks.undertaken.R
import com.neglected.tasks.undertaken.cp.file.CleanFileActivityOptimized
import com.neglected.tasks.undertaken.cp.main.ScanType
import com.neglected.tasks.undertaken.cp.pic.CleanPictureActivity
import com.neglected.tasks.undertaken.cp.scan.CleanTrashActivity
import com.neglected.tasks.undertaken.databinding.ActivityScanLoadBinding

class ScanLoadActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanLoadBinding

    var jumpType = ""
    var isResumedState = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityScanLoadBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.load)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        jumpType = intent.getStringExtra("SCAN_TYPE")?:""
        this.supportActionBar?.hide()
        binding.tvBack.setOnClickListener {
            finish()
        }
        when (jumpType) {
            ScanType.PICTURE_CLEAN.name -> {
                binding.imgLogo.setImageResource(R.mipmap.ic_img)
            }
            ScanType.FILE_CLEAN.name -> {
                binding.imgLogo.setImageResource(R.mipmap.ic_fc)
            }
            else -> {
                binding.imgLogo.setImageResource(R.mipmap.logo)
            }
        }
        startCountdown()
        onBackPressedDispatcher.addCallback {
        }
    }

    private fun startCountdown() {
        val animator = ValueAnimator.ofInt(0, 100)
        animator.duration = 2000
        animator.interpolator = LinearInterpolator()
        animator.addUpdateListener { animation ->
            val progress = animation.animatedValue as Int
            binding.pg.progress = progress
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (!isResumedState) {
                    return
                }
                when (jumpType) {
                    ScanType.PICTURE_CLEAN.name -> {
                        startActivity(Intent(this@ScanLoadActivity, CleanPictureActivity::class.java))
                    }
                    ScanType.FILE_CLEAN.name -> {
                        startActivity(Intent(this@ScanLoadActivity, CleanFileActivityOptimized::class.java))
                    }
                    else -> {
                        startActivity(Intent(this@ScanLoadActivity, CleanTrashActivity::class.java))
                    }
                }
                finish()
            }
        })
        animator.start()
    }

    override fun onResume() {
        super.onResume()
        isResumedState = true
    }

    override fun onPause() {
        super.onPause()
        isResumedState = false
    }

}
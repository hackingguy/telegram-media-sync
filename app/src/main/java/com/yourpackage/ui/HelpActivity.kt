package com.yourpackage.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.yourpackage.databinding.ActivityHelpBinding

class HelpActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHelpBinding
    private var debugTapCount = 0
    private val handler = Handler(Looper.getMainLooper())
    private val resetTapCounter = Runnable { debugTapCount = 0 }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHelpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Help"

        binding.openBotFatherButton.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=BotFather")))
            } catch (e: Exception) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/BotFather")))
            }
        }

        // Add version text with debug tap counter
        binding.versionText.setOnClickListener {
            debugTapCount++
            handler.removeCallbacks(resetTapCounter)
            handler.postDelayed(resetTapCounter, 2000) // Reset after 2 seconds

            if (debugTapCount >= 3) {
                debugTapCount = 0
                showDebugLogs()
            }
        }

        // Set version from build.gradle
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            binding.versionText.text = "Version ${pInfo.versionName}"
        } catch (e: Exception) {
            binding.versionText.text = "Version 1.0.0"
        }
    }

    private fun showDebugLogs() {
        val logIntent = Intent(this, DebugLogActivity::class.java)
        startActivity(logIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(resetTapCounter)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
} 
package com.yourpackage.ui

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.yourpackage.databinding.ActivityDebugLogBinding
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope

class DebugLogActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDebugLogBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDebugLogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Debug Logs"

        loadLogs()
    }

    private fun loadLogs() {
        lifecycleScope.launch {
            try {
                val logs = withContext(Dispatchers.IO) {
                    val process = Runtime.getRuntime().exec("logcat -d")
                    val bufferedReader = BufferedReader(
                        InputStreamReader(process.inputStream)
                    )
                    val log = StringBuilder()
                    var line: String?
                    while (bufferedReader.readLine().also { line = it } != null) {
                        log.append(line)
                        log.append('\n')
                    }
                    log.toString()
                }
                binding.logText.text = logs
            } catch (e: Exception) {
                Log.e("DebugLogActivity", "Error reading logs", e)
                binding.logText.text = "Error reading logs: ${e.message}"
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
} 
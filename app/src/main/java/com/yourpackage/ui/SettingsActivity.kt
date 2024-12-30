package com.yourpackage.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.yourpackage.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding

    companion object {
        private const val TAG = "SettingsActivity"
        private const val DEFAULT_BATCH_SIZE = 5
        private const val DEFAULT_RETRY_COUNT = 3
        private const val DEFAULT_BATTERY_THRESHOLD = 20
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Load current settings
        val prefs = getSharedPreferences("sync_settings", MODE_PRIVATE)
        
        // Network settings
        binding.requireWifiSwitch.isChecked = prefs.getBoolean("require_wifi", true)
        
        // Battery settings
        binding.requireChargingSwitch.isChecked = prefs.getBoolean("require_charging", true)
        binding.requireBatteryNotLowSwitch.isChecked = prefs.getBoolean("require_battery_not_low", true)
        
        // Backup settings
        binding.compressImagesSwitch.isChecked = prefs.getBoolean("compress_images", true)
        binding.batchSizeInput.setText(prefs.getInt("batch_size", DEFAULT_BATCH_SIZE).toString())
        binding.retryCountInput.setText(prefs.getInt("retry_count", DEFAULT_RETRY_COUNT).toString())

        // Load auto-restart setting
        binding.autoRestartSwitch.isChecked = prefs.getBoolean("auto_restart_on_boot", true)

        // Load battery threshold
        val batteryThreshold = prefs.getInt("battery_threshold", DEFAULT_BATTERY_THRESHOLD)
        binding.batteryThresholdSlider.value = batteryThreshold.toFloat()
        binding.batteryThresholdText.text = "$batteryThreshold%"

        // Update threshold when slider changes
        binding.batteryThresholdSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val threshold = value.toInt()
                binding.batteryThresholdText.text = "$threshold%"
                prefs.edit().putInt("battery_threshold", threshold).apply()
            }
        }

        // Save settings when changed
        binding.requireWifiSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("require_wifi", isChecked).apply()
        }

        binding.requireChargingSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("require_charging", isChecked).apply()
        }

        binding.requireBatteryNotLowSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("require_battery_not_low", isChecked).apply()
        }

        binding.compressImagesSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("compress_images", isChecked).apply()
        }

        binding.autoRestartSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("auto_restart_on_boot", isChecked).apply()
        }

        // Save numeric inputs when focus is lost
        binding.batchSizeInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val value = binding.batchSizeInput.text.toString().toIntOrNull() ?: DEFAULT_BATCH_SIZE
                prefs.edit().putInt("batch_size", value.coerceIn(1, 50)).apply()
            }
        }

        binding.retryCountInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val value = binding.retryCountInput.text.toString().toIntOrNull() ?: DEFAULT_RETRY_COUNT
                prefs.edit().putInt("retry_count", value.coerceIn(0, 10)).apply()
            }
        }

        setupBatteryOptimizationButton()
    }

    private fun setupBatteryOptimizationButton() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        updateBatteryOptimizationButtonState(powerManager)

        binding.batteryOptimizationButton.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                    try {
                        val intent = Intent().apply {
                            action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                            data = Uri.parse("package:$packageName")
                        }
                        startActivity(intent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to launch battery optimization settings", e)
                        // Fallback to general battery settings
                        startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
                    }
                } else {
                    // Already ignoring battery optimization
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Update button state when returning from settings
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        updateBatteryOptimizationButtonState(powerManager)
    }

    private fun updateBatteryOptimizationButtonState(powerManager: PowerManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(packageName)
            binding.batteryOptimizationButton.text = if (isIgnoringBatteryOptimizations) {
                "Battery Optimization Disabled"
            } else {
                "Disable Battery Optimization"
            }
            binding.batteryOptimizationButton.isEnabled = !isIgnoringBatteryOptimizations
        } else {
            binding.batteryOptimizationButton.isEnabled = false
            binding.batteryOptimizationButton.text = "Not Available (Android < 6.0)"
        }
    }
} 
import android.content.SharedPreferences

data class BackupSettings(
    val requireWifi: Boolean = true,
    val requireCharging: Boolean = true,
    val requireBatteryNotLow: Boolean = true,
    val compressImages: Boolean = true,
    val batchSize: Int = 5,
    val retryCount: Int = 3
) {
    companion object {
        fun fromPreferences(prefs: SharedPreferences): BackupSettings {
            return BackupSettings(
                requireWifi = prefs.getBoolean("require_wifi", true),
                requireCharging = prefs.getBoolean("require_charging", true),
                requireBatteryNotLow = prefs.getBoolean("require_battery_not_low", true),
                compressImages = prefs.getBoolean("compress_images", true),
                batchSize = prefs.getInt("batch_size", 5),
                retryCount = prefs.getInt("retry_count", 3)
            )
        }
    }
} 
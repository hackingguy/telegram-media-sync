package com.yourpackage

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.yourpackage.util.NotificationConstants
import com.yourpackage.util.NotificationChannelManager

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationChannelManager.createNotificationChannels(this)
    }
} 
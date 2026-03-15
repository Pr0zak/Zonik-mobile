package com.zonik.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ZonikApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        val playbackChannel = NotificationChannel(
            PLAYBACK_CHANNEL_ID,
            "Playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Music playback controls"
        }

        val syncChannel = NotificationChannel(
            SYNC_CHANNEL_ID,
            "Library Sync",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Library synchronization status"
            setSound(null, null)
        }

        val downloadChannel = NotificationChannel(
            DOWNLOAD_CHANNEL_ID,
            "Downloads",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Download progress and completion"
        }

        manager.createNotificationChannels(listOf(playbackChannel, syncChannel, downloadChannel))
    }

    companion object {
        const val PLAYBACK_CHANNEL_ID = "zonik_playback"
        const val SYNC_CHANNEL_ID = "zonik_sync"
        const val DOWNLOAD_CHANNEL_ID = "zonik_downloads"
    }
}

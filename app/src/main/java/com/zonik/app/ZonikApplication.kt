package com.zonik.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.zonik.app.data.repository.SettingsRepository
import com.zonik.app.media.CastManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class ZonikApplication : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var castManager: CastManager

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        com.zonik.app.data.DebugLog.init(this)
        setupUncaughtExceptionHandler()
        createNotificationChannels()
        try {
            castManager.initialize()
        } catch (e: Exception) {
            com.zonik.app.data.DebugLog.w("App", "Cast SDK init failed (no Play Services?): ${e.message}")
        }
    }

    private fun setupUncaughtExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Swallow network errors that escape coroutine/Retrofit exception handling
            val rootCause = generateSequence(throwable) { it.cause }.last()
            if (rootCause is java.net.UnknownHostException ||
                rootCause is java.net.ConnectException ||
                rootCause is java.net.SocketTimeoutException) {
                com.zonik.app.data.DebugLog.w("App", "Swallowed network error on ${thread.name}: ${rootCause.message}")
                return@setDefaultUncaughtExceptionHandler
            }
            // Also catch Retrofit HttpException (server errors like 500)
            if (rootCause is retrofit2.HttpException) {
                com.zonik.app.data.DebugLog.w("App", "Swallowed HTTP error on ${thread.name}: ${rootCause.message}")
                return@setDefaultUncaughtExceptionHandler
            }
            // All other exceptions: log and delegate to default handler
            com.zonik.app.data.DebugLog.e("CRASH", "!!! CRASH on thread '${thread.name}' !!!", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
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

    override fun newImageLoader(): ImageLoader {
        val coverArtCacheSizeMb = runBlocking { settingsRepository.coverArtCacheSizeMb.first() }
        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_cache"))
                    .maxSizeBytes(coverArtCacheSizeMb.toLong() * 1024 * 1024)
                    .build()
            }
            .build()
    }

    companion object {
        const val PLAYBACK_CHANNEL_ID = "zonik_playback"
        const val SYNC_CHANNEL_ID = "zonik_sync"
        const val DOWNLOAD_CHANNEL_ID = "zonik_downloads"
    }
}

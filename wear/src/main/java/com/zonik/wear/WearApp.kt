package com.zonik.wear

import android.app.Application
import com.zonik.core.api.ServerConfigProvider
import com.zonik.wear.data.NetworkMonitor
import com.zonik.wear.data.api.WearNetwork
import com.zonik.wear.data.repository.WearLibraryRepository
import com.zonik.wear.data.repository.WearSettingsRepository
import com.zonik.wear.media.WearMediaManager

/**
 * Manual DI for the watch app — wired lazily so a cold MediaSession bind
 * (tile / complication) doesn't pay for everything up front.
 */
class WearApp : Application() {

    val settings: WearSettingsRepository by lazy { WearSettingsRepository(this) }

    private val configProvider: ServerConfigProvider by lazy {
        ServerConfigProvider { settings.current() }
    }

    private val subsonicApi by lazy { WearNetwork.buildSubsonicApi(configProvider) }

    val library: WearLibraryRepository by lazy { WearLibraryRepository(subsonicApi, settings) }

    val networkMonitor: NetworkMonitor by lazy { NetworkMonitor(this) }

    lateinit var mediaManager: WearMediaManager
        private set

    override fun onCreate() {
        super.onCreate()
        mediaManager = WearMediaManager(this)
        // Eager-init so the StateFlow is hot by the time the first composable
        // collects it — saves an awkward "Unknown" flash on cold launch.
        networkMonitor
    }
}

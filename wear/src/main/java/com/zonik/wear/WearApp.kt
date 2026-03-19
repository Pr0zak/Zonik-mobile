package com.zonik.wear

import android.app.Application
import com.zonik.wear.media.WearMediaManager

class WearApp : Application() {

    lateinit var mediaManager: WearMediaManager
        private set

    override fun onCreate() {
        super.onCreate()
        mediaManager = WearMediaManager(this)
    }
}

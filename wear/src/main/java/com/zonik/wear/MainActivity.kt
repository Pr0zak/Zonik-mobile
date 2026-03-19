package com.zonik.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.zonik.wear.media.WearMediaManager
import com.zonik.wear.ui.navigation.WearNavHost
import com.zonik.wear.ui.theme.ZonikWearTheme

class MainActivity : ComponentActivity() {

    private lateinit var mediaManager: WearMediaManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mediaManager = (application as WearApp).mediaManager
        mediaManager.connect()

        setContent {
            ZonikWearTheme {
                WearNavHost(mediaManager = mediaManager)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaManager.disconnect()
    }
}

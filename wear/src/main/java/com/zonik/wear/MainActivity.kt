package com.zonik.wear

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.zonik.wear.media.WearMediaManager
import com.zonik.wear.ui.navigation.WearNavHost
import com.zonik.wear.ui.theme.ZonikWearTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var mediaManager: WearMediaManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as WearApp
        mediaManager = app.mediaManager
        // WearApp.onCreate already called connect() — don't re-trigger here,
        // and DON'T call disconnect on destroy. The MediaController is
        // app-scoped so it survives screen-off / ambient / activity restart.

        // Phase 1.2 probe — proves the wear data layer wires up against the
        // configured Zonik server. Replaced by real UI in Phase 3.
        lifecycleScope.launch {
            val cfg = app.settings.current()
            if (cfg == null) {
                Log.i(TAG, "Phase 1.2 probe: no ServerConfig yet — pair first (Phase 1.3)")
            } else {
                try {
                    val recent = app.library.listRecentAlbums(size = 5)
                    Log.i(
                        TAG,
                        "Phase 1.2 probe: fetched ${recent.size} recent albums from ${cfg.url}" +
                            recent.joinToString(prefix = " — ", separator = ", ") { it.name }
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Phase 1.2 probe failed: ${e.message}")
                }
            }
        }

        setContent {
            ZonikWearTheme {
                androidx.wear.compose.material3.AppScaffold {
                    WearNavHost(mediaManager = mediaManager)
                }
            }
        }
    }

    // No onDestroy override — the MediaController lives on the WearApp.
    // Leaving it bound across activity restarts means the user comes back to
    // a working browser instead of waiting for a re-bind.

    private companion object {
        const val TAG = "WearMain"
    }
}

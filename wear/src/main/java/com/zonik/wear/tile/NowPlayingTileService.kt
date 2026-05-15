package com.zonik.wear.tile

import android.content.ComponentName
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import com.zonik.wear.media.ZonikWearMediaService
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.guava.future

class NowPlayingTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        return scope.future {
            val (title, artist, isPlaying) = getCurrentTrackInfo()

            val layout = buildTileLayout(title, artist, isPlaying)

            TileBuilders.Tile.Builder()
                .setResourcesVersion("1")
                .setTileTimeline(
                    TimelineBuilders.Timeline.Builder()
                        .addTimelineEntry(
                            TimelineBuilders.TimelineEntry.Builder()
                                .setLayout(
                                    LayoutElementBuilders.Layout.Builder()
                                        .setRoot(layout)
                                        .build()
                                )
                                .build()
                        )
                        .build()
                )
                .setFreshnessIntervalMillis(30_000)
                .build()
        }
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        return scope.future {
            ResourceBuilders.Resources.Builder()
                .setVersion("1")
                .build()
        }
    }

    private fun buildTileLayout(
        title: String,
        artist: String,
        isPlaying: Boolean
    ): LayoutElementBuilders.LayoutElement {
        val clickable = ModifiersBuilders.Clickable.Builder()
            .setId("open_app")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            // applicationId is now com.zonik.app (matches phone for
                            // Wear Data Layer). The class lives under namespace
                            // com.zonik.wear though.
                            .setPackageName("com.zonik.app")
                            .setClassName("com.zonik.wear.MainActivity")
                            .build()
                    )
                    .build()
            )
            .build()

        val modifiers = ModifiersBuilders.Modifiers.Builder()
            .setClickable(clickable)
            .build()

        val primaryColor = ColorBuilders.argb(0xFFAFA9EC.toInt())
        val dimColor = ColorBuilders.argb(0xFFA09CB8.toInt())

        if (title.isEmpty()) {
            return LayoutElementBuilders.Box.Builder()
                .setModifiers(modifiers)
                .setWidth(DimensionBuilders.expand())
                .setHeight(DimensionBuilders.expand())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(
                    LayoutElementBuilders.Text.Builder()
                        .setText("Zonik")
                        .setFontStyle(
                            LayoutElementBuilders.FontStyle.Builder()
                                .setSize(DimensionBuilders.sp(16f))
                                .setColor(primaryColor)
                                .build()
                        )
                        .build()
                )
                .build()
        }

        return LayoutElementBuilders.Column.Builder()
            .setModifiers(modifiers)
            .setWidth(DimensionBuilders.expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(
                LayoutElementBuilders.Spacer.Builder()
                    .setHeight(DimensionBuilders.dp(12f))
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(if (isPlaying) "Now Playing" else "Paused")
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(DimensionBuilders.sp(11f))
                            .setColor(dimColor)
                            .build()
                    )
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Spacer.Builder()
                    .setHeight(DimensionBuilders.dp(4f))
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(title.take(20))
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(DimensionBuilders.sp(14f))
                            .setColor(primaryColor)
                            .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                            .build()
                    )
                    .setMaxLines(1)
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(artist.take(20))
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(DimensionBuilders.sp(12f))
                            .setColor(dimColor)
                            .build()
                    )
                    .setMaxLines(1)
                    .build()
            )
            .build()
    }

    private data class TrackInfo(val title: String, val artist: String, val isPlaying: Boolean)

    private suspend fun getCurrentTrackInfo(): TrackInfo {
        return try {
            // Bind to our own MediaService — same process, so this only succeeds
            // when the service is already running (i.e. user has launched the
            // wear app at least once this session). When it's not running we
            // gracefully fall back to a "Zonik" tile.
            val sessionToken = SessionToken(
                this,
                ComponentName(this, ZonikWearMediaService::class.java),
            )
            val future = MediaBrowser.Builder(this, sessionToken).buildAsync()
            val browser = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                future.addListener({
                    try {
                        cont.resume(future.get()) {}
                    } catch (e: Exception) {
                        cont.resume(null) {}
                    }
                }, MoreExecutors.directExecutor())
            }
            if (browser != null) {
                val item = browser.currentMediaItem
                val info = TrackInfo(
                    title = item?.mediaMetadata?.title?.toString() ?: "",
                    artist = item?.mediaMetadata?.artist?.toString() ?: "",
                    isPlaying = browser.isPlaying
                )
                MediaBrowser.releaseFuture(future)
                info
            } else {
                TrackInfo("", "", false)
            }
        } catch (e: Exception) {
            TrackInfo("", "", false)
        }
    }
}

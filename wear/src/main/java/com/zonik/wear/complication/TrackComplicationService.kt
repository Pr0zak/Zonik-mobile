package com.zonik.wear.complication

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.zonik.wear.MainActivity

class TrackComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder("Track Title").build(),
                contentDescription = PlainComplicationText.Builder("Now playing").build()
            ).build()
            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                text = PlainComplicationText.Builder("Track Title — Artist Name").build(),
                contentDescription = PlainComplicationText.Builder("Now playing").build()
            ).build()
            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
                value = 0.5f,
                min = 0f,
                max = 1f,
                contentDescription = PlainComplicationText.Builder("Playback progress").build()
            )
                .setText(PlainComplicationText.Builder("50%").build())
                .build()
            else -> null
        }
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val tapIntent = Intent(this, MainActivity::class.java)
        val tapAction = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val (title, artist, progress) = getCurrentTrackInfo()

        return when (request.complicationType) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder(title.ifEmpty { "Zonik" }).build(),
                contentDescription = PlainComplicationText.Builder("Now playing").build()
            )
                .setTapAction(tapAction)
                .build()

            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                text = PlainComplicationText.Builder(
                    if (title.isNotEmpty() && artist.isNotEmpty()) "$title — $artist"
                    else title.ifEmpty { "Zonik" }
                ).build(),
                contentDescription = PlainComplicationText.Builder("Now playing").build()
            )
                .setTapAction(tapAction)
                .build()

            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
                value = progress,
                min = 0f,
                max = 1f,
                contentDescription = PlainComplicationText.Builder("Playback progress").build()
            )
                .setText(PlainComplicationText.Builder(title.ifEmpty { "Zonik" }).build())
                .setTapAction(tapAction)
                .build()

            else -> null
        }
    }

    private data class TrackInfo(val title: String, val artist: String, val progress: Float)

    private suspend fun getCurrentTrackInfo(): TrackInfo {
        return try {
            val sessionToken = SessionToken(
                this,
                ComponentName("com.zonik.app", "com.zonik.app.media.ZonikMediaService")
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
                val duration = browser.duration.coerceAtLeast(1L)
                val position = browser.currentPosition.coerceAtLeast(0L)
                val info = TrackInfo(
                    title = item?.mediaMetadata?.title?.toString() ?: "",
                    artist = item?.mediaMetadata?.artist?.toString() ?: "",
                    progress = (position.toFloat() / duration).coerceIn(0f, 1f)
                )
                MediaBrowser.releaseFuture(future)
                info
            } else {
                TrackInfo("", "", 0f)
            }
        } catch (e: Exception) {
            TrackInfo("", "", 0f)
        }
    }
}

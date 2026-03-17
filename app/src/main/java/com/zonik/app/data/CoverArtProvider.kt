package com.zonik.app.data

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.zonik.app.data.repository.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

/**
 * ContentProvider that serves cover art images from the Subsonic server.
 * Used by Android Auto (and other external processes) that can't access
 * the app's OkHttpClient or cleartext HTTP directly.
 *
 * URI format: content://com.zonik.app.artwork/{coverArtId}[/{size}]
 */
class CoverArtProvider : ContentProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface CoverArtEntryPoint {
        fun settingsRepository(): SettingsRepository
        fun okHttpClient(): OkHttpClient
    }

    companion object {
        const val AUTHORITY = "com.zonik.app.artwork"

        fun buildUri(coverArtId: String, size: Int = 300): Uri {
            return Uri.parse("content://$AUTHORITY/$coverArtId/$size")
        }
    }

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = "image/*"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val pathSegments = uri.pathSegments
        if (pathSegments.isEmpty()) return null
        val coverArtId = pathSegments[0]
        val size = pathSegments.getOrNull(1)?.toIntOrNull() ?: 300

        val context = context ?: return null
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            CoverArtEntryPoint::class.java
        )
        val settingsRepository = entryPoint.settingsRepository()
        val okHttpClient = entryPoint.okHttpClient()

        val config = runBlocking { settingsRepository.serverConfig.first() } ?: return null
        val serverUrl = config.url.trimEnd('/')
        val salt = (1..16).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")
        val token = md5("${config.apiKey}$salt")
        val url = "$serverUrl/rest/getCoverArt.view?id=$coverArtId&size=$size" +
            "&u=${config.username}&t=$token&s=$salt&v=1.16.1&c=ZonikApp"

        // Cache to disk to avoid re-fetching
        val cacheDir = File(context.cacheDir, "cover_art")
        cacheDir.mkdirs()
        val cacheFile = File(cacheDir, "${coverArtId}_$size")
        if (cacheFile.exists() && cacheFile.length() > 0) {
            return ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        return try {
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return null
            val bytes = response.body?.bytes() ?: return null
            cacheFile.writeBytes(bytes)
            ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (e: Exception) {
            DebugLog.w("CoverArtProvider", "Failed to fetch cover art: ${e.message}")
            null
        }
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
}

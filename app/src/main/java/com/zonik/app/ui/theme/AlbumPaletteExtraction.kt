package com.zonik.app.ui.theme

import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult

private const val CACHE_LIMIT = 64

private val paletteCache = object : LinkedHashMap<String, AlbumPalette>(CACHE_LIMIT, 0.75f, true) {
    override fun removeEldestEntry(eldest: Map.Entry<String, AlbumPalette>): Boolean = size > CACHE_LIMIT
}

@Synchronized
private fun cacheGet(key: String): AlbumPalette? = paletteCache[key]

@Synchronized
private fun cachePut(key: String, value: AlbumPalette) {
    paletteCache[key] = value
}

@Composable
fun rememberAlbumPalette(coverArtId: String?): AlbumPalette? {
    val context = LocalContext.current
    var palette by remember(coverArtId) {
        mutableStateOf<AlbumPalette?>(coverArtId?.let { cacheGet(it) })
    }

    LaunchedEffect(coverArtId) {
        if (coverArtId == null) {
            palette = null
            return@LaunchedEffect
        }
        cacheGet(coverArtId)?.let {
            palette = it
            return@LaunchedEffect
        }
        try {
            val url = "http://localhost/rest/getCoverArt.view?id=$coverArtId&size=200"
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .build()
            val result = context.imageLoader.execute(request)
            if (result is SuccessResult) {
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap ?: return@LaunchedEffect
                val p = Palette.from(bitmap).clearFilters().generate()
                val dark = Color(p.getDarkMutedColor(p.getDarkVibrantColor(0xFF372A6E.toInt())))
                val vibrant = Color(p.getVibrantColor(p.getLightVibrantColor(0xFFB5A2E8.toInt())))
                val deep = Color(p.getDarkVibrantColor(p.getDarkMutedColor(0xFF0A0814.toInt())))
                val extracted = AlbumPalette(dark = dark, vibrant = vibrant, deep = deep)
                cachePut(coverArtId, extracted)
                palette = extracted
            }
        } catch (_: Exception) {
        }
    }

    return palette
}

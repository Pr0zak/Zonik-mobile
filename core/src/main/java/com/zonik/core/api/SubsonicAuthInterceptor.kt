package com.zonik.core.api

import android.util.Log
import com.zonik.core.util.md5
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Appends Subsonic auth query params (u, t, s, v, c, f) to every outgoing
 * request, using the current ServerConfig from the host module.
 *
 * Stays out of Hilt — both :app and :wear construct this directly. The
 * caller controls the client identifier (`c=` param) so wear can identify
 * itself separately if we want per-device usage stats.
 */
class SubsonicAuthInterceptor(
    private val configProvider: ServerConfigProvider,
    private val clientName: String = "ZonikApp",
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val config = runBlocking { configProvider.current() }
            ?: return chain.proceed(chain.request())

        val salt = generateSalt()
        val token = md5("${config.apiKey}$salt")

        val url = chain.request().url.newBuilder()
            .addQueryParameter("u", config.username)
            .addQueryParameter("t", token)
            .addQueryParameter("s", salt)
            .addQueryParameter("v", "1.16.1")
            .addQueryParameter("c", clientName)
            .addQueryParameter("f", "json")
            .build()

        val request = chain.request().newBuilder()
            .url(url)
            .build()

        val response = chain.proceed(request)
        val path = url.encodedPath
        if (response.isSuccessful) {
            Log.d(TAG, "$path → ${response.code}")
        } else {
            Log.e(TAG, "$path failed: ${response.code} ${response.message}")
        }
        return response
    }

    private fun generateSalt(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..16).map { chars.random() }.joinToString("")
    }

    private companion object {
        const val TAG = "SubsonicAuth"
    }
}

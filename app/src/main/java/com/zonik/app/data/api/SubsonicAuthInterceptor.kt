package com.zonik.app.data.api

import com.zonik.app.data.DebugLog
import com.zonik.app.data.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import com.zonik.app.util.md5
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubsonicAuthInterceptor @Inject constructor(
    private val settingsRepository: SettingsRepository
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val config = runBlocking { settingsRepository.serverConfig.first() }
            ?: return chain.proceed(chain.request())

        val salt = generateSalt()
        val token = md5("${config.apiKey}$salt")

        val url = chain.request().url.newBuilder()
            .addQueryParameter("u", config.username)
            .addQueryParameter("t", token)
            .addQueryParameter("s", salt)
            .addQueryParameter("v", "1.16.1")
            .addQueryParameter("c", "ZonikApp")
            .addQueryParameter("f", "json")
            .build()

        val request = chain.request().newBuilder()
            .url(url)
            .build()

        val response = chain.proceed(request)
        val path = url.encodedPath
        DebugLog.d("API", "$path → ${response.code}")
        if (!response.isSuccessful) {
            DebugLog.e("API", "$path failed: ${response.code} ${response.message}")
        }
        return response
    }

    private fun generateSalt(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..16).map { chars.random() }.joinToString("")
    }

}

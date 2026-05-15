package com.zonik.wear.data.api

import com.zonik.core.api.ServerConfigProvider
import com.zonik.core.api.SubsonicApi
import com.zonik.core.api.SubsonicAuthInterceptor
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Manual DI for the watch — keeps wiring obvious and avoids dragging Hilt
 * into the wear module.
 *
 * Each request goes through:
 *   1. dynamicBaseUrl — rewrites scheme/host/port from the current ServerConfig
 *   2. SubsonicAuthInterceptor — appends Subsonic auth query params
 *   3. HttpLoggingInterceptor — basic line logging via okhttp3
 */
object WearNetwork {

    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    fun buildSubsonicApi(configProvider: ServerConfigProvider): SubsonicApi {
        val client = buildOkHttp(configProvider)
        return Retrofit.Builder()
            .baseUrl("http://localhost/") // rewritten by dynamicBaseUrl interceptor
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SubsonicApi::class.java)
    }

    fun buildOkHttp(configProvider: ServerConfigProvider): OkHttpClient {
        val dynamicBaseUrl = Interceptor { chain ->
            val cfg = runBlocking { configProvider.current() }
                ?: return@Interceptor chain.proceed(chain.request())
            val base = cfg.url.trimEnd('/').toHttpUrl()
            val original = chain.request().url
            val rewritten = original.newBuilder()
                .scheme(base.scheme)
                .host(base.host)
                .port(base.port)
                .build()
            chain.proceed(chain.request().newBuilder().url(rewritten).build())
        }

        return OkHttpClient.Builder()
            .addInterceptor(dynamicBaseUrl)
            .addInterceptor(SubsonicAuthInterceptor(configProvider, clientName = "ZonikWear"))
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}

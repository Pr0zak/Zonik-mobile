package com.zonik.app.di

import android.content.Context
import com.zonik.app.data.api.SubsonicApi
import com.zonik.app.data.api.SubsonicAuthInterceptor
import com.zonik.app.data.api.ZonikApi
import com.zonik.app.data.db.ZonikDatabase
import com.zonik.app.data.repository.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ZonikApiClient

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
        authInterceptor: SubsonicAuthInterceptor,
        settingsRepository: SettingsRepository
    ): OkHttpClient {
        val httpCache = okhttp3.Cache(File(context.cacheDir, "http_cache"), 50L * 1024 * 1024) // 50 MB
        // Dynamic base URL interceptor — rewrites every request to the current server URL
        val dynamicBaseUrlInterceptor = Interceptor { chain ->
            val serverUrl = runBlocking {
                settingsRepository.serverConfig.first()?.url
            }

            if (serverUrl == null) {
                chain.proceed(chain.request())
            } else {
                val originalUrl = chain.request().url
                val newBaseUrl = serverUrl.trimEnd('/').toHttpUrl()

                val newUrl = originalUrl.newBuilder()
                    .scheme(newBaseUrl.scheme)
                    .host(newBaseUrl.host)
                    .port(newBaseUrl.port)
                    .build()

                val newRequest = chain.request().newBuilder()
                    .url(newUrl)
                    .build()

                chain.proceed(newRequest)
            }
        }

        return OkHttpClient.Builder()
            .cache(httpCache)
            .addInterceptor(dynamicBaseUrlInterceptor)
            .addInterceptor(authInterceptor)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @ZonikApiClient
    fun provideZonikApiClient(
        authInterceptor: SubsonicAuthInterceptor,
        settingsRepository: SettingsRepository
    ): OkHttpClient {
        val dynamicBaseUrlInterceptor = Interceptor { chain ->
            val serverUrl = runBlocking {
                settingsRepository.serverConfig.first()?.url
            }

            if (serverUrl == null) {
                chain.proceed(chain.request())
            } else {
                val originalUrl = chain.request().url
                val newBaseUrl = serverUrl.trimEnd('/').toHttpUrl()

                val newUrl = originalUrl.newBuilder()
                    .scheme(newBaseUrl.scheme)
                    .host(newBaseUrl.host)
                    .port(newBaseUrl.port)
                    .build()

                val newRequest = chain.request().newBuilder()
                    .url(newUrl)
                    .build()

                chain.proceed(newRequest)
            }
        }

        return OkHttpClient.Builder()
            .addInterceptor(dynamicBaseUrlInterceptor)
            .addInterceptor(authInterceptor)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideSubsonicApi(
        client: OkHttpClient,
        json: Json
    ): SubsonicApi {
        // Base URL is a placeholder — dynamicBaseUrlInterceptor rewrites it
        return Retrofit.Builder()
            .baseUrl("http://localhost/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SubsonicApi::class.java)
    }

    @Provides
    @Singleton
    fun provideZonikApi(
        @ZonikApiClient client: OkHttpClient,
        json: Json
    ): ZonikApi {
        return Retrofit.Builder()
            .baseUrl("http://localhost/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ZonikApi::class.java)
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ZonikDatabase {
        return ZonikDatabase.create(context)
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Provides
    @Singleton
    fun provideSimpleCache(
        @ApplicationContext context: Context,
        settingsRepository: SettingsRepository
    ): SimpleCache {
        val cacheSizeMb = runBlocking {
            settingsRepository.audioCacheSizeMb.first()
        }
        val cacheDir = File(context.cacheDir, "exoplayer_audio_cache")
        val evictor = LeastRecentlyUsedCacheEvictor(cacheSizeMb.toLong() * 1024 * 1024)
        return SimpleCache(cacheDir, evictor, StandaloneDatabaseProvider(context))
    }
}

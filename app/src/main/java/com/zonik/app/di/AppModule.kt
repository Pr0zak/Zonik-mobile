package com.zonik.app.di

import android.content.Context
import com.zonik.app.data.api.SubsonicApi
import com.zonik.app.data.api.SubsonicAuthInterceptor
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
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

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
        authInterceptor: SubsonicAuthInterceptor,
        settingsRepository: SettingsRepository
    ): OkHttpClient {
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
    fun provideDatabase(@ApplicationContext context: Context): ZonikDatabase {
        return ZonikDatabase.create(context)
    }
}

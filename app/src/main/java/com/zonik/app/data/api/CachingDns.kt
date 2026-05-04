package com.zonik.app.data.api

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zonik.app.data.DebugLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dnsCacheDataStore by preferencesDataStore(name = "dns_cache")

@Singleton
class CachingDns @Inject constructor(
    @ApplicationContext context: Context,
) : Dns {
    private val store = context.dnsCacheDataStore
    private val mem = ConcurrentHashMap<String, List<InetAddress>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            try {
                val prefs = store.data.first()
                for ((k, v) in prefs.asMap()) {
                    if (k is Preferences.Key<*> && v is String && v.isNotBlank()) {
                        try {
                            mem[k.name] = listOf(InetAddress.getByName(v))
                        } catch (_: Exception) { }
                    }
                }
                DebugLog.d("CachingDns", "Restored ${mem.size} cached DNS entries")
            } catch (e: Exception) {
                DebugLog.w("CachingDns", "DNS cache restore failed: ${e.message}")
            }
        }
    }

    override fun lookup(hostname: String): List<InetAddress> {
        return try {
            val addrs = Dns.SYSTEM.lookup(hostname)
            if (addrs.isNotEmpty()) cache(hostname, addrs)
            addrs
        } catch (e: UnknownHostException) {
            val cached = mem[hostname]
            if (cached != null) {
                DebugLog.w("CachingDns", "DNS failed for $hostname; using cached ${cached.first().hostAddress}")
                cached
            } else {
                throw e
            }
        }
    }

    private fun cache(hostname: String, addrs: List<InetAddress>) {
        mem[hostname] = addrs
        val ip = addrs.first().hostAddress ?: return
        scope.launch {
            try {
                store.edit { it[stringPreferencesKey(hostname)] = ip }
            } catch (_: Exception) { }
        }
    }
}

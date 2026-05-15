package com.zonik.core.api

import com.zonik.core.model.ServerConfig

/**
 * Tiny indirection so SubsonicAuthInterceptor (in :core) doesn't have to know
 * about the host module's persistence layer. Each module wires up an
 * implementation backed by its own DataStore / settings repository.
 */
fun interface ServerConfigProvider {
    suspend fun current(): ServerConfig?
}

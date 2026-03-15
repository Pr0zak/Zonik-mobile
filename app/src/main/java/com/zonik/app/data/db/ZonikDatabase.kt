package com.zonik.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ArtistEntity::class,
        AlbumEntity::class,
        TrackEntity::class,
        PendingScrobbleEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ZonikDatabase : RoomDatabase() {

    abstract fun artistDao(): ArtistDao
    abstract fun albumDao(): AlbumDao
    abstract fun trackDao(): TrackDao
    abstract fun scrobbleDao(): PendingScrobbleDao

    companion object {
        fun create(context: Context): ZonikDatabase {
            return Room.databaseBuilder(
                context,
                ZonikDatabase::class.java,
                "zonik.db"
            ).fallbackToDestructiveMigration().build()
        }
    }
}

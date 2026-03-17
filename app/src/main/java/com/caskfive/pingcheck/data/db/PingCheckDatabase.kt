package com.caskfive.pingcheck.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        PingSessionEntity::class,
        PingResultEntity::class,
        TracerouteSessionEntity::class,
        TracerouteHopEntity::class,
        FavoriteEntity::class,
    ],
    views = [HistoryViewItem::class],
    version = 2,
    exportSchema = true,
)
abstract class PingCheckDatabase : RoomDatabase() {
    abstract fun pingDao(): PingDao
    abstract fun tracerouteDao(): TracerouteDao
    abstract fun favoritesDao(): FavoritesDao
    abstract fun historyDao(): HistoryDao
}

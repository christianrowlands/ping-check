package com.caskfive.pingcheck.di

import android.content.Context
import androidx.room.Room
import com.caskfive.pingcheck.data.db.FavoritesDao
import com.caskfive.pingcheck.data.db.HistoryDao
import com.caskfive.pingcheck.data.db.PingCheckDatabase
import com.caskfive.pingcheck.data.db.PingDao
import com.caskfive.pingcheck.data.db.TracerouteDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// TODO: Add SQLCipher encryption — requires key management design and migration from unencrypted DB

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PingCheckDatabase {
        return Room.databaseBuilder(
            context,
            PingCheckDatabase::class.java,
            "pingcheck.db"
        )
            // Only allow destructive migration from version 1 (initial release).
            // For future versions, implement proper Migration objects to preserve user data.
            .fallbackToDestructiveMigrationFrom(1, 2)
            .build()
    }

    @Provides
    fun providePingDao(database: PingCheckDatabase): PingDao = database.pingDao()

    @Provides
    fun provideTracerouteDao(database: PingCheckDatabase): TracerouteDao = database.tracerouteDao()

    @Provides
    fun provideFavoritesDao(database: PingCheckDatabase): FavoritesDao = database.favoritesDao()

    @Provides
    fun provideHistoryDao(database: PingCheckDatabase): HistoryDao = database.historyDao()
}

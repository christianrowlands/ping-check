package com.caskfive.pingcheck.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoritesDao {
    @Insert
    suspend fun insert(favorite: FavoriteEntity): Long

    @Update
    suspend fun update(favorite: FavoriteEntity)

    @Query("SELECT * FROM favorites ORDER BY sort_order ASC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE host = :host LIMIT 1")
    suspend fun getByHost(host: String): FavoriteEntity?

    @Query("DELETE FROM favorites WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COALESCE(MAX(sort_order), 0) + 1 FROM favorites")
    suspend fun getNextSortOrder(): Int
}

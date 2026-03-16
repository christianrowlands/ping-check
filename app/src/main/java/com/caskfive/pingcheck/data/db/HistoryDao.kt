package com.caskfive.pingcheck.data.db

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history_view ORDER BY start_time DESC")
    fun observeAll(): Flow<List<HistoryViewItem>>

    @Query("SELECT * FROM history_view WHERE target_host LIKE '%' || :query || '%' OR resolved_ip LIKE '%' || :query || '%' ORDER BY start_time DESC")
    fun search(query: String): Flow<List<HistoryViewItem>>

    @Query("SELECT * FROM history_view WHERE type = :type ORDER BY start_time DESC")
    fun observeByType(type: String): Flow<List<HistoryViewItem>>
}

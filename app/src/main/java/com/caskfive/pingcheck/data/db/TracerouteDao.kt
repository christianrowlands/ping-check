package com.caskfive.pingcheck.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TracerouteDao {
    @Insert
    suspend fun insertSession(session: TracerouteSessionEntity): Long

    @Update
    suspend fun updateSession(session: TracerouteSessionEntity)

    @Insert
    suspend fun insertHop(hop: TracerouteHopEntity)

    @Query("SELECT * FROM traceroute_sessions WHERE id = :id")
    suspend fun getSession(id: Long): TracerouteSessionEntity?

    @Query("SELECT * FROM traceroute_sessions WHERE id = :id")
    fun observeSession(id: Long): Flow<TracerouteSessionEntity?>

    @Query("SELECT * FROM traceroute_hops WHERE session_id = :sessionId ORDER BY hop_number ASC")
    fun observeHops(sessionId: Long): Flow<List<TracerouteHopEntity>>

    @Query("SELECT * FROM traceroute_hops WHERE session_id = :sessionId ORDER BY hop_number ASC")
    suspend fun getHops(sessionId: Long): List<TracerouteHopEntity>

    @Query("SELECT * FROM traceroute_sessions ORDER BY start_time DESC")
    fun observeAllSessions(): Flow<List<TracerouteSessionEntity>>

    @Query("DELETE FROM traceroute_sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("DELETE FROM traceroute_sessions WHERE start_time < :beforeTimestamp")
    suspend fun deleteSessionsBefore(beforeTimestamp: Long)

    @Query("SELECT is_timeout FROM traceroute_hops WHERE session_id = :sessionId ORDER BY hop_number ASC LIMIT :limit")
    suspend fun getHopTimeoutFlags(sessionId: Long, limit: Int = 6): List<Boolean>
}

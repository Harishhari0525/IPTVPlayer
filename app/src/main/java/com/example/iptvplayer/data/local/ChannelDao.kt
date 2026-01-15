package com.example.iptvplayer.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.iptvplayer.data.model.Channel
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(channels: List<Channel>)

    @Query("SELECT * FROM channels")
    fun getAllChannels(): Flow<List<Channel>>

    @Query("SELECT * FROM channels WHERE isFavorite = 1")
    fun getFavorites(): Flow<List<Channel>>

    @Query("SELECT * FROM channels ORDER BY lastUpdated DESC LIMIT 50")
    fun getRecents(): Flow<List<Channel>>

    @Query("UPDATE channels SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE channels SET lastUpdated = :timestamp WHERE id = :id")
    suspend fun updateLastWatched(id: Long, timestamp: Long)

    // NEW: Delete features
    @Query("DELETE FROM channels")
    suspend fun deleteAllChannels()

    @Query("UPDATE channels SET lastUpdated = 0") // Clear history just resets timestamps
    suspend fun clearRecents()

    @Query("UPDATE channels SET isFavorite = 0") // Clear favorites just resets flags
    suspend fun clearFavorites()

    @Query("DELETE FROM channels WHERE id = :id")
    suspend fun deleteChannelById(id: Long)
}
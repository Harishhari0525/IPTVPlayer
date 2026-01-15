package com.example.iptvplayer.data.model

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "channels")
@Immutable
@Serializable
data class Channel(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,
    val url: String,
    val logoUrl: String? = null,
    val group: String = "Uncategorized",
    val isFavorite: Boolean = false,

    // For "Continue Watching"
    val lastPlaybackPosition: Long = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)
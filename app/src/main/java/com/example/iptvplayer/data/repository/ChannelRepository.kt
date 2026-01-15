package com.example.iptvplayer.data.repository

import android.content.Context
import androidx.room.Room
import com.example.iptvplayer.data.local.AppDatabase
import com.example.iptvplayer.data.model.Channel
import com.example.iptvplayer.utils.M3UParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.InputStream
import androidx.core.content.edit
import org.json.JSONArray
import java.net.URL

class ChannelRepository(context: Context) {

    // Initialize Database
    private val database = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        "iptv_player.db"
    ).build()

    private val dao = database.channelDao()

    // --- Data Streams ---
    val allChannels: Flow<List<Channel>> = dao.getAllChannels()
    val favoriteChannels: Flow<List<Channel>> = dao.getFavorites()
    val recentChannels: Flow<List<Channel>> = dao.getRecents()

    // --- Actions ---

    // Updated to use the specific 'updateFavorite' query
    suspend fun toggleFavorite(channel: Channel) {
        dao.updateFavorite(channel.id, !channel.isFavorite)
    }

    suspend fun addToRecents(channelId: Long) {
        dao.updateLastWatched(channelId, System.currentTimeMillis())
    }

    // --- Playlist Loading (Append Mode) ---

    suspend fun loadPlaylist(inputStream: InputStream) {
        withContext(Dispatchers.IO) {
            val parsedChannels = M3UParser.parse(inputStream)
            dao.insertAll(parsedChannels)
        }
    }

    suspend fun loadPlaylistFromUrl(urlString: String) {
        withContext(Dispatchers.IO) {
            val stream = URL(urlString).openStream()
            val parsedChannels = M3UParser.parse(stream)
            dao.insertAll(parsedChannels)
        }
    }

    // --- Maintenance / Cleanup ---

    suspend fun deleteAll() {
        withContext(Dispatchers.IO) {
            dao.deleteAllChannels()
        }
    }

    suspend fun clearHistory() {
        withContext(Dispatchers.IO) {
            dao.clearRecents()
        }
    }

    suspend fun clearFavorites() {
        withContext(Dispatchers.IO) {
            dao.clearFavorites()
        }
    }

    private val prefs = context.getSharedPreferences("iptv_prefs", Context.MODE_PRIVATE)

    fun savePlaylistUrl(url: String) {
        prefs.edit { putString("saved_playlist_url", url) }
    }

    fun getSavedPlaylistUrl(): String? {
        return prefs.getString("saved_playlist_url", null)
    }

    suspend fun fetchAndMapLogos() {
        withContext(Dispatchers.IO) {
            try {
                // 1. Download the Dedicated Logos File
                // This file is strictly for mapping ID -> Logo URL
                val jsonString = URL("https://iptv-org.github.io/api/logos.json").readText()
                val jsonArray = JSONArray(jsonString)
                val logoMap = mutableMapOf<String, String>()

                // 2. Parse (Map "channel" -> "url")
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    if (obj.has("channel") && obj.has("url")) {
                        val id = obj.getString("channel") // e.g. "002RadioTV.do"
                        val url = obj.getString("url")
                        if (url.isNotBlank()) {
                            logoMap[id] = url
                        }
                    }
                }

                // 3. Update Database
                val currentChannels = dao.getAllChannelsSync()
                val updates = mutableListOf<Channel>()

                for (channel in currentChannels) {
                    if (channel.logoUrl.isNullOrBlank() && channel.tvgId.isNotBlank()) {
                        val newLogo = logoMap[channel.tvgId]
                        if (newLogo != null) {
                            updates.add(channel.copy(logoUrl = newLogo))
                        }
                    }
                }

                if (updates.isNotEmpty()) {
                    dao.updateAll(updates)
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun isChannelAlive(url: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS) // Fast fail
                    .readTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .build()

                val request = okhttp3.Request.Builder()
                    .url(url)
                    .head() // HEAD request only checks headers, doesn't download video
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .build()

                client.newCall(request).execute().use { response ->
                    response.isSuccessful // Returns true if 200 OK
                }
            } catch (_: Exception) {
                false
            }
        }
    }

    suspend fun deleteChannel(channelId: Long) {
        dao.deleteChannelById(channelId)
    }
}
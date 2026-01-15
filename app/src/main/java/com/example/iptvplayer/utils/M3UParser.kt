package com.example.iptvplayer.utils

import com.example.iptvplayer.data.model.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

object M3UParser {

    private val logoRegex = Regex("""tvg-logo="([^"]*)"""")
    private val groupRegex = Regex("""group-title="([^"]*)"""")
    private val nameRegex = Regex(""",([^,]*)$""")

    /**
     * Parses an M3U stream efficiently using Kotlin Sequences.
     * This avoids loading the entire file into memory.
     */
    suspend fun parse(inputStream: InputStream): List<Channel> = withContext(Dispatchers.IO) {
        val channels = mutableListOf<Channel>()

        // We use 'use' to ensure the reader is strictly closed after execution
        inputStream.bufferedReader().use { reader ->
            var currentLogo: String? = null
            var currentGroup: String? = null
            var currentName: String? = null

            reader.forEachLine { line ->
                val trimmed = line.trim()

                when {
                    trimmed.startsWith("#EXTINF:") -> {
                        // Extract metadata
                        currentLogo = logoRegex.find(trimmed)?.groupValues?.get(1)
                        currentGroup = groupRegex.find(trimmed)?.groupValues?.get(1) ?: "General"
                        currentName = nameRegex.find(trimmed)?.groupValues?.get(1)?.trim()
                            ?: trimmed.substringAfterLast(",", "Unknown Channel")
                    }
                    !trimmed.startsWith("#") && trimmed.isNotEmpty() -> {
                        // This is the URL line. Build the object.
                        if (currentName != null) {
                            channels.add(
                                Channel(
                                    name = currentName!!,
                                    url = trimmed,
                                    logoUrl = currentLogo,
                                    group = currentGroup!!
                                )
                            )
                        }
                        // Reset temporary vars
                        currentName = null
                        currentLogo = null
                        currentGroup = null
                    }
                }
            }
        }
        return@withContext channels
    }
}
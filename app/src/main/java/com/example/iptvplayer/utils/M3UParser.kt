package com.example.iptvplayer.utils

import com.example.iptvplayer.data.model.Channel
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

object M3UParser {
    fun parse(inputStream: InputStream): List<Channel> {
        val channels = mutableListOf<Channel>()
        val reader = BufferedReader(InputStreamReader(inputStream))

        var currentName = ""
        var currentLogo = ""
        var currentGroup = ""
        var currentTvgId = "" // <--- NEW VAR

        reader.forEachLine { line ->
            val trimmed = line.trim()

            if (trimmed.startsWith("#EXTINF:")) {
                // 1. Extract Logo
                currentLogo = Regex("""tvg-logo="([^"]*)"""").find(trimmed)?.groupValues?.get(1) ?: ""

                // 2. Extract Group
                currentGroup = Regex("""group-title="([^"]*)"""").find(trimmed)?.groupValues?.get(1) ?: "General"

                // 3. Extract TVG-ID (Crucial for Logo Lookup)
                currentTvgId = Regex("""tvg-id="([^"]*)"""").find(trimmed)?.groupValues?.get(1) ?: ""

                // 4. Extract Name
                currentName = trimmed.substringAfterLast(",", "Unknown Channel").trim()
            }
            else if (trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                if (currentName.isNotBlank()) {
                    channels.add(
                        Channel(
                            name = currentName,
                            url = trimmed,
                            logoUrl = currentLogo,
                            group = currentGroup,
                            tvgId = currentTvgId // <--- Save it
                        )
                    )
                    // Reset
                    currentName = ""
                    currentLogo = ""
                    currentGroup = ""
                    currentTvgId = ""
                }
            }
        }
        return channels
    }
}
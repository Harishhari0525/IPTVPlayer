package com.example.iptvplayer.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.iptvplayer.data.model.Channel
import com.example.iptvplayer.data.repository.ChannelRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChannelViewModel(private val repository: ChannelRepository) : ViewModel() {

    private val _allChannels: StateFlow<List<Channel>> = repository.allChannels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<Channel>> = repository.favoriteChannels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recents: StateFlow<List<Channel>> = repository.recentChannels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 2. UI State for Filtering
    private val _selectedGroup = MutableStateFlow("All")
    private val _searchQuery = MutableStateFlow("")

    // We expose _isLoading as a StateFlow for the UI
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _scanProgress = MutableStateFlow<String?>(null)
    val scanProgress: StateFlow<String?> = _scanProgress

    val groups: StateFlow<List<String>> = _allChannels.map { channels ->
        // Get unique group names, sort them, and ensure "All" is first
        val rawGroups = channels.map { it.group }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

        listOf("All") + rawGroups
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("All"))

    val filteredChannels: StateFlow<List<Channel>> = combine(
        _allChannels,
        _selectedGroup,
        _searchQuery
    ) { channels, group, query ->
        channels.filter { channel ->
            val matchesGroup = group == "All" || channel.group == group

            // Check Search Match
            val matchesSearch = query.isBlank() ||
                    channel.name.contains(query, ignoreCase = true)

            matchesGroup && matchesSearch
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Actions ---

    fun selectGroup(group: String) {
        _selectedGroup.value = group
    }

    fun search(query: String) {
        _searchQuery.value = query
    }

    fun removeDeadChannels() {
        viewModelScope.launch {
            _isLoading.value = true
            _scanProgress.value = "Starting Scan..."

            val currentList = _allChannels.value

            if (currentList.isEmpty()) {
                _scanProgress.value = null
                _isLoading.value = false
                return@launch
            }

            val total = currentList.size
            var deletedCount = 0
            for ((index, channel) in currentList.withIndex()) {

                if (index % 5 == 0) {
                    _scanProgress.value = "Checking ${index + 1} / $total"
                }

                // suspended call to network
                val isAlive = repository.isChannelAlive(channel.url)

                if (!isAlive) {
                    // suspended call to DB
                    repository.deleteChannel(channel.id)
                    deletedCount++
                }
            }

            // 3. Finish
            _scanProgress.value = null
            _isLoading.value = false
        }
    }

    fun toggleFavorite(channel: Channel) {
        viewModelScope.launch {
            repository.toggleFavorite(channel)
        }
    }

    fun markAsWatched(channel: Channel) {
        viewModelScope.launch {
            repository.addToRecents(channel.id)
        }
    }

    // --- Import Logic ---

    fun importPlaylist(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    repository.loadPlaylist(stream)
                    repository.fetchAndMapLogos()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun importPlaylistFromUrl(url: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.loadPlaylistFromUrl(url)

                repository.fetchAndMapLogos()

                repository.savePlaylistUrl(url)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }


    fun deleteAll() = viewModelScope.launch { repository.deleteAll() }
    fun clearHistory() = viewModelScope.launch { repository.clearHistory() }
    fun clearFavorites() = viewModelScope.launch { repository.clearFavorites() }

    init {
        val savedUrl = repository.getSavedPlaylistUrl()
        if (!savedUrl.isNullOrBlank()) {
            viewModelScope.launch {
                _isLoading.value = true
                try {
                    repository.loadPlaylistFromUrl(savedUrl)
                } catch (e: Exception) {
                    e.printStackTrace() // Update failed (offline?), keep old data
                } finally {
                    _isLoading.value = false
                }
            }
        }
    }

}

class ChannelViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChannelViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChannelViewModel(ChannelRepository(context)) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
package app.mindcore.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Items + server status for the UI. One per (server, key); refreshed on start and on pull-down. */
class Library(val api: Api?) {
    var items by mutableStateOf<List<ApiItem>>(emptyList())
        private set
    var status by mutableStateOf<Status?>(null)
        private set
    var upcoming by mutableStateOf<List<Upcoming>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    val paired get() = api != null

    suspend fun refresh() {
        val api = api ?: return
        loading = true
        try {
            status = api.status()
            items = api.items()
            upcoming = runCatching { api.upcoming() }.getOrDefault(emptyList())
            error = null
        } catch (e: Exception) {
            error = e.message ?: "Couldn't reach the server"
        } finally {
            loading = false
        }
    }

    /** Star/unstar right away, then tell the server; undo if the server says no. */
    suspend fun toggleFavorite(item: ApiItem) {
        val api = api ?: return
        val flipped = item.copy(favorite = !item.favorite)
        items = items.map { if (it.id == item.id) flipped else it }
        runCatching { api.setFavorite(item.id, flipped.favorite) }.onFailure {
            items = items.map { if (it.id == item.id) item else it }
            error = "Couldn't update favorite: ${it.message}"
        }
    }

    /** Keep the list in sync after a change made on the detail screen. */
    fun replace(item: ApiItem) {
        items = items.map { if (it.id == item.id) item.copy(sourceCount = it.sourceCount) else it }
    }
}

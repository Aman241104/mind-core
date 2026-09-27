package app.mindcore

import kotlinx.coroutines.flow.MutableStateFlow

/** Requests to open a screen, e.g. from a widget tap. The app UI consumes and clears them. */
object Nav {
    val openItem = MutableStateFlow<String?>(null)
    const val EXTRA_OPEN_ITEM = "app.mindcore.OPEN_ITEM"
}

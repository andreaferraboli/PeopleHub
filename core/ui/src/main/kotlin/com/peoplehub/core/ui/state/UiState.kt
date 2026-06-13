package com.peoplehub.core.ui.state

/**
 * The single, exhaustive contract every screen's state stream conforms to. ViewModels expose
 * `StateFlow<UiState<T>>` and each screen renders all four cases.
 */
sealed interface UiState<out T> {

    /** Data is being loaded for the first time. */
    data object Loading : UiState<Nothing>

    /** Data loaded successfully. */
    data class Success<T>(val data: T) : UiState<T>

    /** A recoverable error occurred; [message] is a user-presentable description. */
    data class Error(val message: String) : UiState<Nothing>

    /** Loading completed but there is nothing to show. */
    data object Empty : UiState<Nothing>
}

/** Maps the [UiState.Success] payload, leaving the other states untouched. */
inline fun <T, R> UiState<T>.map(transform: (T) -> R): UiState<R> = when (this) {
    is UiState.Success -> UiState.Success(transform(data))
    is UiState.Error -> this
    UiState.Loading -> UiState.Loading
    UiState.Empty -> UiState.Empty
}

/**
 * Builds a list-oriented [UiState] from a loaded [list]: [UiState.Empty] when the list is empty,
 * otherwise [UiState.Success].
 */
fun <T> List<T>.toListUiState(): UiState<List<T>> =
    if (isEmpty()) UiState.Empty else UiState.Success(this)

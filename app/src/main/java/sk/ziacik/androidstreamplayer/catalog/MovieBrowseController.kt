package sk.ziacik.androidstreamplayer.catalog

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MovieBrowseUiState(
	val isLoading: Boolean = true,
	val trending: List<Movie> = emptyList(),
	val errorMessage: String? = null,
)

class MovieBrowseController(
	private val scope: CoroutineScope,
	private val loadTrending: suspend () -> List<Movie>,
) {
	private val _state = MutableStateFlow(MovieBrowseUiState())
	val state: StateFlow<MovieBrowseUiState> = _state.asStateFlow()

	init {
		refresh()
	}

	fun retry() = refresh()

	private fun refresh() {
		_state.value = _state.value.copy(
			isLoading = true,
			errorMessage = null,
		)
		scope.launch {
			try {
				_state.value = MovieBrowseUiState(
					isLoading = false,
					trending = loadTrending(),
				)
			} catch (error: CancellationException) {
				throw error
			} catch (error: Exception) {
				_state.value = MovieBrowseUiState(
					isLoading = false,
					errorMessage = "Couldn’t load trending movies",
				)
			}
		}
	}
}

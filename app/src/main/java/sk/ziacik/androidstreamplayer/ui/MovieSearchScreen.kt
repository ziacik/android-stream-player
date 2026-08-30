package sk.ziacik.androidstreamplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sk.ziacik.androidstreamplayer.catalog.Movie
import sk.ziacik.androidstreamplayer.catalog.MovieSearchController

@Composable
fun MovieSearchScreen(
	controller: MovieSearchController,
	onMovieSelected: (Movie) -> Unit,
	modifier: Modifier = Modifier,
) {
	val state by controller.state.collectAsState()
	val searchRequester = remember { FocusRequester() }
	val gridState = rememberLazyGridState()
	val movieIds = state.results.map { it.tmdbId }
	val posterRequesters = remember(movieIds) {
		movieIds.associateWith { FocusRequester() }
	}
	val firstPosterRequester = state.results.firstOrNull()?.let { posterRequesters[it.tmdbId] }
	var initialFocusHandled by remember { mutableStateOf(false) }

	LaunchedEffect(state.results, state.focusedMovieId) {
		if (initialFocusHandled) return@LaunchedEffect

		val rememberedMovieId = state.focusedMovieId
		if (rememberedMovieId != null) {
			val index = state.results.indexOfFirst { it.tmdbId == rememberedMovieId }
			if (index >= 0) {
				gridState.scrollToItem(index)
				posterRequesters[rememberedMovieId]?.requestFocus()
				initialFocusHandled = true
			}
		} else {
			searchRequester.requestFocus()
			initialFocusHandled = true
		}
	}

	Surface(
		modifier = modifier.fillMaxSize(),
		color = Color.Black,
	) {
		Box(
			modifier = Modifier
				.fillMaxSize()
				.background(
					Brush.verticalGradient(
						listOf(
							MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
							MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
							Color.Black,
						),
					),
				),
		) {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.background(
						Brush.radialGradient(
							colors = listOf(
								MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
								Color.Transparent,
							),
							radius = 900f,
						),
					),
			)

			Column(
				modifier = Modifier
					.fillMaxSize()
					.padding(horizontal = 64.dp, vertical = 36.dp),
			) {
				MovieSearchHeader(compact = state.results.isNotEmpty())
				Spacer(Modifier.height(if (state.results.isNotEmpty()) 18.dp else 28.dp))

				OutlinedTextField(
					value = state.query,
					onValueChange = controller::setQuery,
					modifier = Modifier
						.width(620.dp)
						.testTag("movie-search-input")
						.focusRequester(searchRequester)
						.focusProperties {
							firstPosterRequester?.let { down = it }
						},
					placeholder = { Text("Movie title") },
					singleLine = true,
					keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
					keyboardActions = KeyboardActions(onSearch = { controller.searchNow() }),
				)

				Spacer(Modifier.height(22.dp))

				when {
					state.isSearching && state.results.isEmpty() -> MovieSearchLoading()
					state.errorMessage != null -> MovieSearchError(onRetry = controller::retry)
					state.results.isNotEmpty() -> {
						if (state.isSearching) {
							Row(
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(10.dp),
							) {
								CircularProgressIndicator(modifier = Modifier.width(18.dp))
								Text(
									text = "Updating…",
									style = MaterialTheme.typography.labelLarge,
									color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f),
								)
							}
							Spacer(Modifier.height(10.dp))
						}

						LazyVerticalGrid(
							columns = GridCells.Fixed(6),
							state = gridState,
							modifier = Modifier.fillMaxSize(),
							contentPadding = PaddingValues(bottom = 40.dp),
							horizontalArrangement = Arrangement.spacedBy(18.dp),
							verticalArrangement = Arrangement.spacedBy(20.dp),
						) {
							itemsIndexed(
								items = state.results,
								key = { _, movie -> movie.tmdbId },
							) { index, movie ->
								val requester = posterRequesters.getValue(movie.tmdbId)
								MoviePosterCard(
									movie = movie,
									onClick = { onMovieSelected(movie) },
									focusRequester = requester,
									upFocusRequester = searchRequester.takeIf { index < POSTER_COLUMNS },
									onFocused = { controller.setFocusedMovie(movie.tmdbId) },
									modifier = Modifier.fillMaxWidth(),
								)
							}
						}
					}
					state.query.trim().length >= 2 -> MovieSearchEmpty()
					else -> MovieSearchLanding()
				}
			}
		}
	}
}

@Composable
private fun MovieSearchHeader(compact: Boolean) {
	Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
		Text(
			text = "KINO",
			style = MaterialTheme.typography.labelLarge,
			fontWeight = FontWeight.Black,
			letterSpacing = 3.sp,
			color = MaterialTheme.colorScheme.secondary,
		)
		Text(
			text = if (compact) "Search movies" else "What are we watching?",
			fontSize = if (compact) 28.sp else 42.sp,
			fontWeight = FontWeight.SemiBold,
			color = MaterialTheme.colorScheme.onBackground,
		)
		if (!compact) {
			Text(
				text = "Pick the movie first. We’ll find the best available versions after.",
				style = MaterialTheme.typography.bodyLarge,
				color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.64f),
			)
		}
	}
}

@Composable
private fun MovieSearchLoading() {
	Row(
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(14.dp),
	) {
		CircularProgressIndicator()
		Text(
			text = "Searching movies…",
			style = MaterialTheme.typography.titleMedium,
			color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
		)
	}
}

@Composable
private fun MovieSearchError(onRetry: () -> Unit) {
	Column(
		modifier = Modifier.testTag("movie-search-error"),
		verticalArrangement = Arrangement.spacedBy(12.dp),
	) {
		Text(
			text = "Couldn’t search movies",
			style = MaterialTheme.typography.titleLarge,
			color = MaterialTheme.colorScheme.onBackground,
		)
		Text(
			text = "The movie catalog didn’t answer. Your current search stays here.",
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f),
		)
		Button(onClick = onRetry) {
			Text("Retry")
		}
	}
}

@Composable
private fun MovieSearchEmpty() {
	Column(
		modifier = Modifier.testTag("movie-search-empty"),
		verticalArrangement = Arrangement.spacedBy(6.dp),
	) {
		Text(
			text = "No movies found",
			style = MaterialTheme.typography.titleLarge,
			color = MaterialTheme.colorScheme.onBackground,
		)
		Text(
			text = "Try the original title or a shorter search.",
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.58f),
		)
	}
}

@Composable
private fun MovieSearchLanding() {
	Column(
		modifier = Modifier.padding(top = 26.dp),
		verticalArrangement = Arrangement.spacedBy(8.dp),
	) {
		Text(
			text = "Movies, not filenames.",
			fontSize = 26.sp,
			fontWeight = FontWeight.Medium,
			color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
		)
		Text(
			text = "Start typing and Kino will show poster suggestions from the catalog.",
			style = MaterialTheme.typography.bodyLarge,
			color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.56f),
		)
	}
}

private const val POSTER_COLUMNS = 6

package sk.ziacik.androidstreamplayer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sk.ziacik.androidstreamplayer.catalog.Movie
import sk.ziacik.androidstreamplayer.catalog.MovieBrowseController
import sk.ziacik.androidstreamplayer.watch.WatchProgressEntry

@Composable
fun HomeScreen(
	controller: MovieBrowseController,
	resumeWatching: List<WatchProgressEntry>,
	onMovieSelected: (Movie) -> Unit,
	onResumeWatching: (WatchProgressEntry) -> Unit,
	onCancelResumeWatching: () -> Unit,
	onRemoveResumeWatching: (Int) -> Unit,
	startingResumeMovieId: Int?,
	onSearch: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val state by controller.state.collectAsState()
	val searchRequester = remember { FocusRequester() }
	val resumeIds = resumeWatching.map { it.movie.tmdbId }
	val resumeRequesters = remember(resumeIds) {
		resumeIds.associateWith { FocusRequester() }
	}
	val trendingIds = state.trending.map { it.tmdbId }
	val trendingRequesters = remember(trendingIds) {
		trendingIds.associateWith { FocusRequester() }
	}
	val firstResumeRequester = resumeWatching.firstOrNull()?.let { resumeRequesters[it.movie.tmdbId] }
	val firstTrendingRequester = state.trending.firstOrNull()?.let { trendingRequesters[it.tmdbId] }
	val firstContentRequester = firstResumeRequester ?: firstTrendingRequester
	var initialFocusHandled by remember { mutableStateOf(false) }
	var resumeActionEntry by remember { mutableStateOf<WatchProgressEntry?>(null) }

	BackHandler(enabled = resumeActionEntry != null) {
		resumeActionEntry = null
	}
	BackHandler(enabled = resumeActionEntry == null && startingResumeMovieId != null) {
		onCancelResumeWatching()
	}

	LaunchedEffect(firstContentRequester, state.isLoading) {
		if (initialFocusHandled) return@LaunchedEffect
		if (firstContentRequester != null) {
			firstContentRequester.requestFocus()
			initialFocusHandled = true
		} else if (!state.isLoading) {
			searchRequester.requestFocus()
			initialFocusHandled = true
		}
	}

	Surface(
		modifier = modifier
			.fillMaxSize()
			.testTag("home-dashboard"),
		color = Color.Black,
	) {
		Box(
			modifier = Modifier
				.fillMaxSize()
				.background(
					Brush.verticalGradient(
						listOf(
							MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f),
							MaterialTheme.colorScheme.background.copy(alpha = 0.97f),
							Color.Black,
						),
					),
				),
		) {
			Column(
				modifier = Modifier
					.fillMaxSize()
					.padding(horizontal = 64.dp),
			) {
				Spacer(Modifier.height(32.dp))
				HomeHeader(
					searchRequester = searchRequester,
					downFocusRequester = firstContentRequester,
					onSearch = onSearch,
				)
				Spacer(Modifier.height(24.dp))

				LazyColumn(
					modifier = Modifier
						.fillMaxWidth()
						.weight(1f),
					verticalArrangement = Arrangement.spacedBy(24.dp),
					contentPadding = PaddingValues(bottom = 32.dp),
				) {
					if (resumeWatching.isNotEmpty()) {
						item(key = "resume-watching") {
							HomeResumeWatchingRow(
								entries = resumeWatching,
								focusRequesters = resumeRequesters,
								upFocusRequester = searchRequester,
								downFocusRequester = firstTrendingRequester,
								onResume = onResumeWatching,
								onCancelStarting = onCancelResumeWatching,
								onOpenActions = { resumeActionEntry = it },
								startingMovieId = startingResumeMovieId,
							)
						}
					}

					item(key = "trending") {
						HomeTrendingRow(
							movies = state.trending,
							focusRequesters = trendingRequesters,
							upFocusRequester = firstResumeRequester ?: searchRequester,
							downFocusRequester = null,
							isLoading = state.isLoading,
							errorMessage = state.errorMessage,
							onRetry = controller::retry,
							onMovieSelected = onMovieSelected,
						)
					}
				}
			}

			resumeActionEntry?.let { entry ->
				HomeResumeWatchingActions(
					entry = entry,
					onRemove = {
						onRemoveResumeWatching(entry.movie.tmdbId)
						resumeActionEntry = null
					},
					onCancel = { resumeActionEntry = null },
				)
			}
		}
	}
}

@Composable
private fun HomeHeader(
	searchRequester: FocusRequester,
	downFocusRequester: FocusRequester?,
	onSearch: () -> Unit,
) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.SpaceBetween,
	) {
		Row(
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(28.dp),
		) {
			Text(
				text = "KINO",
				fontWeight = FontWeight.Black,
				letterSpacing = 3.sp,
				color = MaterialTheme.colorScheme.secondary,
			)
			Text(
				text = "Home",
				style = MaterialTheme.typography.titleMedium,
				fontWeight = FontWeight.SemiBold,
				color = MaterialTheme.colorScheme.onBackground,
			)
		}

		Button(
			onClick = onSearch,
			modifier = Modifier
				.testTag("home-search-nav")
				.focusRequester(searchRequester)
				.focusProperties {
					down = downFocusRequester ?: FocusRequester.Default
				},
		) {
			Text("Search")
		}
	}
}

@Composable
private fun HomeTrendingRow(
	movies: List<Movie>,
	focusRequesters: Map<Int, FocusRequester>,
	upFocusRequester: FocusRequester?,
	downFocusRequester: FocusRequester?,
	isLoading: Boolean,
	errorMessage: String?,
	onRetry: () -> Unit,
	onMovieSelected: (Movie) -> Unit,
) {
	Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
		Text(
			text = "Trending",
			style = MaterialTheme.typography.titleLarge,
			fontWeight = FontWeight.SemiBold,
			color = MaterialTheme.colorScheme.onBackground,
		)

		when {
			movies.isNotEmpty() -> {
				LazyRow(
					horizontalArrangement = Arrangement.spacedBy(18.dp),
					contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
				) {
					items(
						items = movies,
						key = { it.tmdbId },
					) { movie ->
						MoviePosterCard(
							movie = movie,
							onClick = { onMovieSelected(movie) },
							focusRequester = focusRequesters.getValue(movie.tmdbId),
							onFocused = {},
							upFocusRequester = upFocusRequester,
							downFocusRequester = downFocusRequester,
							testTag = "home-trending-${movie.tmdbId}",
							modifier = Modifier.width(HOME_POSTER_WIDTH),
						)
					}
				}
			}

			isLoading -> {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(12.dp),
				) {
					CircularProgressIndicator(modifier = Modifier.size(28.dp))
					Text(
						text = "Loading trending movies…",
						color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
					)
				}
			}

			errorMessage != null -> {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(16.dp),
				) {
					Text(
						text = errorMessage,
						color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
					)
					Button(onClick = onRetry) {
						Text("Retry")
					}
				}
			}
		}
	}
}

@Composable
private fun HomeResumeWatchingRow(
	entries: List<WatchProgressEntry>,
	focusRequesters: Map<Int, FocusRequester>,
	upFocusRequester: FocusRequester,
	downFocusRequester: FocusRequester?,
	onResume: (WatchProgressEntry) -> Unit,
	onCancelStarting: () -> Unit,
	onOpenActions: (WatchProgressEntry) -> Unit,
	startingMovieId: Int?,
) {
	Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
		Text(
			text = RESUME_WATCHING_LABEL,
			style = MaterialTheme.typography.titleLarge,
			fontWeight = FontWeight.SemiBold,
			color = MaterialTheme.colorScheme.onBackground,
		)
		LazyRow(
			horizontalArrangement = Arrangement.spacedBy(18.dp),
			contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
		) {
			items(
				items = entries,
				key = { it.movie.tmdbId },
			) { entry ->
				HomeResumeWatchingCard(
					entry = entry,
					focusRequester = focusRequesters.getValue(entry.movie.tmdbId),
					upFocusRequester = upFocusRequester,
					downFocusRequester = downFocusRequester,
					onResume = { onResume(entry) },
					onCancelStarting = onCancelStarting,
					onOpenActions = { onOpenActions(entry) },
					isStarting = startingMovieId == entry.movie.tmdbId,
				)
			}
		}
	}
}

@Composable
private fun HomeResumeWatchingCard(
	entry: WatchProgressEntry,
	focusRequester: FocusRequester,
	upFocusRequester: FocusRequester,
	downFocusRequester: FocusRequester?,
	onResume: () -> Unit,
	onCancelStarting: () -> Unit,
	onOpenActions: () -> Unit,
	isStarting: Boolean,
) {
	var longPressTriggered by remember { mutableStateOf(false) }
	val progress = if (entry.durationMs > 0L) {
		(entry.positionMs.toFloat() / entry.durationMs.toFloat()).coerceIn(0f, 1f)
	} else {
		0f
	}

	Box(
		modifier = Modifier
			.width(HOME_POSTER_WIDTH)
			.semantics {
				if (resumeWatchingOptionsEnabled(isStarting)) {
					onLongClick(label = "Show options") {
						onOpenActions()
						true
					}
				}
			}
			.onPreviewKeyEvent { event ->
				if (!resumeWatchingOptionsEnabled(isStarting)) {
					false
				} else {
					val isConfirm = event.key == Key.DirectionCenter ||
						event.key == Key.Enter ||
						event.key == Key.NumPadEnter
					when (
						resumeWatchingKeyAction(
							isConfirm = isConfirm,
							isDown = event.type == KeyEventType.KeyDown,
							isUp = event.type == KeyEventType.KeyUp,
							repeatCount = event.nativeKeyEvent.repeatCount,
							longPressTriggered = longPressTriggered,
						)
					) {
						ResumeWatchingKeyAction.OpenActions -> {
							longPressTriggered = true
							onOpenActions()
							true
						}
						ResumeWatchingKeyAction.Consume -> {
							if (event.type == KeyEventType.KeyUp) longPressTriggered = false
							true
						}
						ResumeWatchingKeyAction.PassThrough -> false
					}
				}
			},
	) {
		MoviePosterCard(
			movie = entry.movie,
			onClick = {
				when (resumeWatchingActivation(isStarting)) {
					ResumeWatchingActivation.Resume -> onResume()
					ResumeWatchingActivation.Cancel -> onCancelStarting()
				}
			},
			focusRequester = focusRequester,
			onFocused = {},
			upFocusRequester = upFocusRequester,
			downFocusRequester = downFocusRequester,
			testTag = "resume-watching-${entry.movie.tmdbId}",
			modifier = Modifier.fillMaxWidth(),
		)

		LinearProgressIndicator(
			progress = { progress },
			modifier = Modifier
				.align(Alignment.BottomCenter)
				.fillMaxWidth()
				.padding(horizontal = 4.dp, vertical = 4.dp)
				.height(4.dp),
		)

		if (isStarting) {
			Box(
				modifier = Modifier
					.fillMaxWidth()
					.aspectRatio(2f / 3f)
					.background(Color.Black.copy(alpha = 0.58f))
					.testTag("resume-watching-starting-${entry.movie.tmdbId}"),
				contentAlignment = Alignment.Center,
			) {
				CircularProgressIndicator(
					modifier = Modifier.size(38.dp),
					strokeWidth = 3.dp,
				)
			}
		}
	}
}

@Composable
private fun HomeResumeWatchingActions(
	entry: WatchProgressEntry,
	onRemove: () -> Unit,
	onCancel: () -> Unit,
) {
	val cancelRequester = remember { FocusRequester() }
	var waitingForConfirmRelease by remember(entry.movie.tmdbId) { mutableStateOf(true) }
	var removeFocused by remember { mutableStateOf(false) }
	var cancelFocused by remember { mutableStateOf(false) }
	val removeFocusStyle = resumeActionFocusStyle(removeFocused)
	val cancelFocusStyle = resumeActionFocusStyle(cancelFocused)
	val buttonShape = RoundedCornerShape(12.dp)

	LaunchedEffect(entry.movie.tmdbId) {
		cancelRequester.requestFocus()
	}

	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(Color.Black.copy(alpha = 0.76f))
			.testTag("resume-watching-actions")
			.onPreviewKeyEvent { event ->
				val isConfirm = event.key == Key.DirectionCenter ||
					event.key == Key.Enter ||
					event.key == Key.NumPadEnter
				val decision = resumeActionsKeyDecision(
					isConfirm = isConfirm,
					isUp = event.type == KeyEventType.KeyUp,
					waitingForRelease = waitingForConfirmRelease,
				)
				waitingForConfirmRelease = decision.waitingForRelease
				decision.consume
			},
		contentAlignment = Alignment.Center,
	) {
		Surface(
			modifier = Modifier.width(430.dp),
			shape = RoundedCornerShape(20.dp),
			color = MaterialTheme.colorScheme.surface,
			shadowElevation = 18.dp,
		) {
			Column(
				modifier = Modifier.padding(24.dp),
				verticalArrangement = Arrangement.spacedBy(12.dp),
			) {
				Text(
					text = entry.movie.title,
					style = MaterialTheme.typography.titleLarge,
					fontWeight = FontWeight.SemiBold,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
				Text(
					text = "$RESUME_WATCHING_LABEL options",
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
				)
				Spacer(Modifier.height(4.dp))
				Button(
					onClick = onRemove,
					modifier = Modifier
						.fillMaxWidth()
						.graphicsLayer {
							scaleX = removeFocusStyle.scale
							scaleY = removeFocusStyle.scale
						}
						.border(
							width = removeFocusStyle.borderWidthDp.dp,
							color = if (removeFocused) MaterialTheme.colorScheme.secondary else Color.Transparent,
							shape = buttonShape,
						)
						.onFocusChanged { removeFocused = it.isFocused }
						.testTag("resume-watching-remove"),
					shape = buttonShape,
				) {
					Text(resumeWatchingRemoveLabel())
				}
				TextButton(
					onClick = onCancel,
					modifier = Modifier
						.fillMaxWidth()
						.graphicsLayer {
							scaleX = cancelFocusStyle.scale
							scaleY = cancelFocusStyle.scale
						}
						.border(
							width = cancelFocusStyle.borderWidthDp.dp,
							color = if (cancelFocused) MaterialTheme.colorScheme.secondary else Color.Transparent,
							shape = buttonShape,
						)
						.onFocusChanged { cancelFocused = it.isFocused }
						.focusRequester(cancelRequester)
						.testTag("resume-watching-cancel"),
					shape = buttonShape,
				) {
					Text("Cancel")
				}
			}
		}
	}
}

private val HOME_POSTER_WIDTH = 154.dp

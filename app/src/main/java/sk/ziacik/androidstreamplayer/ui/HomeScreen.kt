package sk.ziacik.androidstreamplayer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
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
	homeState: HomeScreenState = rememberHomeScreenState(),
) {
	val state by controller.state.collectAsState()
	val homeRequester = remember { FocusRequester() }
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
	val resumeDestination = homeState.lastResumeMovieId?.let(resumeRequesters::get) ?: firstResumeRequester
	val trendingDestination = homeState.lastTrendingMovieId?.let(trendingRequesters::get) ?: firstTrendingRequester
	val contentDestination = resumeDestination ?: trendingDestination
	val restoredFocusRequester = when (val target = homeState.focusedTarget) {
		HomeFocusTarget.Home -> homeRequester
		HomeFocusTarget.Search -> searchRequester
		is HomeFocusTarget.Resume -> resumeRequesters[target.movieId]
		is HomeFocusTarget.Trending -> trendingRequesters[target.movieId]
		null -> null
	}
	val waitingForTrendingRestore = homeState.focusedTarget is HomeFocusTarget.Trending &&
		restoredFocusRequester == null &&
		state.isLoading
	var initialFocusHandled by remember { mutableStateOf(false) }
	var resumeActionEntry by remember { mutableStateOf<WatchProgressEntry?>(null) }
	var pendingResumeRemovalId by remember { mutableStateOf<Int?>(null) }

	BackHandler(enabled = resumeActionEntry != null) {
		if (pendingResumeRemovalId == null) {
			resumeActionEntry = null
		}
	}
	BackHandler(enabled = resumeActionEntry == null && startingResumeMovieId != null) {
		onCancelResumeWatching()
	}

	LaunchedEffect(
		restoredFocusRequester,
		contentDestination,
		state.isLoading,
		homeState.focusedTarget,
	) {
		if (initialFocusHandled) return@LaunchedEffect

		when {
			restoredFocusRequester != null -> {
				restoredFocusRequester.requestFocus()
				initialFocusHandled = true
			}

			waitingForTrendingRestore -> Unit

			contentDestination != null -> {
				contentDestination.requestFocus()
				initialFocusHandled = true
			}

			!state.isLoading -> {
				searchRequester.requestFocus()
				initialFocusHandled = true
			}
		}
	}

	LaunchedEffect(
		pendingResumeRemovalId,
		resumeIds,
		trendingIds,
		state.isLoading,
	) {
		val removedMovieId = pendingResumeRemovalId ?: return@LaunchedEffect
		if (removedMovieId in resumeIds) return@LaunchedEffect

		val remainingResumeId = homeState.lastResumeMovieId
			?.takeIf { it != removedMovieId && it in resumeIds }
			?: resumeIds.firstOrNull()
		val destinationTrendingId = homeState.lastTrendingMovieId
			?.takeIf { it in trendingIds }
			?: trendingIds.firstOrNull()

		val destinationTarget: HomeFocusTarget
		val destinationRequester: FocusRequester
		when {
			remainingResumeId != null -> {
				destinationTarget = HomeFocusTarget.Resume(remainingResumeId)
				destinationRequester = resumeRequesters.getValue(remainingResumeId)
			}

			destinationTrendingId != null -> {
				destinationTarget = HomeFocusTarget.Trending(destinationTrendingId)
				destinationRequester = trendingRequesters.getValue(destinationTrendingId)
			}

			!state.isLoading -> {
				destinationTarget = HomeFocusTarget.Search
				destinationRequester = searchRequester
			}

			else -> return@LaunchedEffect
		}

		// The actions overlay is already gone and its focus was explicitly cleared.
		// Wait for the lazy layout to expose the replacement row before requesting focus.
		homeState.contentListState.scrollToItem(0)
		withFrameNanos { }

		homeState.lastResumeMovieId = remainingResumeId
		when (destinationTarget) {
			HomeFocusTarget.Home -> homeState.focusedTarget = HomeFocusTarget.Home
			HomeFocusTarget.Search -> homeState.focusedTarget = HomeFocusTarget.Search
			is HomeFocusTarget.Resume -> homeState.focusedTarget = destinationTarget
			is HomeFocusTarget.Trending -> {
				homeState.lastTrendingMovieId = destinationTarget.movieId
				homeState.focusedTarget = destinationTarget
			}
		}

		fun requestFocusSafely(requester: FocusRequester): Boolean =
			runCatching { requester.requestFocus() }.getOrDefault(false)

		var restored = requestFocusSafely(destinationRequester)
		if (!restored) {
			withFrameNanos { }
			restored = requestFocusSafely(destinationRequester)
		}
		if (!restored) {
			homeState.focusedTarget = HomeFocusTarget.Search
			requestFocusSafely(searchRequester)
		}

		pendingResumeRemovalId = null
	}

	LaunchedEffect(
		resumeIds,
		trendingIds,
		resumeActionEntry,
		pendingResumeRemovalId,
		state.isLoading,
	) {
		if (resumeActionEntry != null || pendingResumeRemovalId != null) return@LaunchedEffect

		when (val target = homeState.focusedTarget) {
			is HomeFocusTarget.Resume -> {
				if (target.movieId !in resumeIds) {
					homeState.lastResumeMovieId = null
					homeState.contentListState.scrollToItem(0)
					withFrameNanos { }
					when {
						firstResumeRequester != null -> {
							val movieId = resumeIds.first()
							homeState.lastResumeMovieId = movieId
							homeState.focusedTarget = HomeFocusTarget.Resume(movieId)
							firstResumeRequester.requestFocus()
						}

						trendingDestination != null -> {
							val movieId = homeState.lastTrendingMovieId
								?.takeIf { it in trendingIds }
								?: trendingIds.first()
							homeState.lastTrendingMovieId = movieId
							homeState.focusedTarget = HomeFocusTarget.Trending(movieId)
							trendingDestination.requestFocus()
						}

						!state.isLoading -> {
							homeState.focusedTarget = HomeFocusTarget.Search
							searchRequester.requestFocus()
						}
					}
				}
			}

			is HomeFocusTarget.Trending -> {
				if (target.movieId !in trendingIds && !state.isLoading) {
					homeState.lastTrendingMovieId = null
					homeState.contentListState.scrollToItem(0)
					withFrameNanos { }
					when {
						firstTrendingRequester != null -> {
							val movieId = trendingIds.first()
							homeState.lastTrendingMovieId = movieId
							homeState.focusedTarget = HomeFocusTarget.Trending(movieId)
							firstTrendingRequester.requestFocus()
						}

						firstResumeRequester != null -> {
							val movieId = resumeIds.first()
							homeState.lastResumeMovieId = movieId
							homeState.focusedTarget = HomeFocusTarget.Resume(movieId)
							firstResumeRequester.requestFocus()
						}

						else -> {
							homeState.focusedTarget = HomeFocusTarget.Search
							searchRequester.requestFocus()
						}
					}
				}
			}

			else -> Unit
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
					homeRequester = homeRequester,
					searchRequester = searchRequester,
					downFocusRequester = contentDestination,
					onHomeFocused = { homeState.focusedTarget = HomeFocusTarget.Home },
					onSearchFocused = { homeState.focusedTarget = HomeFocusTarget.Search },
					onHome = { homeState.focusedTarget = HomeFocusTarget.Home },
					onSearch = {
						homeState.focusedTarget = HomeFocusTarget.Search
						onSearch()
					},
				)
				Spacer(Modifier.height(24.dp))

				LazyColumn(
					state = homeState.contentListState,
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
								listState = homeState.resumeRowState,
								focusRequesters = resumeRequesters,
								onFocused = { entry ->
									homeState.lastResumeMovieId = entry.movie.tmdbId
									homeState.focusedTarget = HomeFocusTarget.Resume(entry.movie.tmdbId)
								},
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
							listState = homeState.trendingRowState,
							focusRequesters = trendingRequesters,
							onFocused = { movie ->
								homeState.lastTrendingMovieId = movie.tmdbId
								homeState.focusedTarget = HomeFocusTarget.Trending(movie.tmdbId)
							},
							isLoading = state.isLoading,
							errorMessage = state.errorMessage,
							onRetry = controller::retry,
							onMovieSelected = { movie ->
								homeState.lastTrendingMovieId = movie.tmdbId
								homeState.focusedTarget = HomeFocusTarget.Trending(movie.tmdbId)
								onMovieSelected(movie)
							},
						)
					}
				}
			}

			resumeActionEntry?.let { entry ->
				HomeResumeWatchingActions(
					entry = entry,
					onRemove = {
						if (pendingResumeRemovalId == null) {
							pendingResumeRemovalId = entry.movie.tmdbId
							resumeActionEntry = null
							onRemoveResumeWatching(entry.movie.tmdbId)
						}
					},
					onCancel = {
						if (pendingResumeRemovalId == null) {
							resumeActionEntry = null
						}
					},
				)
			}
		}
	}
}

@Composable
private fun HomeHeader(
	homeRequester: FocusRequester,
	searchRequester: FocusRequester,
	downFocusRequester: FocusRequester?,
	onHomeFocused: () -> Unit,
	onSearchFocused: () -> Unit,
	onHome: () -> Unit,
	onSearch: () -> Unit,
) {
	var homeFocused by remember { mutableStateOf(false) }
	var searchFocused by remember { mutableStateOf(false) }

	Row(
		modifier = Modifier.fillMaxWidth(),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(28.dp),
	) {
		Text(
			text = "KINO",
			fontWeight = FontWeight.Black,
			letterSpacing = 3.sp,
			color = MaterialTheme.colorScheme.secondary,
		)
		TextButton(
			onClick = onHome,
			modifier = Modifier
				.testTag("home-home-nav")
				.focusRequester(homeRequester)
				.onFocusChanged {
					homeFocused = it.isFocused
					if (it.isFocused) onHomeFocused()
				}
				.focusProperties {
					left = FocusRequester.Cancel
					right = searchRequester
					down = downFocusRequester ?: FocusRequester.Default
				},
		) {
			Text(
				text = "Home",
				style = MaterialTheme.typography.titleMedium,
				fontWeight = FontWeight.SemiBold,
				color = if (homeFocused) {
					MaterialTheme.colorScheme.secondary
				} else {
					MaterialTheme.colorScheme.onBackground
				},
			)
		}
		Button(
			onClick = onSearch,
			modifier = Modifier
				.size(48.dp)
				.testTag("home-search-nav")
				.semantics { contentDescription = "Search" }
				.focusRequester(searchRequester)
				.graphicsLayer {
					val scale = if (searchFocused) 1.08f else 1f
					scaleX = scale
					scaleY = scale
				}
				.onFocusChanged {
					searchFocused = it.isFocused
					if (it.isFocused) onSearchFocused()
				}
				.focusProperties {
					left = homeRequester
					right = FocusRequester.Cancel
					down = downFocusRequester ?: FocusRequester.Default
				},
			contentPadding = PaddingValues(0.dp),
	) {
		SearchGlyph()
	}
}

@Composable
private fun SearchGlyph() {
	val color = MaterialTheme.colorScheme.onPrimary
	Canvas(modifier = Modifier.size(21.dp)) {
		val strokeWidth = 2.2.dp.toPx()
		val radius = size.minDimension * 0.27f
		val center = Offset(size.width * 0.42f, size.height * 0.42f)
		drawCircle(
			color = color,
			radius = radius,
			center = center,
			style = Stroke(width = strokeWidth),
		)
		drawLine(
			color = color,
			start = Offset(
				center.x + radius * 0.7f,
				center.y + radius * 0.7f,
			),
			end = Offset(size.width * 0.84f, size.height * 0.84f),
			strokeWidth = strokeWidth,
			cap = StrokeCap.Round,
		)
	}
}

@Composable
private fun HomeTrendingRow(
	movies: List<Movie>,
	listState: LazyListState,
	focusRequesters: Map<Int, FocusRequester>,
	onFocused: (Movie) -> Unit,
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
					state = listState,
					modifier = Modifier.focusRestorer(),
					horizontalArrangement = Arrangement.spacedBy(18.dp),
					contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
				) {
					itemsIndexed(
						items = movies,
						key = { _, movie -> movie.tmdbId },
					) { index, movie ->
						MoviePosterCard(
							movie = movie,
							onClick = { onMovieSelected(movie) },
							focusRequester = focusRequesters.getValue(movie.tmdbId),
							onFocused = { onFocused(movie) },
							leftFocusRequester = if (index == 0) FocusRequester.Cancel else null,
							rightFocusRequester = if (index == movies.lastIndex) FocusRequester.Cancel else null,
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
	listState: LazyListState,
	focusRequesters: Map<Int, FocusRequester>,
	onFocused: (WatchProgressEntry) -> Unit,
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
			state = listState,
			modifier = Modifier.focusRestorer(),
			horizontalArrangement = Arrangement.spacedBy(18.dp),
			contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
		) {
			itemsIndexed(
				items = entries,
				key = { _, entry -> entry.movie.tmdbId },
			) { index, entry ->
				HomeResumeWatchingCard(
					entry = entry,
					focusRequester = focusRequesters.getValue(entry.movie.tmdbId),
					leftFocusRequester = if (index == 0) FocusRequester.Cancel else null,
					rightFocusRequester = if (index == entries.lastIndex) FocusRequester.Cancel else null,
					onFocused = { onFocused(entry) },
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
	leftFocusRequester: FocusRequester?,
	rightFocusRequester: FocusRequester?,
	onFocused: () -> Unit,
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
			leftFocusRequester = leftFocusRequester,
			rightFocusRequester = rightFocusRequester,
			onFocused = onFocused,
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
	val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
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
					onClick = {
						focusManager.clearFocus(force = true)
						onRemove()
					},
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
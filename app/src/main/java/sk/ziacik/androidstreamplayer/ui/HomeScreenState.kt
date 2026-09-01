package sk.ziacik.androidstreamplayer.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

sealed interface HomeFocusTarget {
	data object Home : HomeFocusTarget
	data object Search : HomeFocusTarget
	data class Resume(val movieId: Int) : HomeFocusTarget
	data class Trending(val movieId: Int) : HomeFocusTarget
}

class HomeScreenState internal constructor(
	val contentListState: LazyListState,
	val resumeRowState: LazyListState,
	val trendingRowState: LazyListState,
) {
	var focusedTarget by mutableStateOf<HomeFocusTarget?>(null)
		internal set

	var lastResumeMovieId by mutableStateOf<Int?>(null)
		internal set

	var lastTrendingMovieId by mutableStateOf<Int?>(null)
		internal set
}

@Composable
fun rememberHomeScreenState(): HomeScreenState {
	val contentListState = rememberLazyListState()
	val resumeRowState = rememberLazyListState()
	val trendingRowState = rememberLazyListState()

	return remember(contentListState, resumeRowState, trendingRowState) {
		HomeScreenState(
			contentListState = contentListState,
			resumeRowState = resumeRowState,
			trendingRowState = trendingRowState,
		)
	}
}

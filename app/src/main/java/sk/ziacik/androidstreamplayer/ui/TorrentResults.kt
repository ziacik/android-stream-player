package sk.ziacik.androidstreamplayer.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import sk.ziacik.androidstreamplayer.search.TorrentSearchResult
import sk.ziacik.androidstreamplayer.search.TorrentSearchUiState

@Composable
fun TorrentResults(
	state: TorrentSearchUiState,
	onPlay: (TorrentSearchResult) -> Unit,
	onRetry: () -> Unit,
	modifier: Modifier = Modifier,
) {
	Column(
		modifier = modifier,
		verticalArrangement = Arrangement.spacedBy(14.dp),
	) {
		Text(
			text = "Available versions",
			style = MaterialTheme.typography.headlineSmall,
			fontWeight = FontWeight.SemiBold,
		)

		when {
			state.isSearching -> {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(12.dp),
				) {
					CircularProgressIndicator()
					Text(
						text = "Finding versions…",
						style = MaterialTheme.typography.bodyLarge,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}
			}

			state.errorMessage != null -> {
				Column(
					modifier = Modifier.testTag("torrent-results-error"),
					verticalArrangement = Arrangement.spacedBy(12.dp),
				) {
					Text(
						text = "Couldn’t find versions",
						style = MaterialTheme.typography.titleMedium,
					)
					Text(
						text = "Torrent search failed. The movie is still here, so you can retry without going back.",
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
					Button(onClick = onRetry) {
						Text("Retry")
					}
				}
			}

			state.results.isEmpty() -> {
				Column(
					modifier = Modifier.testTag("torrent-results-empty"),
					verticalArrangement = Arrangement.spacedBy(6.dp),
				) {
					Text(
						text = "No versions found",
						style = MaterialTheme.typography.titleMedium,
					)
					Text(
						text = "Try another movie or come back later.",
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}
			}

			else -> {
				val firstFocusRequester = remember { FocusRequester() }
				val resultIds = state.results.map(TorrentSearchResult::id)
				LaunchedEffect(resultIds) {
					firstFocusRequester.requestFocus()
				}

				LazyColumn(
					verticalArrangement = Arrangement.spacedBy(10.dp),
				) {
					itemsIndexed(
						items = state.results,
						key = { _, result -> result.id },
					) { index, result ->
						TorrentResultRow(
							result = result,
							onClick = { onPlay(result) },
							modifier = if (index == 0) {
								Modifier.focusRequester(firstFocusRequester)
							} else {
								Modifier
							},
						)
					}
				}
			}
		}
	}
}

@Composable
private fun TorrentResultRow(
	result: TorrentSearchResult,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	var focused by remember { mutableStateOf(false) }
	val scale by animateFloatAsState(
		targetValue = if (focused) 1.025f else 1f,
		label = "torrent-row-scale",
	)
	val shape = RoundedCornerShape(12.dp)

	Card(
		onClick = onClick,
		modifier = modifier
			.fillMaxWidth()
			.testTag("torrent-${result.id}")
			.onFocusChanged { focused = it.isFocused }
			.graphicsLayer {
				scaleX = scale
				scaleY = scale
			},
		shape = shape,
		border = if (focused) {
			BorderStroke(3.dp, MaterialTheme.colorScheme.secondary)
		} else {
			BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
		},
		colors = CardDefaults.cardColors(
			containerColor = if (focused) {
				MaterialTheme.colorScheme.surfaceVariant
			} else {
				MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)
			},
		),
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 18.dp, vertical = 15.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Surface(
				shape = RoundedCornerShape(7.dp),
				color = MaterialTheme.colorScheme.secondaryContainer,
			) {
				Text(
					text = result.quality ?: "AUTO",
					modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
					style = MaterialTheme.typography.labelLarge,
					fontWeight = FontWeight.Bold,
				)
			}

			Spacer(Modifier.width(14.dp))

			Column(
				modifier = Modifier.weight(1f),
				verticalArrangement = Arrangement.spacedBy(4.dp),
			) {
				Text(
					text = result.title,
					style = MaterialTheme.typography.titleMedium,
					fontWeight = FontWeight.SemiBold,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
				Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
					Text(
						text = result.sizeBytes?.let(::formatSize) ?: "Unknown size",
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
					Text(
						text = "${result.seeders ?: 0} seeds",
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
					Text(
						text = result.source ?: "Unknown source",
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
					)
				}
			}

			Spacer(Modifier.width(14.dp))
			Text(
				text = "PLAY",
				style = MaterialTheme.typography.labelLarge,
				fontWeight = FontWeight.Black,
				color = MaterialTheme.colorScheme.secondary,
			)
		}
	}
}

private fun formatSize(bytes: Long): String {
	val gib = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
	return String.format(Locale.US, "%.1f GiB", gib)
}

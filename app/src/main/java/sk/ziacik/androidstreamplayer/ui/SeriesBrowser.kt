package sk.ziacik.androidstreamplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sk.ziacik.androidstreamplayer.catalog.SeriesEpisode
import sk.ziacik.androidstreamplayer.catalog.SeriesUiState

@Composable
fun SeriesBrowser(
    state: SeriesUiState,
    onSeasonSelected: (Int) -> Unit,
    onEpisodeSelected: (SeriesEpisode) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Episodes",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        if (state.seasons.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 2.dp),
            ) {
                items(state.seasons, key = { it.number }) { season ->
                    if (season.number == state.selectedSeason) {
                        Button(
                            onClick = { onSeasonSelected(season.number) },
                            modifier = Modifier.testTag("season-${season.number}"),
                        ) {
                            Text("S${season.number}")
                        }
                    } else {
                        TextButton(
                            onClick = { onSeasonSelected(season.number) },
                            modifier = Modifier.testTag("season-${season.number}"),
                        ) {
                            Text("S${season.number}")
                        }
                    }
                }
            }
        }

        when {
            state.isLoading -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text("Loading episodes…")
                }
            }

            state.errorMessage != null -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(state.errorMessage)
                    Button(onClick = onRetry) { Text("Retry") }
                }
            }

            state.episodes.isEmpty() -> {
                Text(
                    text = "No episodes found",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(
                        items = state.episodes,
                        key = { episode -> "${episode.seasonNumber}:${episode.number}" },
                    ) { episode ->
                        EpisodeCard(
                            episode = episode,
                            onClick = { onEpisodeSelected(episode) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeCard(
    episode: SeriesEpisode,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("episode-${episode.seasonNumber}-${episode.number}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = "S%02dE%02d · %s".format(
                    episode.seasonNumber,
                    episode.number,
                    episode.name,
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            episode.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

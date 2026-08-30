package sk.ziacik.androidstreamplayer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import sk.ziacik.androidstreamplayer.catalog.Movie
import sk.ziacik.androidstreamplayer.catalog.tmdbBackdropUrl
import sk.ziacik.androidstreamplayer.catalog.tmdbPosterUrl
import sk.ziacik.androidstreamplayer.search.TorrentSearchController
import sk.ziacik.androidstreamplayer.search.TorrentSearchResult

@Composable
fun MovieDetailScreen(
	movie: Movie,
	torrentController: TorrentSearchController,
	onPlay: (TorrentSearchResult) -> Unit,
	onBack: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val torrentState by torrentController.state.collectAsState()

	LaunchedEffect(movie.tmdbId) {
		torrentController.open(movie)
	}
	BackHandler(onBack = onBack)

	Box(
		modifier = modifier
			.fillMaxSize()
			.testTag("movie-detail")
			.background(Color(0xFF090607)),
	) {
		tmdbBackdropUrl(movie.backdropPath)?.let { backdropUrl ->
			AsyncImage(
				model = backdropUrl,
				contentDescription = null,
				contentScale = ContentScale.Crop,
				modifier = Modifier
					.fillMaxWidth()
					.fillMaxHeight(0.68f),
			)
		}

		Box(
			modifier = Modifier
				.fillMaxSize()
				.background(
					Brush.verticalGradient(
						colorStops = arrayOf(
							0f to Color.Black.copy(alpha = 0.24f),
							0.42f to Color(0xFF090607).copy(alpha = 0.68f),
							0.72f to Color(0xFF090607),
							1f to Color(0xFF090607),
						),
					),
		)
		Box(
			modifier = Modifier
				.fillMaxSize()
				.background(
					Brush.horizontalGradient(
						listOf(
							Color(0xFF090607).copy(alpha = 0.92f),
							Color.Transparent,
							Color(0xFF090607).copy(alpha = 0.76f),
						),
					),
		)

		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(horizontal = 44.dp, vertical = 28.dp),
			verticalArrangement = Arrangement.spacedBy(22.dp),
		) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically,
			) {
				Text(
					text = "KINO",
					style = MaterialTheme.typography.titleLarge,
					fontWeight = FontWeight.Black,
					color = MaterialTheme.colorScheme.secondary,
				)
				Spacer(Modifier.weight(1f))
				Text(
					text = "BACK  ←",
					style = MaterialTheme.typography.labelLarge,
					color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f),
				)
			}

			Row(
				modifier = Modifier
					.fillMaxWidth()
					.weight(1f),
				horizontalArrangement = Arrangement.spacedBy(34.dp),
			) {
				MovieIdentity(
					movie = movie,
					modifier = Modifier.weight(0.43f),
				)

				TorrentResults(
					state = torrentState,
					onPlay = onPlay,
					onRetry = torrentController::retry,
					modifier = Modifier
						.weight(0.57f)
						.fillMaxHeight(),
				)
			}
		}
	}
}

@Composable
private fun MovieIdentity(
	movie: Movie,
	modifier: Modifier = Modifier,
) {
	Row(
		modifier = modifier.fillMaxHeight(),
		horizontalArrangement = Arrangement.spacedBy(22.dp),
		verticalAlignment = Alignment.Bottom,
	) {
		MovieDetailPoster(
			movie = movie,
			modifier = Modifier.width(156.dp),
		)

		Column(
			modifier = Modifier
				.weight(1f)
				.padding(bottom = 8.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp),
		) {
			Text(
				text = movie.title,
				style = MaterialTheme.typography.displaySmall,
				fontWeight = FontWeight.Black,
				maxLines = 2,
				overflow = TextOverflow.Ellipsis,
			)

			Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
				movie.releaseYear?.let { year ->
					Surface(
						shape = RoundedCornerShape(7.dp),
						color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
					) {
						Text(
							text = year.toString(),
							modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
							style = MaterialTheme.typography.labelLarge,
						)
					}
				}
				movie.voteAverage?.takeIf { it > 0.0 }?.let { rating ->
					Surface(
						shape = RoundedCornerShape(7.dp),
						color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.88f),
					) {
						Text(
							text = "★ %.1f".format(rating),
							modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
							style = MaterialTheme.typography.labelLarge,
							fontWeight = FontWeight.Bold,
						)
					}
				}
			}

			movie.overview?.takeIf { it.isNotBlank() }?.let { overview ->
				Text(
					text = overview,
					style = MaterialTheme.typography.bodyLarge,
					color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
					maxLines = 8,
					overflow = TextOverflow.Ellipsis,
				)
			}
		}
	}
}

@Composable
private fun MovieDetailPoster(
	movie: Movie,
	modifier: Modifier = Modifier,
) {
	val shape = RoundedCornerShape(12.dp)
	val posterUrl = tmdbPosterUrl(movie.posterPath)
	Box(
		modifier = modifier
			.aspectRatio(2f / 3f)
			.clip(shape)
			.background(MaterialTheme.colorScheme.surfaceVariant),
		contentAlignment = Alignment.Center,
	) {
		if (posterUrl != null) {
			AsyncImage(
				model = posterUrl,
				contentDescription = movie.title,
				contentScale = ContentScale.Crop,
				modifier = Modifier.fillMaxSize(),
			)
		} else {
			Text(
				text = movie.title.take(1).uppercase(),
				style = MaterialTheme.typography.displayLarge,
				fontWeight = FontWeight.Black,
				color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
			)
		}
	}
}

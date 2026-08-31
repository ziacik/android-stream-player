package sk.ziacik.androidstreamplayer.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import sk.ziacik.androidstreamplayer.catalog.Movie
import sk.ziacik.androidstreamplayer.catalog.tmdbPosterUrl

@Composable
fun MoviePosterCard(
	movie: Movie,
	onClick: () -> Unit,
	focusRequester: FocusRequester,
	onFocused: () -> Unit,
	upFocusRequester: FocusRequester? = null,
	downFocusRequester: FocusRequester? = null,
	leftFocusRequester: FocusRequester? = null,
	rightFocusRequester: FocusRequester? = null,
	testTag: String = "movie-${movie.tmdbId}",
	modifier: Modifier = Modifier,
) {
	var focused by remember { mutableStateOf(false) }
	val scale by animateFloatAsState(
		targetValue = if (focused) 1.05f else 1f,
		label = "movie-poster-scale",
	)
	val shape = RoundedCornerShape(12.dp)

	Card(
		onClick = onClick,
		modifier = modifier
			.focusProperties {
				if (upFocusRequester != null) up = upFocusRequester
				if (downFocusRequester != null) down = downFocusRequester
				if (leftFocusRequester != null) left = leftFocusRequester
				if (rightFocusRequester != null) right = rightFocusRequester
			}
			.testTag(testTag)
			.focusRequester(focusRequester)
			.onFocusChanged { focusState ->
				focused = focusState.isFocused
				if (focusState.isFocused) onFocused()
			}
			.graphicsLayer {
				scaleX = scale
				scaleY = scale
			},
		shape = shape,
		border = if (focused) {
			BorderStroke(3.dp, MaterialTheme.colorScheme.secondary)
		} else {
			BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))
		},
		colors = CardDefaults.cardColors(
			containerColor = MaterialTheme.colorScheme.surfaceVariant,
		),
	) {
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.aspectRatio(2f / 3f)
				.clip(shape),
		) {
			val posterUrl = tmdbPosterUrl(movie.posterPath)
			if (posterUrl != null) {
				AsyncImage(
					model = posterUrl,
					contentDescription = movie.title,
					contentScale = ContentScale.Crop,
					modifier = Modifier.fillMaxSize(),
				)
			} else {
				Box(
					modifier = Modifier
						.fillMaxSize()
						.background(
							Brush.verticalGradient(
								listOf(
									MaterialTheme.colorScheme.surfaceVariant,
									MaterialTheme.colorScheme.surface,
								),
							),
						),
					contentAlignment = Alignment.Center,
				) {
					Text(
						text = movie.title.take(1).uppercase(),
						style = MaterialTheme.typography.displayMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
						fontWeight = FontWeight.Black,
					)
				}
			}

			Box(
				modifier = Modifier
					.fillMaxSize()
					.background(
						Brush.verticalGradient(
							colorStops = arrayOf(
								0.48f to Color.Transparent,
								1f to Color.Black.copy(alpha = if (focused) 0.92f else 0.58f),
							),
						),
					),
			)

			if (focused) {
				Column(
					modifier = Modifier
						.align(Alignment.BottomStart)
						.padding(12.dp),
					verticalArrangement = Arrangement.spacedBy(2.dp),
				) {
					Text(
						text = movie.title,
						style = MaterialTheme.typography.titleSmall,
						fontWeight = FontWeight.SemiBold,
						color = Color.White,
						maxLines = 2,
						overflow = TextOverflow.Ellipsis,
					)
					movie.releaseYear?.let { year ->
						Text(
							text = year.toString(),
							style = MaterialTheme.typography.labelMedium,
							color = Color.White.copy(alpha = 0.72f),
						)
					}
				}
			}
		}
	}
}
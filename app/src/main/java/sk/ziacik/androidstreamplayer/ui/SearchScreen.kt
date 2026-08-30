package sk.ziacik.androidstreamplayer.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import java.util.Locale
import sk.ziacik.androidstreamplayer.search.SearchController
import sk.ziacik.androidstreamplayer.search.TorrentSearchResult

@UnstableApi
@Composable
fun SearchScreen(
    controller: SearchController,
    player: Player? = null,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()
    val streamStatus = state.streamStatus

    if (streamStatus == "Playing" && player != null) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    this.player = player
                    useController = true
                    keepScreenOn = true
                }
            },
            update = { view ->
                view.player = player
            },
            modifier = modifier
                .fillMaxSize()
                .testTag("player-view"),
        )
        return
    }

    val errorMessage = state.errorMessage
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        searchFocusRequester.requestFocus()
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                            MaterialTheme.colorScheme.background,
                            Color.Black,
                        ),
                    ),
                )
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            Color.Transparent,
                        ),
                        radius = 1100f,
                    ),
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 64.dp, vertical = 42.dp),
            ) {
                Text(
                    text = "ANDROID STREAM PLAYER",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.8.sp,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "What are we watching?",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 42.sp,
                    lineHeight = 46.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Search for a movie and choose the version that suits you.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(28.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = controller::setQuery,
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .focusRequester(searchFocusRequester)
                            .testTag("search-field"),
                        singleLine = true,
                        placeholder = {
                            Text(
                                text = "Search for a movie…",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        textStyle = MaterialTheme.typography.titleLarge,
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.secondary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                            cursorColor = MaterialTheme.colorScheme.secondary,
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { controller.search() }),
                    )
                    Spacer(Modifier.width(16.dp))
                    Button(
                        onClick = controller::search,
                        modifier = Modifier
                            .width(136.dp)
                            .height(64.dp)
                            .testTag("search-button"),
                        enabled = !state.isSearching,
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary,
                        ),
                    ) {
                        Text(
                            text = "Find",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Spacer(Modifier.height(26.dp))

                when {
                    streamStatus != null -> StreamStatus(
                        result = state.selectedResult,
                        status = streamStatus,
                    )

                    state.isSearching -> LoadingState()

                    errorMessage != null -> ErrorState(onRetry = controller::retry)

                    state.results.isEmpty() && state.query.isNotBlank() -> EmptyState()

                    state.results.isNotEmpty() -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Available versions",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "${state.results.size} found",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            items(
                                items = state.results,
                                key = TorrentSearchResult::id,
                            ) { result ->
                                TorrentResultCard(
                                    result = result,
                                    onClick = { controller.select(result) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TorrentResultCard(
    result: TorrentSearchResult,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.025f else 1f,
        animationSpec = tween(durationMillis = 140),
        label = "resultFocusScale",
    )

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { focused = it.isFocused }
            .testTag("result-${result.id}"),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            width = if (focused) 2.dp else 1.dp,
            color = if (focused) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
            },
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (focused) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayTitle(result),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    result.quality?.let { quality ->
                        QualityBadge(quality)
                    }
                    result.sizeBytes?.let { bytes ->
                        MetaText(formatBytes(bytes))
                    }
                    result.seeders?.let { seeders ->
                        MetaText(availabilityLabel(seeders))
                    }
                    if (result.id == "direct-magnet") {
                        MetaText("Direct link")
                    }
                }
            }
            Spacer(Modifier.width(20.dp))
            Text(
                text = "PLAY",
                style = MaterialTheme.typography.labelLarge,
                color = if (focused) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                },
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
            )
        }
    }
}

@Composable
private fun QualityBadge(quality: String) {
    Surface(
        shape = RoundedCornerShape(100.dp),
        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.38f),
        ),
    ) {
        Text(
            text = quality,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun MetaText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun LoadingState() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
        Column {
            Text(
                text = "Finding the best versions…",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "This can take a moment.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorState(onRetry: () -> Unit) {
    Column {
        Text(
            text = "Couldn’t search right now.",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Try again in a moment.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
            ),
        ) {
            Text("Try again", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EmptyState() {
    Column {
        Text(
            text = "Nothing here yet.",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Try another title or a more specific search.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StreamStatus(
    result: TorrentSearchResult?,
    status: String,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (status == "Preparing stream…") {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
            }
            Column {
                result?.let {
                    Text(
                        text = displayTitle(it),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Text(
                    text = friendlyStatus(status),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

private fun displayTitle(result: TorrentSearchResult): String =
    if (result.id == "direct-magnet") "Direct link" else result.title

private fun friendlyStatus(status: String): String = when (status) {
    "Preparing stream…" -> "Getting your movie ready…"
    "Streaming unavailable" -> "Playback unavailable"
    "Playback failed", "Stream failed" -> "Couldn’t start playback"
    else -> status
}

private fun availabilityLabel(seeders: Int): String = when {
    seeders >= 50 -> "Great availability"
    seeders >= 10 -> "Good availability"
    else -> "Limited availability"
}

private fun formatBytes(bytes: Long): String {
    val gib = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
    return String.format(Locale.US, "%.1f GB", gib)
}

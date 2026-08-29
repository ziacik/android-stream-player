package sk.ziacik.androidstreamplayer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 56.dp, vertical = 40.dp),
        ) {
            Text(
                text = "Android Stream Player",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
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
                        .focusRequester(searchFocusRequester)
                        .testTag("search-field"),
                    singleLine = true,
                    label = { Text("Movie or magnet") },
                    placeholder = { Text("Search torrents or paste magnet…") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { controller.search() }),
                )
                Spacer(Modifier.width(16.dp))
                Button(
                    onClick = controller::search,
                    modifier = Modifier.testTag("search-button"),
                    enabled = !state.isSearching,
                ) {
                    Text("Search", fontSize = 18.sp)
                }
            }

            Spacer(Modifier.height(28.dp))

            when {
                streamStatus != null -> StreamStatus(
                    result = state.selectedResult,
                    status = streamStatus,
                )

                state.isSearching -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator()
                    Text("Searching torrents…")
                }

                errorMessage != null -> Column {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = controller::retry) {
                        Text("Retry")
                    }
                }

                state.results.isEmpty() && state.query.isNotBlank() -> Text(
                    text = "Nothing found",
                    style = MaterialTheme.typography.titleMedium,
                )

                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
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

@Composable
private fun TorrentResultCard(
    result: TorrentSearchResult,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .testTag("result-${result.id}"),
        border = if (focused) {
            BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        colors = CardDefaults.cardColors(
            containerColor = if (focused) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
        ) {
            Text(
                text = result.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                result.quality?.let { Text(it) }
                result.sizeBytes?.let { Text(formatBytes(it)) }
                result.seeders?.let { Text("↑ $it seeders") }
                result.source?.let { Text(it) }
            }
        }
    }
}

@Composable
private fun StreamStatus(
    result: TorrentSearchResult?,
    status: String,
) {
    Column {
        result?.let {
            Text(
                text = it.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
        }
        Text(
            text = status,
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

private fun formatBytes(bytes: Long): String {
    val gib = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
    return String.format(Locale.US, "%.1f GB", gib)
}

package sk.ziacik.androidstreamplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val StreamPlayerColors = darkColorScheme()

@Composable
fun AndroidStreamPlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = StreamPlayerColors,
        content = content,
    )
}

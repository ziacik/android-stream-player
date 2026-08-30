package sk.ziacik.androidstreamplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CinemaBlack = Color(0xFF0B090A)
private val CinemaSurface = Color(0xFF171214)
private val CinemaSurfaceRaised = Color(0xFF24191D)
private val CinemaWine = Color(0xFF7A1F35)
private val CinemaWineDark = Color(0xFF3B111C)
private val Champagne = Color(0xFFE7C98D)
private val WarmCream = Color(0xFFF4ECDD)
private val MutedCream = Color(0xFFCDBFC3)
private val CinemaOutline = Color(0xFF6B555C)

private val StreamPlayerColors = darkColorScheme(
    primary = CinemaWine,
    onPrimary = Color.White,
    primaryContainer = CinemaWineDark,
    onPrimaryContainer = WarmCream,
    secondary = Champagne,
    onSecondary = Color(0xFF2C210B),
    background = CinemaBlack,
    onBackground = WarmCream,
    surface = CinemaSurface,
    onSurface = WarmCream,
    surfaceVariant = CinemaSurfaceRaised,
    onSurfaceVariant = MutedCream,
    outline = CinemaOutline,
)

@Composable
fun AndroidStreamPlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = StreamPlayerColors,
        content = content,
    )
}

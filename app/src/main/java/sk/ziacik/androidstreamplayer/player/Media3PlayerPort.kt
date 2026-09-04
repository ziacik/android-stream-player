package sk.ziacik.androidstreamplayer.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import java.io.File
import sk.ziacik.androidstreamplayer.subtitle.SubtitleTrack
import sk.ziacik.androidstreamplayer.torrent.TorrentSource

@UnstableApi
class Media3PlayerPort(context: Context) : PlayerPort {
	override val player: ExoPlayer = ExoPlayer.Builder(context).build()

	override fun prepare(source: TorrentSource) {
		prepare(source, null)
	}

	fun prepare(source: TorrentSource, subtitle: SubtitleTrack?) {
		val mediaItem = MediaItem.Builder()
			.setUri(source.uri)
			.apply {
				subtitle?.let { track ->
					setSubtitleConfigurations(
						listOf(
							MediaItem.SubtitleConfiguration.Builder(
								Uri.fromFile(File(track.path)),
							)
								.setMimeType(track.mimeType)
								.setLanguage(track.language)
								.setLabel(track.label)
								.setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
								.build(),
						),
					)
				}
			}
			.build()

		player.setMediaItem(mediaItem)
		player.prepare()
	}

	override fun play() {
		player.play()
	}

	override fun pause() {
		player.pause()
	}

	override fun release() {
		player.release()
	}
}

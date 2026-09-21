package paige.navic.statusbar

import androidx.compose.runtime.snapshotFlow
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import paige.navic.data.database.dao.SongDao
import paige.navic.data.database.mappers.toDomainModel
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.lyrics.LyricsLine
import paige.navic.domain.models.lyrics.LyricsResult
import paige.navic.domain.repositories.LyricsRepository

/** Drives line-level ticker updates from the same player used by the Media3 session. */
class StatusBarLyricsController(
	private val scope: CoroutineScope,
	private val songDao: SongDao,
	private val lyricsRepository: LyricsRepository,
	private val preferenceManager: PreferenceManager,
	private val notificationProvider: StatusBarLyricsNotificationProvider
) : Player.Listener {
	private var player: Player? = null
	private var lyricsJob: Job? = null
	private var tickerJob: Job? = null
	private var offsetJob: Job? = null
	private var songId: String? = null
	private var lines: List<LyricsLine> = emptyList()

	fun attach(player: Player) {
		this.player = player
		player.addListener(this)
		offsetJob = scope.launch {
			snapshotFlow { preferenceManager.statusBarLyricsOffsetMs }
				.distinctUntilChanged()
				.collect { syncTicker() }
		}
		loadLyrics(player.currentMediaItem)
	}

	fun release() {
		player?.removeListener(this)
		lyricsJob?.cancel()
		tickerJob?.cancel()
		offsetJob?.cancel()
		player = null
		songId = null
		lines = emptyList()
		notificationProvider.updateLyric(null, onlyUpdateTicker = false)
	}

	override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
		loadLyrics(mediaItem)
	}

	override fun onIsPlayingChanged(isPlaying: Boolean) {
		val player = player ?: return
		if (isPlaying) {
			syncTicker()
		} else if (!player.playWhenReady || player.playbackState == Player.STATE_ENDED) {
			tickerJob?.cancel()
			notificationProvider.updateLyric(null)
		} else {
			tickerJob?.cancel()
		}
	}

	override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
		syncTicker()
	}

	override fun onPositionDiscontinuity(
		oldPosition: Player.PositionInfo,
		newPosition: Player.PositionInfo,
		reason: Int
	) {
		syncTicker()
	}

	override fun onPlaybackStateChanged(playbackState: Int) {
		if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
			tickerJob?.cancel()
			notificationProvider.updateLyric(null)
		}
	}

	override fun onPlayerError(error: PlaybackException) {
		tickerJob?.cancel()
		notificationProvider.updateLyric(null)
	}

	private fun loadLyrics(mediaItem: MediaItem?) {
		lyricsJob?.cancel()
		tickerJob?.cancel()
		lines = emptyList()
		notificationProvider.updateLyric(null, onlyUpdateTicker = false)

		val id = mediaItem?.mediaId?.takeIf { it.isNotBlank() } ?: run {
			songId = null
			return
		}

		songId = id
		lyricsJob = scope.launch {
			val result: LyricsResult? = runCatching {
				val song = songDao.getSongById(id)?.toDomainModel()
				if (song == null) null else lyricsRepository.fetchLyrics(song)
			}.getOrNull()

			if (songId != id) return@launch
			lines = result?.lines
				.orEmpty()
				.filter { it.time != null && it.text.isNotBlank() }
				.sortedBy { it.time }
			syncTicker()
		}
	}

	private fun syncTicker() {
		tickerJob?.cancel()
		val player = player ?: return
		if (!player.isPlaying || lines.isEmpty()) return

		val position = (player.currentPosition + preferenceManager.statusBarLyricsOffsetMs)
			.coerceAtLeast(0L)
		val activeIndex = lines.indexOfLast {
				it.time!!.inWholeMilliseconds <= position
			}
		notificationProvider.updateLyric(lines.getOrNull(activeIndex)?.text)

		val nextTime = lines.getOrNull(activeIndex + 1)?.time?.inWholeMilliseconds ?: return
		val speed = player.playbackParameters.speed.coerceAtLeast(0.01f)
		val waitMs = ((nextTime - position).coerceAtLeast(1L) / speed)
			tickerJob = scope.launch {
				delay(waitMs.toLong().coerceAtLeast(1L))
				syncTicker()
			}
	}
}

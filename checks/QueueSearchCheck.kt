package paige.navic.ui.screen.queue

import paige.navic.domain.model.*
import kotlin.time.Duration

private fun song(id: String, title: String, artist: String? = null, album: String? = null) = DomainSong(
	id = id, title = title, artistName = artist, artistId = "artist", albumTitle = album,
	albumId = null, parentId = null, comment = null, trackNumber = null, discNumber = null,
	isrc = emptyList(), year = null, genre = null, genres = emptyList(), moods = emptyList(),
	duration = Duration.ZERO, bpm = null, contributors = emptyList(), userRating = null,
	averageRating = null, bitRate = null, bitDepth = null, sampleRate = null, audioChannelCount = null,
	replayGain = null, fileSize = 0, fileExtension = null, mimeType = null, filePath = null,
	starredAt = null, coverArtId = null, musicBrainzId = null,
	explicitStatus = DomainExplicitStatus.Unknown, artists = emptyList(), albumArtists = emptyList()
)

fun main() {
	val repeated = song("same", "夜曲", "周杰伦", "十一月的萧邦")
	val queue = listOf(song("other", "Other"), repeated, song("b", "Blue", "Singer"), repeated)
	check(filterQueue(queue, " 夜曲 ").map { it.index } == listOf(1, 3))
	check(filterQueue(queue, "周杰伦").map { it.index } == listOf(1, 3))
	check(filterQueue(queue, "萧邦").map { it.index } == listOf(1, 3))
	check(filterQueue(queue, "BLUE").single().index == 2)
	check(filterQueue(queue, "singer").single().index == 2)
	check(filterQueue(queue, " ").map { it.value } == queue)
	check(filterQueue(queue, "missing").isEmpty())
	check(filterQueue(emptyList(), "song").isEmpty())
	val withArtist = song("c", "Track").copy(artists = listOf(DomainSongArtist("a", "Guest")))
	check(filterQueue(listOf(withArtist), "guest").single().index == 0)
	// Removing the second matching occurrence must preserve the first occurrence.
	val secondMatch = filterQueue(queue, "夜曲")[1]
	val remaining = queue.toMutableList().apply { removeAt(secondMatch.index) }
	check(remaining.map { it.id } == listOf("other", "same", "b"))
	check(filterQueue(remaining.reversed(), "夜曲").single().index == 1)
	check(queue.size == 4)
	println("Queue search checks passed (matching, duplicate occurrences, removal and reordering).")
}

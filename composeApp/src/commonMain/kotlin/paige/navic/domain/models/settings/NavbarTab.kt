package paige.navic.domain.models.settings

import kotlinx.serialization.Serializable
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.title_albums
import navic.composeapp.generated.resources.title_artists
import navic.composeapp.generated.resources.title_genres
import navic.composeapp.generated.resources.title_library
import navic.composeapp.generated.resources.title_playlists
import navic.composeapp.generated.resources.title_radios
import navic.composeapp.generated.resources.title_search
import navic.composeapp.generated.resources.title_songs
import navic.composeapp.generated.resources.title_statistics
import org.jetbrains.compose.resources.StringResource

@Serializable
data class NavbarTab(
	val id: Id,
	val visible: Boolean
) {
	@Serializable
	enum class Id(val displayName: StringResource) {
		LIBRARY(Res.string.title_library),
		ALBUMS(Res.string.title_albums),
		PLAYLISTS(Res.string.title_playlists),
		ARTISTS(Res.string.title_artists),
		SEARCH(Res.string.title_search),
		GENRES(Res.string.title_genres),
		SONGS(Res.string.title_songs),
		RADIOS(Res.string.title_radios),
		STATISTICS(Res.string.title_statistics)
	}
}

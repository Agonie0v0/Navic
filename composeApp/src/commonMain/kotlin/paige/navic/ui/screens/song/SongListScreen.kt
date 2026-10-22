package paige.navic.ui.screens.song

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.collections.immutable.toImmutableList
import navic.composeapp.generated.resources.action_select_songs
import navic.composeapp.generated.resources.action_select_all_songs
import navic.composeapp.generated.resources.action_deselect_all_songs
import navic.composeapp.generated.resources.info_selected_songs
import navic.composeapp.generated.resources.action_cancel
import navic.composeapp.generated.resources.action_add_to_queue
import navic.composeapp.generated.resources.action_add_to_playlist
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.title_songs
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import paige.navic.di.LocalBottomBarScrollManager
import paige.navic.di.LocalPlatformContext
import paige.navic.di.isLandscape
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.DomainSongListType
import paige.navic.domain.models.settings.BottomBarVisibilityMode
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.components.dialogs.QueueDuplicateDialog
import paige.navic.ui.components.layouts.NestedTopBar
import paige.navic.ui.components.layouts.PullToRefreshBox
import paige.navic.ui.components.layouts.RootBottomBar
import paige.navic.ui.components.layouts.RootTopBar
import paige.navic.ui.core.UiState
import paige.navic.ui.navigation.PersistentViewModelStoreOwner
import paige.navic.ui.screens.playlist.dialogs.PlaylistUpdateDialog
import paige.navic.ui.screens.share.dialogs.ShareDialog
import paige.navic.ui.screens.song.components.SongListScreenSortButton
import paige.navic.ui.screens.song.components.songListScreenContent
import paige.navic.ui.screens.song.viewmodels.SongListViewModel
import paige.navic.ui.util.withoutTop
import paige.navic.ui.viewmodel.RootViewModel
import kotlin.time.Duration

@Composable
fun SongListScreen(
	nested: Boolean,
	listType: DomainSongListType
) {
	val platformContext = LocalPlatformContext.current
	val viewModel = koinViewModel<SongListViewModel>(
		key = listType.toString(),
		parameters = { parametersOf(listType) },
		viewModelStoreOwner = if (nested) {
			LocalViewModelStoreOwner.current!!
		} else {
			koinInject<PersistentViewModelStoreOwner>()
		}
	)
	val preferenceManager = koinInject<PreferenceManager>()
	val player = koinInject<MediaPlayerViewModel>()
	val songsState by viewModel.songsState.collectAsStateWithLifecycle()
	val selectedSong by viewModel.selectedSong.collectAsStateWithLifecycle()
	val selectedSorting by viewModel.selectedSorting.collectAsStateWithLifecycle()
	val selectedReversed by viewModel.selectedReversed.collectAsStateWithLifecycle()
	val selectedFilters by viewModel.selectedFilters.collectAsStateWithLifecycle()
	val starred by viewModel.starred.collectAsStateWithLifecycle()
	val selectedSongRating by viewModel.selectedSongRating.collectAsStateWithLifecycle()
	val allDownloads by viewModel.allDownloads.collectAsStateWithLifecycle()

	var shareId by remember { mutableStateOf<String?>(null) }
	var shareExpiry by remember { mutableStateOf<Duration?>(null) }
	var songToQueue by remember { mutableStateOf<DomainSong?>(null) }
	var selecting by rememberSaveable { mutableStateOf(false) }
	var selectedIds by rememberSaveable { mutableStateOf(listOf<String>()) }
	var playlistSongs by remember { mutableStateOf<List<DomainSong>?>(null) }
	var queueSongs by remember { mutableStateOf<List<DomainSong>?>(null) }
	val songs = songsState.data.orEmpty()
	val selectedIdSet = remember(selectedIds) { selectedIds.toSet() }
	val selectedSongs = remember(songs, selectedIdSet) {
		songs.filter { it.id in selectedIdSet }.distinctBy { it.id }
	}
	val finishSelection = { selecting = false; selectedIds = emptyList<String>() }
	LaunchedEffect(songsState) {
		if (songsState is UiState.Success) {
			val visibleIds = songs.map { it.id }.toSet()
			selectedIds = selectedIds.filter { it in visibleIds }
		}
	}
	NavigationBackHandler(
		state = rememberNavigationEventState(NavigationEventInfo.None),
		isBackEnabled = selecting && playlistSongs == null && queueSongs == null,
		onBackCompleted = finishSelection
	)
	val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

	val actions: @Composable RowScope.() -> Unit = {
		TextButton(
			onClick = { viewModel.clearSelection(); selecting = true },
			enabled = songs.isNotEmpty()
		) { Text(stringResource(Res.string.action_select_songs)) }
		SongListScreenSortButton(
			nested = nested,
			selectedSorting = selectedSorting,
			onSetSorting = { viewModel.setSorting(it) },
			selectedReversed = selectedReversed,
			onSetReversed = { viewModel.setReversed(it) },
			selectedFilters = selectedFilters,
			onToggleFilter = { viewModel.toggleFilter(it) }
		)
	}

	val rootViewModel = koinViewModel<RootViewModel>()
	val listState = rememberLazyListState()
	LaunchedEffect(Unit) {
		rootViewModel.events.collect { event ->
			if (event is RootViewModel.Event.ScrollToTop) {
				listState.animateScrollToItem(0)
			}
		}
	}

	Scaffold(
		topBar = {
			if (selecting) {
				Column {
					NestedTopBar(
						title = { Text(stringResource(Res.string.info_selected_songs, selectedSongs.size)) },
						navigationAction = {
							TextButton(onClick = finishSelection) { Text(stringResource(Res.string.action_cancel)) }
						},
						actions = {
							val allSelected = songs.isNotEmpty() && songs.all { it.id in selectedIdSet }
							TextButton(onClick = {
								selectedIds = if (allSelected) emptyList() else songs.map { it.id }.distinct()
							}) { Text(stringResource(if (allSelected) Res.string.action_deselect_all_songs else Res.string.action_select_all_songs)) }
						}
					)
					FlowRow(
						modifier = Modifier.padding(horizontal = 16.dp),
						horizontalArrangement = Arrangement.spacedBy(8.dp)
					) {
						FilledTonalButton(enabled = selectedSongs.isNotEmpty(), onClick = {
							val queuedIds = player.uiState.value.queue.map { it.id }.toSet()
							if (!preferenceManager.shushQueueDuplicateDialog && selectedSongs.any { it.id in queuedIds }) {
								queueSongs = selectedSongs
							} else {
								player.addToQueue(selectedSongs)
								finishSelection()
							}
						}) { Text(stringResource(Res.string.action_add_to_queue)) }
						FilledTonalButton(enabled = selectedSongs.isNotEmpty(), onClick = {
							playlistSongs = selectedSongs
						}) { Text(stringResource(Res.string.action_add_to_playlist)) }
					}
				}
			} else if (!nested) {
				RootTopBar(
					title = { Text(stringResource(Res.string.title_songs)) },
					scrollBehavior = scrollBehavior,
					actions = actions
				)
			} else {
				NestedTopBar(
					title = { Text(stringResource(Res.string.title_songs)) },
					actions = actions
				)
			}
		},
		bottomBar = {
			val scrollManager = LocalBottomBarScrollManager.current
			val preferVisible = preferenceManager.bottomBarVisibilityMode == BottomBarVisibilityMode.AllScreens
			if (!nested || (!platformContext.isLandscape() && preferVisible)) {
				RootBottomBar(scrolled = scrollManager.isTriggered)
			}
		}
	) { innerPadding ->
		PullToRefreshBox(
			modifier = Modifier
				.padding(top = innerPadding.calculateTopPadding())
				.background(MaterialTheme.colorScheme.surface),
			finished = songsState !is UiState.Loading,
			onRefresh = { viewModel.refreshSongs(true) },
			key = songsState
		) {
			LazyColumn(
				modifier = if (!nested)
					Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)
				else Modifier.fillMaxSize(),
				contentPadding = innerPadding.withoutTop(),
				verticalArrangement = if ((songsState as? UiState.Success)?.data?.isEmpty() == true)
					Arrangement.Center
				else Arrangement.spacedBy(12.dp),
				state = listState
			) {
				songListScreenContent(
					state = songsState,
					selectionMode = selecting,
					selectedIds = selectedIdSet,
					onToggleSelection = { song ->
						selectedIds = if (song.id in selectedIds) selectedIds - song.id else selectedIds + song.id
					},
					selectedSongIsStarred = starred,
					selectedSongRating = selectedSongRating,
					selectedSong = selectedSong,
					onUpdateSelection = { viewModel.selectSong(it) },
					onClearSelection = { viewModel.clearSelection() },
					onSetShareId = { newShareId ->
						shareId = newShareId
					},
					onSetStarred = { viewModel.starSong(it) },
					onPlayNext = { song ->
						if (player.uiState.value.queue.any { it.id == song.id } && !preferenceManager.shushQueueDuplicateDialog) {
							songToQueue = song
						} else {
							player.playNextSingle(song)
						}
					},
					onAddToQueue = { song ->
						if (player.uiState.value.queue.any { it.id == song.id } && !preferenceManager.shushQueueDuplicateDialog) {
							songToQueue = song
						} else {
							player.addToQueueSingle(song)
						}
					},
					onPlaySong = { song ->
						player.playNow(song)
					},
					onSetRating = { viewModel.rateSelectedSong(it) },
					onDownload = { viewModel.downloadSong(it) },
					allDownloads = allDownloads,
					onCancelDownload = { viewModel.cancelDownload(it.id) },
					onDeleteDownload = { viewModel.deleteDownload(it.id) }
				)
			}
		}
	}

	playlistSongs?.let { selected ->
		PlaylistUpdateDialog(
			songs = selected.toImmutableList(),
			onDismissRequest = { playlistSongs = null },
			onSuccess = finishSelection
		)
	}
	queueSongs?.let { selected ->
		QueueDuplicateDialog(
			onDismissRequest = { queueSongs = null },
			onConfirm = { player.addToQueue(selected); finishSelection() }
		)
	}

	ShareDialog(
		id = shareId,
		onIdClear = { shareId = null },
		expiry = shareExpiry,
		onExpiryChange = { shareExpiry = it }
	)

	if (songToQueue != null) {
		QueueDuplicateDialog(
			onDismissRequest = { songToQueue = null },
			onConfirm = {
				songToQueue?.let { player.addToQueueSingle(it) }
			}
		)
	}
}

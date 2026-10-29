/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.queue

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import paige.navic.R
import paige.navic.di.LocalNavStack
import paige.navic.domain.manager.runtime.PreferenceManager
import paige.navic.domain.model.DomainSong
import paige.navic.domain.model.settings.QueueInfoType
import paige.navic.playback.MediaPlayer
import paige.navic.ui.component.common.ContentUnavailable
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screen.queue.component.QueueScreenItem
import paige.navic.ui.screen.queue.viewmodel.QueueViewModel
import paige.navic.ui.theme.defaultFont
import paige.navic.ui.icons.Icons
import paige.navic.ui.icons.outlined.Close
import paige.navic.ui.icons.outlined.Search
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun QueueScreen() {
	val viewModel = koinViewModel<QueueViewModel>()
	val player = koinInject<MediaPlayer>()
	val state = player.uiState.collectAsStateWithLifecycle()

	val playerState by remember(player) {
		player.uiState
			.map { it.copy(progress = 0f) }
			.distinctUntilChanged()
	}.collectAsStateWithLifecycle(
		initialValue = state.value.copy(progress = 0f)
	)

	val isReachable by viewModel.isReachable.collectAsStateWithLifecycle()
	val downloadedSongs by viewModel.downloadedSongs.collectAsStateWithLifecycle()
	val queue = remember(playerState.queue, playerState.shuffleOrder) {
		playerState.activeQueue
	}
	val currentIndex = remember(
		playerState.queue,
		playerState.shuffleOrder,
		playerState.currentIndex
	) {
		playerState.activeIndex
	}
	val haptic = LocalHapticFeedback.current
	val preferenceManager = koinInject<PreferenceManager>()
	val backStack = LocalNavStack.current
	var query by rememberSaveable { mutableStateOf("") }

	Column(modifier = Modifier.fillMaxSize()) {
		if (queue.isNotEmpty()) {
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 24.dp, vertical = 8.dp),
				horizontalArrangement = Arrangement.SpaceBetween
			) {
				Row(
					modifier = Modifier
						.height(36.dp)
						.clickable {
							preferenceManager.queueInfoType =
								when (preferenceManager.queueInfoType) {
									QueueInfoType.Full -> QueueInfoType.Remaining
									QueueInfoType.Remaining -> QueueInfoType.Full
								}
						}, verticalAlignment = Alignment.CenterVertically
				) {
					QueueInfoText(
						player = player,
						queue = queue,
						currentIndex = currentIndex
					)
				}
				FilledTonalButton(
					onClick = {
						haptic.performHapticFeedback(HapticFeedbackType.LongPress)
						player.clearQueue()
						backStack.remove(Screen.Queue)
						backStack.remove(Screen.NowPlaying)
					},
					colors = ButtonDefaults.filledTonalButtonColors(),
					contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
					modifier = Modifier.height(36.dp)
				) {
					Text(stringResource(R.string.action_clear_queue))
				}
			}
		}

		OutlinedTextField(
			value = query,
			onValueChange = { query = it },
			modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
			label = { Text(stringResource(R.string.label_search_queue)) },
			leadingIcon = { Icon(Icons.Outlined.Search, null) },
			trailingIcon = {
				if (query.isNotEmpty()) {
					IconButton(onClick = { query = "" }) {
						Icon(Icons.Outlined.Close, stringResource(R.string.action_clear_queue_search))
					}
				}
			},
			singleLine = true
		)
		AndroidView(
			modifier = Modifier
				.padding(horizontal = 12.dp)
				.fillMaxSize()
				.navigationBarsPadding()
				.clipToBounds()
				.systemGestureExclusion {
					androidx.compose.ui.geometry.Rect(
						left = 5f,
						top = 0f,
						right = 5f,
						bottom = 0f
					)
				},
			factory = { context ->
				QueueRecyclerView(context).apply {
					layoutManager = LinearLayoutManager(context)
					clipToOutline = true
					addItemDecoration(object : RecyclerView.ItemDecoration() {
						override fun getItemOffsets(
							outRect: Rect,
							view: android.view.View,
							parent: RecyclerView,
							state: RecyclerView.State
						) {
							if (parent.getChildAdapterPosition(view) > 0) {
								outRect.top = (3 * resources.displayMetrics.density).toInt()
							}
						}
					})
					val adapter = QueueRecyclerAdapter()
					this.adapter = adapter
					val touchHelper = ItemTouchHelper(adapter.touchCallback)
					touchHelper.attachToRecyclerView(this)
					adapter.attachTouchHelper(touchHelper)
				}
			},
			update = { recyclerView ->
				val adapter = recyclerView.adapter as QueueRecyclerAdapter
				adapter.update(
					queue = queue,
					query = query,
					currentIndex = currentIndex,
					isPaused = playerState.isPaused,
					isOnline = isReachable,
					downloadedSongs = downloadedSongs,
					onPlay = { index ->
						if (currentIndex != index) {
							player.playAt(index)
						}
					},
					onRemove = { index ->
						haptic.performHapticFeedback(HapticFeedbackType.LongPress)
						player.removeFromQueue(index)
					},
					onPlayNext = { index -> queue.getOrNull(index)?.let { player.playNextSingle(it) } },
					onMove = { from, to ->
						haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
						player.moveQueueItem(from, to)
					})
				if (adapter.shouldScrollToCurrentIndex) {
					recyclerView.scrollToPosition(adapter.currentVisibleIndex)
					adapter.shouldScrollToCurrentIndex = false
				}
			})
	}
}

@Composable
private fun QueueInfoText(
	player: MediaPlayer,
	queue: List<DomainSong>,
	currentIndex: Int
) {
	val preferenceManager = koinInject<PreferenceManager>()
	val queueInfoType = preferenceManager.queueInfoType
	val state = player.uiState.collectAsStateWithLifecycle()

	val songCountText = when (queueInfoType) {
		QueueInfoType.Full -> pluralStringResource(R.plurals.count_songs, queue.size, queue.size)
		QueueInfoType.Remaining -> pluralStringResource(
			R.plurals.count_remaining_songs,
			queue.size - currentIndex,
			queue.size - currentIndex
		)
	}
	val totalMillis = remember(queue) {
		queue.sumOf { it.duration.inWholeMilliseconds } // ms because precision
	}
	// duration of all songs that are before the current index
	val pastMillis = remember(queue, currentIndex) {
		queue.take(currentIndex.coerceIn(0, queue.size))
			.sumOf { it.duration.inWholeMilliseconds }
	}
	val currentTrackMillis = remember(queue, currentIndex) {
		queue.getOrNull(currentIndex)?.duration?.inWholeMilliseconds ?: 0L
	}

	val elapsedMillis by remember(player, currentTrackMillis) {
		player.uiState.map {
			(it.progress * currentTrackMillis).toLong() / 1000 * 1000
		}.distinctUntilChanged()
	}.collectAsStateWithLifecycle(
		initialValue =
			(state.value.progress * currentTrackMillis).toLong() / 1000 * 1000
	)
	val durationText = remember(
		queueInfoType,
		totalMillis,
		pastMillis,
		elapsedMillis
	) {
		var remainingMillis = totalMillis

		if (queueInfoType == QueueInfoType.Remaining) {
			// deduct previous durations plus current elapsed duration
			remainingMillis -= pastMillis + elapsedMillis

			// wow, that's confusing as shit for some reason
		}

		// then we format manually because Duration.toString() doesn't let u disable decimals
		val totalSeconds = remainingMillis.milliseconds.inWholeSeconds
		val hours = totalSeconds / 3600
		val minutes = (totalSeconds % 3600) / 60
		val seconds = totalSeconds % 60

		buildString {
			if (hours > 0) append("${hours}h ")
			if (minutes > 0 || hours > 0) append("${minutes}m ")
			append("${seconds}s")
		}
	}
	val formattedDurationText = when (queueInfoType) {
		QueueInfoType.Full -> durationText
		QueueInfoType.Remaining -> stringResource(R.string.info_duration_left, durationText)
	}

	Text(
		text = "$songCountText • $formattedDurationText",
		style = MaterialTheme.typography.titleMedium,
		fontWeight = FontWeight.SemiBold,
		fontFamily = defaultFont(round = 100f),
		color = MaterialTheme.colorScheme.onSurfaceVariant,
		textAlign = TextAlign.Center
	)
}

private class QueueRecyclerView(context: Context) : RecyclerView(context) {
	override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
		when (event.actionMasked) {
			MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> parent?.requestDisallowInterceptTouchEvent(
				true
			)

			MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(
				false
			)
		}
		return super.onInterceptTouchEvent(event)
	}
}

private class QueueRecyclerAdapter : RecyclerView.Adapter<QueueRecyclerAdapter.ViewHolder>() {
	private var queue = emptyList<DomainSong>()
	private var requestedQueue = emptyList<DomainSong>()
	private var query = ""
	private var visibleIndices = emptyList<Int>()
	val currentVisibleIndex get() = visibleIndices.indexOf(currentIndex).coerceAtLeast(0)
	private var currentIndex = 0
	private var isPaused = true
	private var isOnline = true
	private var downloadedSongs = emptyMap<String, String>()
	private var onPlay: (Int) -> Unit = {}
	private var onRemove: (Int) -> Unit = {}
	private var onPlayNext: (Int) -> Unit = {}
	private var onMove: (Int, Int) -> Unit = { _, _ -> }
	private var draggingPosition = RecyclerView.NO_POSITION
	private var touchHelper: ItemTouchHelper? = null
	var shouldScrollToCurrentIndex = false

	val touchCallback = object : ItemTouchHelper.SimpleCallback(
		ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
	) {
		override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int =
			if (query.isNotBlank() || queue.isEmpty()) 0 else super.getMovementFlags(recyclerView, viewHolder)

		override fun onMove(
			recyclerView: RecyclerView,
			viewHolder: RecyclerView.ViewHolder,
			target: RecyclerView.ViewHolder
		): Boolean {
			val from = viewHolder.bindingAdapterPosition
			val to = target.bindingAdapterPosition
			if (query.isNotBlank() || from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) {
				return false
			}

			val mutableQueue = queue.toMutableList()
			mutableQueue.add(to, mutableQueue.removeAt(from))
			queue = mutableQueue
			requestedQueue = mutableQueue

			notifyItemMoved(from, to)
			onMove(from, to)
			return true
		}

		override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
			super.onSelectedChanged(viewHolder, actionState)
			draggingPosition =
				if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
					viewHolder.bindingAdapterPosition
				} else {
					RecyclerView.NO_POSITION
				}
			viewHolder?.itemView?.elevation =
				if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) 8f else 0f
		}

		override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
			super.clearView(recyclerView, viewHolder)
			draggingPosition = RecyclerView.NO_POSITION
			viewHolder.itemView.elevation = 0f
		}

		override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
	}

	fun attachTouchHelper(touchHelper: ItemTouchHelper) {
		this.touchHelper = touchHelper
	}

	@SuppressLint("NotifyDataSetChanged")
	fun update(
		queue: List<DomainSong>,
		query: String,
		currentIndex: Int,
		isPaused: Boolean,
		isOnline: Boolean,
		downloadedSongs: Map<String, String>,
		onPlay: (Int) -> Unit,
		onRemove: (Int) -> Unit,
		onPlayNext: (Int) -> Unit,
		onMove: (Int, Int) -> Unit
	) {
		val queueChanged = requestedQueue != queue
		val queryChanged = this.query != query
		val itemStateChanged =
			this.currentIndex != currentIndex || this.isPaused != isPaused || this.isOnline != isOnline || this.downloadedSongs != downloadedSongs
		shouldScrollToCurrentIndex = this.currentIndex != currentIndex || queryChanged
		requestedQueue = queue
		this.query = query
		this.currentIndex = currentIndex
		this.isPaused = isPaused
		this.isOnline = isOnline
		this.downloadedSongs = downloadedSongs
		this.onPlay = onPlay
		this.onRemove = onRemove
		this.onPlayNext = onPlayNext
		this.onMove = onMove
		if (queueChanged || queryChanged) {
			this.queue = queue
			visibleIndices = filterQueue(queue, query).map { it.index }
			notifyDataSetChanged()
		} else if (itemStateChanged) {
			notifyItemRangeChanged(0, itemCount)
		}
	}

	override fun getItemCount() = visibleIndices.size.coerceAtLeast(1)

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
		val composeView = ComposeView(parent.context)
		return ViewHolder(composeView)
	}

	override fun onBindViewHolder(holder: ViewHolder, position: Int) {
		if (visibleIndices.isEmpty()) {
			holder.view.setContent {
				ContentUnavailable(label = stringResource(if (query.isBlank()) R.string.info_no_queue else R.string.info_no_queue_matches))
			}
			return
		}
		val queueIndex = visibleIndices[position]
		val song = queue[queueIndex]
		holder.view.setContent {
			QueueScreenItem(
				index = position,
				count = visibleIndices.size,
				song = song,
				isPlaying = currentIndex == queueIndex && !isPaused,
				isSelected = currentIndex == queueIndex,
				isDragging = draggingPosition == position,
				onDrag = { touchHelper?.startDrag(holder) },
				canReorder = query.isBlank(),
				onPlayNext = if (query.isBlank()) null else {
					{ visibleIndices.getOrNull(holder.bindingAdapterPosition)?.let(onPlayNext) }
				},
				onClick = {
					holder.bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }
						?.let { visibleIndices.getOrNull(it) }
						?.let(onPlay)
				},
				onRemove = {
					holder.bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }
						?.let { visibleIndices.getOrNull(it) }
						?.let(onRemove)
				},
				isOffline = !isOnline,
				isDownloaded = downloadedSongs.containsKey(song.id)
			)
		}
	}

	class ViewHolder(val view: ComposeView) : RecyclerView.ViewHolder(view)
}

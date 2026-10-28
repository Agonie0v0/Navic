/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.playback.exoplayer.impl.cache

import android.net.Uri
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.extractor.metadata.icy.IcyHeaders
import dev.zt64.subsonic.api.model.MediaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import paige.navic.domain.manager.playback.StreamCacheManager
import paige.navic.domain.manager.runtime.PreferenceManager
import paige.navic.domain.manager.session.DownloadManager
import paige.navic.domain.manager.session.SessionManager
import paige.navic.domain.manager.session.connectivity.ConnectivityManager
import paige.navic.playback.PlaybackUtils
import paige.navic.util.Logger
import paige.navic.util.network.NavicDnsResolver
import paige.navic.util.network.createOkHttpClient
import paige.navic.util.network.getProxyUrlOrNull
import java.io.File

@OptIn(UnstableApi::class)
class NavicDataSource(
	private val downloadManager: DownloadManager,
	private val sessionManager: SessionManager,
	private val streamCacheManager: StreamCacheManager,
	private val preferenceManager: PreferenceManager,
	private val connectivityManager: ConnectivityManager,
	dnsResolver: NavicDnsResolver,
) : BaseDataSource(true) {
	val ttlLruCache
		get() = sessionManager.ttlLruCache

	class NavicDataSourceFactory(
		private val downloadManager: DownloadManager,
		private val sessionManager: SessionManager,
		private val streamCacheManager: StreamCacheManager,
		private val preferenceManager: PreferenceManager,
		private val connectivityManager: ConnectivityManager,
		private val dnsResolver: NavicDnsResolver,
	) : DataSource.Factory {
		override fun createDataSource(): DataSource {
			return NavicDataSource(
				downloadManager,
				sessionManager,
				streamCacheManager,
				preferenceManager,
				connectivityManager,
				dnsResolver
			)
		}
	}

	companion object {
		const val NAVIC_SCHEME = "navic"
		const val NAVIC_SONG_HOST = "song"
		const val NAVIC_COVER_HOST = "cover"
		private const val TAG = "NavicDataSource"

		fun String.buildSongUri(): Uri {
			return "$NAVIC_SCHEME://$NAVIC_SONG_HOST/$this".toUri()
		}

		fun String.buildCoverUri(): Uri {
			return "$NAVIC_SCHEME://$NAVIC_COVER_HOST/$this".toUri()
		}
	}

	// TODO: figure out why KtorDataSource causes #650
	private val upstreamFactory = OkHttpDataSource.Factory(
		createOkHttpClient(
			proxyUrl = preferenceManager.proxyUrl.getProxyUrlOrNull(),
			trustAllCerts = preferenceManager.dangerousSslNoopEnabled,
			dnsResolver,
			customHeaders = preferenceManager.customHeadersMap()
		)
	).setDefaultRequestProperties(
		mapOf(
			IcyHeaders.REQUEST_HEADER_ENABLE_METADATA_NAME to IcyHeaders.REQUEST_HEADER_ENABLE_METADATA_VALUE
		)
	)

	private val cacheDataSourceFactory by lazy {
		CacheDataSource.Factory()
			.setCache(streamCacheManager.cache)
			.setUpstreamDataSourceFactory(upstreamFactory)
			.setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
	}

	private var wrappedDataSource: DataSource? = null
	private var currentUri: Uri? = null
	private var songId: String? = null
	private var isTransferStarted = false

	override fun open(dataSpec: DataSpec): Long {
		transferInitializing(dataSpec)
		val uri = dataSpec.uri
		currentUri = uri

		songId = if (uri.scheme == NAVIC_SCHEME && uri.host == NAVIC_SONG_HOST) {
			uri.lastPathSegment
		} else {
			null
		}

		var cacheKey: String? = null

		val targetUri = songId?.let {
			runBlocking {
				withContext(Dispatchers.IO) {
					val localPath = downloadManager.getDownloadedFilePath(it)
					if (localPath != null) {
						Uri.fromFile(File(localPath))
					} else {
						// shortcut: the disk cache is keyed by song only, so quality changes still play already-cached audio
						cacheKey = streamCacheManager.keyFor(it)
						resolveStreamUrl(it)
					}
				}
			}
		} ?: uri

		val resolvedDataSpecBuilder = dataSpec.buildUpon().setUri(targetUri)
		if (cacheKey != null) {
			resolvedDataSpecBuilder
				.setKey(cacheKey)
				.setFlags(dataSpec.flags and DataSpec.FLAG_DONT_CACHE_IF_LENGTH_UNKNOWN.inv())
		}
		val resolvedDataSpec = resolvedDataSpecBuilder.build()

		val source = when {
			targetUri.scheme == "file" || targetUri.scheme == "content" -> FileDataSource()
			cacheKey != null -> cacheDataSourceFactory.createDataSource()
			else -> upstreamFactory.createDataSource()
		}

		wrappedDataSource = source as DataSource?
		val length = source.open(resolvedDataSpec)
		transferStarted(dataSpec)
		isTransferStarted = true
		return length
	}

	override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
		val bytesRead = wrappedDataSource?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT
		if (bytesRead > 0) {
			bytesTransferred(bytesRead)
		}
		return bytesRead
	}

	override fun getUri(): Uri? {
		return wrappedDataSource?.uri ?: currentUri
	}

	override fun getResponseHeaders(): Map<String, List<String>> {
		return wrappedDataSource?.responseHeaders ?: emptyMap()
	}

	override fun close() {
		wrappedDataSource?.close()
		wrappedDataSource = null
		currentUri = null
		songId = null
		if (isTransferStarted) {
			isTransferStarted = false
			transferEnded()
		}
	}

	private fun currentQuality(): Pair<Int, String?> {
		if (preferenceManager.isAdvancedTranscodingActive) {
			return preferenceManager.customMaxBitrate to
				preferenceManager.customFormat.trim().takeIf { it.isNotEmpty() }
		}

		val quality = if (connectivityManager.isMetered.value) {
			preferenceManager.streamingQualityCellular
		} else {
			preferenceManager.streamingQualityWifi
		}
		return quality.bitrate to quality.container
	}

	private fun String.toStreamUri(): Uri {
		return toUri().buildUpon()
			.appendQueryParameter("estimateContentLength", "true")
			.build()
	}

	private suspend fun resolveStreamUrl(id: String): Uri {
		val (bitrateKbps, format) = currentQuality()
		val cacheKey = "$id|$bitrateKbps|${format.orEmpty()}"
		val cachedStreamUrl = ttlLruCache.get(cacheKey)

		if (cachedStreamUrl != null) {
			return cachedStreamUrl.toUri()
		}

		val defaultStreamUrl = sessionManager.api.getStreamUrl(id, 0)

		if (!sessionManager.supportsTranscoding.value) {
			val streamUrl = defaultStreamUrl.toStreamUri().toString()
			ttlLruCache.put(cacheKey, streamUrl)
			return streamUrl.toUri()
		}

		return try {
			val transcodeDecision = withContext(Dispatchers.IO) {
				sessionManager.api.getTranscodeDecision(
					id,
					MediaType.SONG,
					PlaybackUtils.buildClientInfo(bitrateKbps, format)
				)
			}
			val isTranscode = !transcodeDecision.canDirectPlay &&
				transcodeDecision.canTranscode &&
				transcodeDecision.transcodeParams != null
			val streamUrl = if (isTranscode) {
				sessionManager.api.getTranscodeStreamUrl(
					id, MediaType.SONG, 0,
					transcodeDecision.transcodeParams!!
				).toStreamUri().toString()
			} else {
				defaultStreamUrl.toStreamUri().toString()
			}

			ttlLruCache.put(cacheKey, streamUrl)

			streamUrl.toUri()
		} catch (e: Exception) {
			if (e is CancellationException) throw e
			Logger.w(TAG, "transcode decision failed for $id, using direct stream", e)
			val streamUrl = defaultStreamUrl.toStreamUri().toString()
			ttlLruCache.put(cacheKey, streamUrl)
			streamUrl.toUri()
		}
	}
}

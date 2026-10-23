package com.fares.flux.core

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/** A subtitle or caption track of the stream playing. */
data class TextTrack(val group: TrackGroup, val index: Int, val label: String)

/** A film or an episode, for the full-screen player. */
data class Video(val id: String, val url: String, val title: String, val subtitle: String? = null, val resumeAt: Double? = null)

/** The one player of the app. Live TV plays on a screen of its own, drawn
 *  by Flux (the zapping banner, the channel list); films and episodes play
 *  with Media3's controls. */
@OptIn(UnstableApi::class)
class TVPlayer(context: Context, private val state: AppState) {
    var channel by mutableStateOf<ChannelGroup?>(null); private set
    var variant by mutableStateOf<ChannelVariant?>(null); private set
    /** The channels zapped through: the list the channel was picked from. */
    var lineup by mutableStateOf<List<ChannelGroup>>(emptyList()); private set
    var error by mutableStateOf<String?>(null); private set
    var isPaused by mutableStateOf(false); private set
    var isBuffering by mutableStateOf(false); private set
    /** A frame of the stream playing is on screen: false from tuning until
     *  the first one, while the picture is black. */
    var hasPicture by mutableStateOf(false); private set
    var showsLive by mutableStateOf(false); private set
    /** Sound and no picture: a radio. */
    var isAudioOnly by mutableStateOf(false); private set
    var video by mutableStateOf<Video?>(null); private set
    /** The stream's subtitles and captions, and the one shown (null: off). */
    var textTracks by mutableStateOf<List<TextTrack>>(emptyList()); private set
    var textTrack by mutableStateOf<TextTrack?>(null); private set
    /** Subtitles on, the stream had none in the device's language: its first is picked, once. */
    private var pickedFirstText = false

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(
            // Timeouts on the client's own: 10 s, longer than a stream that should play needs.
            androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(Net.callFactory).setUserAgent(XtreamClient.USER_AGENT)
        ))
        .build()

    private var client: XtreamClient? = null
    private val tried = mutableSetOf<Int>()
    private var pendingZap: Job? = null
    private var progressJob: Job? = null

    init {
        applySubtitles()
        player.addListener(object : Player.Listener {
            override fun onRenderedFirstFrame() { hasPicture = true }

            override fun onTracksChanged(tracks: Tracks) {
                val list = ArrayList<TextTrack>()
                var shown: TextTrack? = null
                for (group in tracks.groups) {
                    if (group.type != C.TRACK_TYPE_TEXT) continue
                    for (i in 0 until group.length) {
                        if (!group.isTrackSupported(i)) continue
                        val track = TextTrack(group.mediaTrackGroup, i, label(group.getTrackFormat(i), list.size + 1))
                        list += track
                        if (group.isTrackSelected(i)) shown = track
                    }
                }
                textTracks = list
                textTrack = shown
                isAudioOnly = tracks.containsType(C.TRACK_TYPE_AUDIO) && !tracks.containsType(C.TRACK_TYPE_VIDEO)
                if (state.settings.subtitles && shown == null && list.isNotEmpty() && !pickedFirstText) {
                    pickedFirstText = true
                    selectText(list[0])
                }
            }

            // Media3's own controls (films) turn them on or off too: kept for every stream.
            override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) {
                state.settings.changeSubtitles(C.TRACK_TYPE_TEXT !in parameters.disabledTrackTypes)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
            }

            override fun onPlayerError(error: PlaybackException) {
                if (channel != null && showsLive) streamFailed()
                else if (video != null) this@TVPlayer.error = tr("Lecture impossible (${error.errorCodeName}).", "Can’t play this (${error.errorCodeName}).")
            }
        })
    }

    // Live

    /** Opens the live screen on a channel; `lineup` is what up and down zap through. */
    fun watch(group: ChannelGroup, lineup: List<ChannelGroup>, client: XtreamClient, variant: ChannelVariant? = null) {
        if (video != null) closeVideo()
        this.client = client
        var list = lineup.ifEmpty { this.lineup }
        if (list.none { it.id == group.id }) list = listOf(group) + list
        this.lineup = list
        pendingZap?.cancel()
        showsLive = true
        start(group, variant)
    }

    /** What up and down zap through from now on: a category picked under the picture. */
    fun changeLineup(list: List<ChannelGroup>) { if (list.isNotEmpty()) lineup = list }

    /** The next channel down the list (`step` 1) or up it (-1), shown at once
     *  and tuned once the presses stop. */
    fun zap(step: Int) {
        val current = channel ?: return
        val i = lineup.indexOfFirst { it.id == current.id }
        if (i < 0 || lineup.size < 2) return
        val target = lineup[(i + step + lineup.size) % lineup.size]
        channel = target
        error = null
        pendingZap?.cancel()
        pendingZap = state.scope.launch {
            delay(550)
            start(target)
        }
    }

    private fun start(group: ChannelGroup, picked: ChannelVariant? = null) {
        // A stream picked by hand is the one for the channel from now on.
        if (picked != null) state.library.rememberQuality(picked.quality.label, group)
        val chosen = picked ?: group.preferred(state.settings.quality, state.library.qualityChoices[group.id])
        state.library.played(group)
        tried.clear()
        tune(group, chosen)
    }

    private fun tune(group: ChannelGroup, variant: ChannelVariant) {
        val client = client ?: return
        hasPicture = false
        tried += variant.id
        channel = group
        this.variant = variant
        error = null
        // The free channels' addresses are looked up first: one that can't
        // be found fails over like a stream that won't play.
        resolving?.cancel()
        resolving = state.scope.launch {
            val url = client.liveURL(variant.channel)
            if (this@TVPlayer.variant?.id != variant.id) return@launch
            if (url == null) { streamFailed(); return@launch }
            // A playlist's live streams can be MPEG-TS, which ExoPlayer reads from the address.
            val hls = variant.channel.url?.let { it.contains(".m3u8", ignoreCase = true) || !it.startsWith("http") } ?: true
            load(url, group.name, null, null, live = true, hls = hls)
        }
    }

    private var resolving: Job? = null

    /** A stream that won't play hands over to the next one of the channel. */
    private fun streamFailed() {
        val group = channel ?: return
        val delay = variant?.delay ?: return
        val next = group.variants(delay).firstOrNull { it.id !in tried }
        if (next != null) tune(group, next) else error = tr("Cette chaîne ne répond pas pour le moment.", "This channel isn’t responding right now.")
    }

    fun retry() { channel?.let { start(it) } }

    /** Another of the channel's streams, picked by hand: kept for it from now on. */
    fun pick(variant: ChannelVariant) { channel?.let { start(it, variant) } }

    fun togglePause() {
        if (isPaused) player.play() else player.pause()
        isPaused = !isPaused
    }

    fun closeLive() {
        pendingZap?.cancel()
        showsLive = false
        stop()
        channel = null
        variant = null
        error = null
    }

    // Films and episodes

    fun play(video: Video) {
        if (showsLive) closeLive()
        error = null
        load(video.url, video.title, video.subtitle, video.resumeAt, live = false)
        this.video = video
        progressJob = state.scope.launch {
            while (true) { delay(5_000); saveProgress() }
        }
    }

    fun play(movie: Movie, client: XtreamClient, fromStart: Boolean = false) {
        val key = Library.key(MediaKind.MOVIE, movie.id)
        play(Video(key, client.movieURL(movie), DisplayName.clean(movie.name), null, if (fromStart) null else state.library.resumePosition(key)))
    }

    fun play(episode: Episode, series: Series, client: XtreamClient, fromStart: Boolean = false) {
        val key = Library.key(MediaKind.SERIES, episode.id)
        play(Video(key, client.episodeURL(episode), episode.title, "${DisplayName.clean(series.name)} · S${episode.season} E${episode.number}",
            if (fromStart) null else state.library.resumePosition(key)))
    }

    fun closeVideo() {
        saveProgress()
        stop()
        video = null
        error = null
    }

    // Subtitles

    /** Off: no text track at all, even one the stream flags as default or
     *  forced. On: the device's language first. */
    private fun applySubtitles() {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !state.settings.subtitles)
            .setPreferredTextLanguage(Locale.getDefault().language)
            .setSelectUndeterminedTextLanguage(true)
            .build()
    }

    /** Shows this track, or none; remembered for every stream after. */
    fun selectText(track: TextTrack?) {
        val builder = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT)
        if (track == null) builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        else builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).setOverrideForType(TrackSelectionOverride(track.group, track.index))
        state.settings.changeSubtitles(track != null)
        player.trackSelectionParameters = builder.build()
    }

    /** "Français", "English (CC)", or "Piste 2" when the stream says nothing. */
    private fun label(format: Format, n: Int): String {
        val language = format.language?.takeIf { it.isNotBlank() && it != "und" }
            ?.let { Locale.forLanguageTag(it).getDisplayLanguage(Locale.getDefault()).replaceFirstChar { c -> c.titlecase(Locale.getDefault()) } }
        val cc = format.sampleMimeType == MimeTypes.APPLICATION_CEA608 || format.sampleMimeType == MimeTypes.APPLICATION_CEA708
        val name = format.label?.nonEmpty ?: language ?: tr("Piste $n", "Track $n")
        return if (cc && "CC" !in name) "$name (CC)" else name
    }

    // Playback

    private fun load(url: String, title: String, subtitle: String?, resumeAt: Double?, live: Boolean, hls: Boolean = live) {
        progressJob?.cancel()
        // Each stream starts from the setting, not from the last one's track.
        pickedFirstText = false
        applySubtitles()
        val item = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setSubtitle(subtitle).build())
            .apply { if (hls) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .build()
        player.setMediaItem(item, ((resumeAt ?: 0.0) * 1000).toLong())
        player.prepare()
        isPaused = false
        player.playWhenReady = true
    }

    private fun stop() {
        resolving?.cancel()
        hasPicture = false
        progressJob?.cancel()
        progressJob = null
        player.stop()
        player.clearMediaItems()
        isPaused = false
    }

    private fun saveProgress() {
        val video = video ?: return
        val duration = player.duration
        if (duration <= 0) return
        state.library.saveProgress(video.id, player.currentPosition / 1000.0, duration / 1000.0)
    }

    fun release() = player.release()
}

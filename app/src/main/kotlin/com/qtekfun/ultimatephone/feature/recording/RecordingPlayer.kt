package com.qtekfun.ultimatephone.feature.recording

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlayerState(
    val uri: Uri,
    val playing: Boolean = false,
    val positionMs: Int = 0,
    val durationMs: Int = 0,
    val failed: Boolean = false,
    val ready: Boolean = false
)

/** One [MediaPlayer] at a time, with play, pause and seek. Main thread only. */
class RecordingPlayer(private val context: Context) {
    private var player: MediaPlayer? = null
    private val mutable = MutableStateFlow<PlayerState?>(null)
    val state: StateFlow<PlayerState?> = mutable.asStateFlow()

    /** Plays [uri], or pauses/resumes it if it is the one already loaded. */
    fun toggle(uri: Uri) {
        val active = player
        if (active != null && mutable.value.isUsableFor(uri)) {
            if (active.isPlaying) active.pause() else active.start()
            sync()
            return
        }
        load(uri)
    }

    private fun PlayerState?.isUsableFor(uri: Uri) = this != null && this.uri == uri && ready && !failed

    // A provider or codec can fail in many ways; all of them mean "cannot play".
    private fun load(uri: Uri) {
        release()
        mutable.value = PlayerState(uri)
        val created = MediaPlayer()
        player = created
        val prepared = runCatching {
            created.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            )
            created.setDataSource(context, uri)
            created.setOnPreparedListener {
                it.start()
                mutable.value = PlayerState(uri, playing = true, durationMs = it.duration, ready = true)
            }
            created.setOnCompletionListener {
                mutable.value = PlayerState(uri, playing = false, positionMs = 0, durationMs = it.duration, ready = true)
            }
            created.setOnErrorListener { _, _, _ ->
                mutable.value = PlayerState(uri, failed = true)
                true
            }
            created.prepareAsync()
        }.isSuccess
        if (!prepared) mutable.value = PlayerState(uri, failed = true)
    }

    fun seekTo(positionMs: Int) {
        val active = player ?: return
        if (mutable.value?.ready != true) return
        active.seekTo(positionMs)
        sync()
    }

    /** Copies the player's position and play state into [state]. Called while playing, a few times a second. */
    fun sync() {
        val active = player ?: return
        val current = mutable.value ?: return
        if (!current.ready) return
        // The player can be released by the system between the check and the read.
        val read = runCatching { active.isPlaying to active.currentPosition }.getOrNull()
        mutable.value = if (read != null) current.copy(playing = read.first, positionMs = read.second) else current.copy(failed = true, playing = false)
    }

    fun release() {
        player?.let { runCatching { it.release() } }
        player = null
        mutable.value = null
    }
}

package com.dergruenkohl.newsillyimagedownloader.downloader

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean

class DownloadController {
    private val _paused = AtomicBoolean(false)
    private val _stopped = AtomicBoolean(false)

    val isPaused: Boolean get() = _paused.get()
    val isStopped: Boolean get() = _stopped.get()

    fun pause() { _paused.set(true) }
    fun resume() { _paused.set(false) }

    fun stop() { _stopped.set(true); _paused.set(false) }

    /**
     * Call periodically from downloader to suspend while paused and throw if stopped.
     */
    suspend fun checkPausedOrStopped() {
        while (_paused.get()) {
            if (_stopped.get()) throw CancellationException("Download stopped")
            //delay(200)
        }
        if (_stopped.get()) throw CancellationException("Download stopped")
    }
}

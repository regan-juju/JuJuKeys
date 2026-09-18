package com.reganbarua.jujukeys.voice

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * A tiny in-process event bus so [MicPermissionActivity] can tell the (still-running)
 * IME service what happened after a runtime permission dialog. An IME service cannot
 * call requestPermissions itself (no Activity), so this is the bridge back.
 */
object MicPermissionBus {
    private val _events = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    fun postResult(granted: Boolean) {
        _events.tryEmit(granted)
    }
}

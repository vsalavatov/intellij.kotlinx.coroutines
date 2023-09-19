/*
 * Copyright 2016-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines.debug.internal

import kotlin.coroutines.*

public actual val DISPATCHED_COROUTINES_TRACKING_ENABLED: Boolean = false

internal actual object DispatchedCoroutinesDebugProbesImpl {
    actual fun <T> probeCoroutineCreated(completion: Continuation<T>): Continuation<T> = completion
    actual fun probeCoroutineSuspended(frame: Continuation<*>) {}
    actual fun probeCoroutineResumed(frame: Continuation<*>) {}

    actual fun dumpCoroutinesInfo(): List<DebugCoroutineInfo> = emptyList()
}

internal actual class DebugCoroutineInfo
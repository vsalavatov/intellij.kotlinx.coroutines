/*
 * Copyright 2016-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines.debug.internal

import kotlinx.coroutines.*

public actual val DISPATCHED_COROUTINES_TRACKING_ENABLED: Boolean
    get() = false

internal actual object DispatchedCoroutinesDebugProbesImpl {
    actual fun <T> probeCoroutineCreated(completion: kotlin.coroutines.Continuation<T>): kotlin.coroutines.Continuation<T> = completion
    actual fun probeCoroutineSuspended(frame: kotlin.coroutines.Continuation<*>) {}
    actual fun probeCoroutineResumed(frame: kotlin.coroutines.Continuation<*>) {}

    actual fun dumpCoroutinesInfo(): List<DebugCoroutineInfo> = throw Throwable("kek")
}

internal actual class DebugCoroutineInfo
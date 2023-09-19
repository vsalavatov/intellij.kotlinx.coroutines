/*
 * Copyright 2016-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines.debug.internal

import kotlin.coroutines.*

public expect val DISPATCHED_COROUTINES_TRACKING_ENABLED: Boolean

@PublishedApi
internal expect class DebugCoroutineInfo

@PublishedApi
internal expect object DispatchedCoroutinesDebugProbesImpl {
    fun <T> probeCoroutineCreated(completion: Continuation<T>): Continuation<T>
    fun probeCoroutineSuspended(frame: Continuation<*>)
    fun probeCoroutineResumed(frame: Continuation<*>)

    fun dumpCoroutinesInfo(): List<DebugCoroutineInfo>
}
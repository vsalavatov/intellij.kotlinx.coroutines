/*
 * Copyright 2016-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("UNUSED", "INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package kotlinx.coroutines.debug

import kotlinx.coroutines.debug.internal.*

public object DispatchedCoroutinesDebugProbes {
    public fun dumpCoroutinesInfo(): List<CoroutineInfo> =
        DispatchedCoroutinesDebugProbesImpl.dumpCoroutinesInfo().map { CoroutineInfo(it) }
}
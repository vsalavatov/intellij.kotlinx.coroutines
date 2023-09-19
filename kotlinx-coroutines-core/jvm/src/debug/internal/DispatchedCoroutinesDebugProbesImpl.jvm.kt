/*
 * Copyright 2016-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines.debug.internal

import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.coroutines.jvm.internal.CoroutineStackFrame

@JvmField
public actual val DISPATCHED_COROUTINES_TRACKING_ENABLED: Boolean =
    System.getProperty("kotlinx.coroutines.dispatched-coroutines-tracking", "false").toBoolean()

/**
 * mostly copy-pasted from DebugProbesImpl
 * callerInfoCache is disabled as we are not tracking running->running transitions
 */
@PublishedApi
internal actual object DispatchedCoroutinesDebugProbesImpl {
    private val capturedCoroutinesMap = ConcurrentWeakMap<DispatchedCoroutineOwner<*>, Boolean>()
    private val capturedCoroutines: Set<DispatchedCoroutineOwner<*>> get() = capturedCoroutinesMap.keys

    // To sort coroutines by creation order, used as a unique id
    private val sequenceNumber = atomic(0L)

    // TODO probably it is not needed here? we are not tracking resume->resume transitions
//    private val callerInfoCache = ConcurrentWeakMap<CoroutineStackFrame, DebugCoroutineInfoImpl>(weakRefQueue = true)

    // if DispatchedCoroutinesDebugProbes is touched, then DISPATCHED_COROUTINES_TRACKING_ENABLED == true,
    // no need to support installation counter
//    private val weakRefCleanerThread: AtomicReference<Thread?> = AtomicReference(
//        thread(isDaemon = true, name = "Coroutines Debugger Cleaner") {
//            callerInfoCache.runWeakRefQueueCleaningLoopUntilInterrupted()
//        }
//    )

//    fun stopWeakRefCleanerThread() {
//        val thread = weakRefCleanerThread.getAndSet(null) ?: return
//        thread.interrupt()
//        thread.join()
//    }

    private fun Continuation<*>.dispatchedOwner(): DispatchedCoroutineOwner<*>? =
        (this as? CoroutineStackFrame)?.dispatchedOwner()

    private tailrec fun CoroutineStackFrame.dispatchedOwner(): DispatchedCoroutineOwner<*>? =
        if (this is DispatchedCoroutineOwner<*>) this else callerFrame?.dispatchedOwner()

//    private tailrec fun CoroutineStackFrame.realCaller(): CoroutineStackFrame? {
//        val caller = callerFrame ?: return null
//        return if (caller.getStackTraceElement() != null) caller else caller.realCaller()
//    }

    actual fun <T> probeCoroutineCreated(completion: Continuation<T>): Continuation<T> {
        val owner = completion.dispatchedOwner()
        if (owner != null) return completion
        val frame = if (DebugProbesImpl.enableCreationStackTraces) {
//            sanitizeStackTrace(Exception()).toStackTraceFrame()
            null
        } else {
            null
        }
        return createDispatchedOwner(completion, frame)
    }

    private fun <T> createDispatchedOwner(completion: Continuation<T>, frame: StackTraceFrame?): Continuation<T> {
        val info = DebugCoroutineInfoImpl(completion.context, frame, sequenceNumber.incrementAndGet())
        val owner = DispatchedCoroutineOwner(completion, info)
        capturedCoroutinesMap[owner] = true
        return owner
    }

//    private fun updateRunningState(frame: CoroutineStackFrame, state: String) {
//        if (!DebugProbesImpl.isInstalled) return
//        // Lookup coroutine info in cache or by traversing stack frame
//        val info: DebugCoroutineInfoImpl
//        val cached = callerInfoCache.remove(frame)
//        val shouldBeMatchedWithProbeSuspended: Boolean
//        if (cached != null) {
//            info = cached
//            shouldBeMatchedWithProbeSuspended = false
//        } else {
//            info = frame.dispatchedOwner()?.info ?: return
//            shouldBeMatchedWithProbeSuspended = true
//            val realCaller = info.lastObservedFrame?.realCaller()
//            if (realCaller != null) callerInfoCache.remove(realCaller)
//        }
//        info.updateState(state, frame as Continuation<*>, shouldBeMatchedWithProbeSuspended)
//        // Do not cache it for proxy-classes such as ScopeCoroutines
//        val caller = frame.realCaller() ?: return
//        callerInfoCache[caller] = info
//    }

    private fun updateState(frame: Continuation<*>, state: String) {
//        if (state == RUNNING) {
//            val stackFrame = frame as? CoroutineStackFrame ?: return
//            updateRunningState(stackFrame, state)
//            return
//        }

        // Find ArtificialStackFrame of the coroutine
        val owner = frame.dispatchedOwner() ?: return
        updateState(owner, frame, state)
    }

    private fun updateState(owner: DispatchedCoroutineOwner<*>, frame: Continuation<*>, state: String) {
        owner.info.updateState(state, frame, true)
    }

    actual fun probeCoroutineSuspended(frame: Continuation<*>) = updateState(frame, SUSPENDED)
    actual fun probeCoroutineResumed(frame: Continuation<*>) = updateState(frame, RUNNING)

    fun probeCoroutineCompleted(owner: DispatchedCoroutineOwner<*>) {
        capturedCoroutinesMap.remove(owner)
//        val caller = owner.info.lastObservedFrame?.realCaller() ?: return
//        callerInfoCache.remove(caller)
    }

    actual fun dumpCoroutinesInfo(): List<DebugCoroutineInfo> =
        dumpCoroutinesInfoImpl { owner, context -> DebugCoroutineInfo(owner.info, context) }

    private fun DispatchedCoroutineOwner<*>.isFinished(): Boolean {
        // Guarded by lock
        val job = info.context?.get(Job) ?: return false
        if (!job.isCompleted) return false
        capturedCoroutinesMap.remove(this) // Clean it up by the way
        return true
    }

    private inline fun <R : Any> dumpCoroutinesInfoImpl(crossinline create: (DispatchedCoroutineOwner<*>, CoroutineContext) -> R): List<R> {
        return capturedCoroutines
            .asSequence()
            // Stable ordering of coroutines by their sequence number
            .sortedBy { it.info.sequenceNumber }
            // Leave in the dump only the coroutines that were not collected while we were dumping them
            .mapNotNull { owner ->
                // Fuse map and filter into one operation to save an inline
                if (owner.isFinished()) null
                else owner.info.context?.let { context -> create(owner, context) }
            }.toList()
    }
}

/** basically copy-pasted CoroutineOwner with substituted debug probes */
internal class DispatchedCoroutineOwner<T> internal constructor(
    @JvmField internal val delegate: Continuation<T>,
    @JvmField public val info: DebugCoroutineInfoImpl
) : Continuation<T> by delegate, CoroutineStackFrame {
    private val frame get() = info.creationStackBottom

    override val callerFrame: CoroutineStackFrame?
        get() = frame?.callerFrame

    override fun getStackTraceElement(): StackTraceElement? = frame?.getStackTraceElement()

    override fun resumeWith(result: Result<T>) {
        DispatchedCoroutinesDebugProbesImpl.probeCoroutineCompleted(this)
        delegate.resumeWith(result)
    }

    override fun toString(): String = delegate.toString()
}
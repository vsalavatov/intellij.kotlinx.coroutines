package kotlinx.coroutines.internal.intellij

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.internal.softLimitedParallelism as softLimitedParallelismImpl
import kotlinx.coroutines.internal.SoftLimitedDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.*

internal val currentContextThreadLocal: ThreadLocal<CoroutineContext?> = ThreadLocal.withInitial { null }

/**
 * [IntellijCoroutines] exposes the API added as part of IntelliJ patches.
 * Prefer to use the corresponding API from the IntelliJ Platform instead of accessing this object directly.
 */
@InternalCoroutinesApi
public object IntellijCoroutines {
    /**
     * IntelliJ Platform would like to introspect coroutine contexts outside the coroutine framework.
     * This function is a non-suspend version of [coroutineContext].
     *
     * @return null if current thread is not used by coroutine dispatchers,
     * or [coroutineContext] otherwise.
     */
    public fun currentThreadCoroutineContext(): CoroutineContext? {
        return currentContextThreadLocal.get()
    }

    /**
     * Constructs a [SoftLimitedDispatcher] from the specified [CoroutineDispatcher].
     * [SoftLimitedDispatcher] behaves as [LimitedDispatcher][kotlinx.coroutines.internal.LimitedDispatcher] but allows
     * temporarily exceeding the parallelism limit in case [parallelism compensation][kotlinx.coroutines.scheduling.withCompensatedParallelism]
     * was requested (e.g., by [kotlinx.coroutines.runBlocking]).
     *
     * This extension can only be used on instances of [Dispatchers.Default], [Dispatchers.IO] and also on what this extension
     * has returned. Throws [UnsupportedOperationException] if [this] does not support parallelism compensation mechanism.
     */
    public fun CoroutineDispatcher.softLimitedParallelism(parallelism: Int): CoroutineDispatcher = softLimitedParallelismImpl(parallelism)
}
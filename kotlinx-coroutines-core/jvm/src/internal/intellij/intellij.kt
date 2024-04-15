/**
 * A special file that contains IntelliJ-related functions
 */
package kotlinx.coroutines.internal.intellij

import kotlin.coroutines.*



internal val currentContextThreadLocal : ThreadLocal<CoroutineContext?> = ThreadLocal.withInitial { null }

// We use an `object` here to reduce visibility of the newly added public API.
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
}

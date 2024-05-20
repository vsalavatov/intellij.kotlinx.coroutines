package kotlinx.coroutines.scheduling

import kotlinx.coroutines.InternalCoroutinesApi

/**
 * Introduced as part of IntelliJ patches.
 *
 * Increases the parallelism limit of the coroutine dispatcher associated with the current thread for the duration of [body] execution.
 * After the [body] completes, the effective parallelism may stay higher than the associated limit, but it is said
 * that eventually it will adjust to meet it.
 */
@InternalCoroutinesApi
public fun <T> withCompensatedParallelism(body: () -> T): T {
    // CoroutineScheduler.Worker implements ParallelismCompensation
    val worker = Thread.currentThread() as? ParallelismCompensation
        ?: return body()
    worker.increaseParallelismAndLimit()
    try {
        return body()
    } finally {
        worker.decreaseParallelismLimit()
    }
}
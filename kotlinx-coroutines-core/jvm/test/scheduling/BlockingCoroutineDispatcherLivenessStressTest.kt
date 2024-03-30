package kotlinx.coroutines.scheduling

import kotlinx.coroutines.testing.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Test
import java.lang.management.ManagementFactory
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import kotlin.test.*

/**
 * Test that ensures implementation correctness of [LimitingDispatcher] and
 * designed to stress its particular implementation details.
 */
class BlockingCoroutineDispatcherLivenessStressTest : SchedulerTestBase() {
    private val concurrentWorkers = AtomicInteger(0)

    @Before
    fun setUp() {
        // In case of starvation test will hang
        idleWorkerKeepAliveNs = Long.MAX_VALUE
    }

    @Test
    fun testAddPollRace() = runBlocking {
        val limitingDispatcher = blockingDispatcher(1)
        val iterations = 25_000 * stressTestMultiplier
        // Stress test for specific case (race #2 from LimitingDispatcher). Shouldn't hang.
        for (i in 1..iterations) {
            val tasks = (1..2).map {
                async(limitingDispatcher) {
                    try {
                        val currentlyExecuting = concurrentWorkers.incrementAndGet()
                        assertEquals(1, currentlyExecuting)
                    } finally {
                        concurrentWorkers.decrementAndGet()
                    }
                }
            }
            tasks.forEach { it.await() }
        }
    }

    @Test
    fun testPingPongThreadsCount() = runBlocking {
        corePoolSize = CORES_COUNT
        val iterations = 100_000 * stressTestMultiplier
        val completed = AtomicInteger(0)
        for (i in 1..iterations) {
            val tasks = (1..2).map {
                async(dispatcher) {
                    // Useless work
                    concurrentWorkers.incrementAndGet()
                    concurrentWorkers.decrementAndGet()
                    completed.incrementAndGet()
                }
            }
            tasks.forEach { it.await() }
        }
        assertEquals(2 * iterations, completed.get())
    }
}


class BlockingCoroutineDispatcherTestCorePoolSize1 : SchedulerTestBase() {
    init {
        corePoolSize = 1
    }

    @Test
    fun testLivenessOfDefaultDispatcher(): Unit = runBlocking { // (Dispatchers.Default)
        val oldRunBlockings = ArrayDeque<Job>()
        var maxOldRunBlockings = 0
        var busyWaits = 0
        repeat(20_000 * stressTestMultiplier) {
//        repeat(1000 * stressTestMultiplier) {
            if (oldRunBlockings.size > maxOldRunBlockings) {
                maxOldRunBlockings = oldRunBlockings.size
            }
            if (it % 1000 == 0
                || true
                ) {
                System.err.println("======== $it, " +
                    "old runBlocking count=${oldRunBlockings.size}, " +
                    "max old runBlocking count=${maxOldRunBlockings}, " +
                    "busy waits count=$busyWaits")
            }
            val barrier = CyclicBarrier(2)
            val barrier2 = CompletableDeferred<Unit>()
            val blocking = launch(dispatcher) {
                barrier.await()
                runBlocking {
                    yield()
                    barrier2.await()
                    yield()
                }
            }
            oldRunBlockings.addLast(blocking)
            val task = async(dispatcher) { yield(); 42 }
            barrier.await()
            task.join()
            barrier2.complete(Unit)

            oldRunBlockings.removeIf(Job::isCompleted)
            while (oldRunBlockings.size > 5) {
                busyWaits++
                oldRunBlockings.removeIf(Job::isCompleted)
            }
        }
    }
}

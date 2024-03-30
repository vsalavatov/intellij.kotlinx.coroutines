package kotlinx.coroutines.internal

import kotlinx.coroutines.scheduling.*
import kotlinx.coroutines.scheduling.CoroutineScheduler
import java.util.concurrent.locks.*

internal actual class PermitTransfer {
    private val status = PermitTransferStatus()

    public actual fun releaseFun(releasePermit: () -> Unit): () -> Unit {
        val blockedThread = Thread.currentThread()
        return {
            if (status.complete()) {
                try {
                    releasePermit()
                } finally {
//                    (blockedThread as? CoroutineScheduler.Worker)?.scheduler?.log("permit transfer releaseFun: unparking ${blockedThread.name}")
                    LockSupport.unpark(blockedThread)
                }
            }
        }
    }

    public actual fun acquire(tryAllocatePermit: () -> Boolean, deallocatePermit: () -> Unit) {
        while (true) {
            if (status.check()) {
                return
            }
            if (tryAllocatePermit()) {
                if (!status.complete()) { // race: transfer was completed first by releaseFun
                    deallocatePermit()
                }
                return
            }
//            (Thread.currentThread() as? CoroutineScheduler.Worker)?.scheduler?.log("permit transfer acquire: parking")
//            LockSupport.park(this)
            LockSupport.park()
        }
    }
}
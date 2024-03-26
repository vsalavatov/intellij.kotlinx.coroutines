# Included IntelliJ-related patches

## Introspection of `coroutineContext`

### Description:
To support the threading framework of IntelliJ,
we need to cooperate with kotlin coroutines better than they allow it by default.

One example of this cooperation is an ability to get coroutine context from non-suspending places.
Essentially, we put the coroutine context into a thread local variable on every coroutine resumption,
which allows us to read necessary information without a significant change in semantics.
This change has a mild performance penalty, namely, modification of a thread local variable.
However, coroutines themselves use thread local states via `ThreadLocalContextElement`, which hints that
one more thread local variable would not harm.

### API:

We provide a single method `kotlinx.coroutines.internal.intellij.IntellijCoroutines.currentThreadCoroutineContext`.
The invariant is that the result of this method is always equal to `coroutineContext` in suspending environment,
and it does not change during the non-suspending execution within the same thread.

## `runBlocking` without Dispatcher starvation 

[IJPL-721](https://youtrack.jetbrains.com/issue/IJPL-721), [#3983](https://github.com/Kotlin/kotlinx.coroutines/issues/3983)

### Description:
`runBlocking` with its default semantics may cause dispatcher starvation if it is called on a worker thread. 
For example, if `runBlocking` happens to block all `Dispatchers.Default` workers, it may lead to a deadlock in the application:
there may be tasks in the CPU queue that `runBlocking`s await, but there are none CPU workers available to run them.

This patch changes the behavior of `runBlocking` so that it always releases associated computation permits before it parks,
and reacquires them after unpark. It works for every `CoroutineDispatcher` that is built using library primitives:
plain `Dispatcher.*` objects or `.limitedParallelism` limited dispatchers that are on top of them.

This change in behavior comes with a cost. Permit reacquiring mechanism _may_ need an additional thread park/unpark.
Worker threads that release their computational permits always let go of the local task queue, which means less benefit
from locality, higher contention and transactional costs at the very least. 

This patch doesn't change the fact that `runBlocking` should still be used carefully.
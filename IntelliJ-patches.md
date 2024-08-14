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


## Parallelism compensation for `CoroutineDispatcher`s

If `runBlocking` happens to be invoked on a thread from `CoroutineDispatcher`, it may cause a thread starvation problem
(Kotlin#3983). This happens because `runBlocking` does not release an associated computational permit while it parks the
thread. To fix this, a parallelism compensation mechanism is introduced. Some `CoroutineDispatcher`s (such as 
`Dispatchers.Default`, `Dispatchers.IO` and others) support `ParallelismCompensation`, meaning that these dispatchers
can be notified that they should increase parallelism and parallelism limit, or they should decrease it. It is important that these
are only requests and dispatchers are in full control on how and when they need to adjust the effective parallelism.
It also means that the instantaneous parallelism may exceed the current allowed parallelism limit for the given dispatcher.

`runBlockingWithParallelismCompensation` (further abbreviated as `rBWPC`) is introduced as a counterpart of `runBlocking` 
with the following behavioral change. When `rBWPC` decides to park a `CoroutineDispatcher` thread, it first increases the allowed parallelism
limit of the `CoroutineDispatcher`. After the thread unparks, `rBWPC` notifies the dispatcher that the parallelism limit should be lowered back.
A separate function is introduced because parallelism compensation is now always a desirable behavior.

It is easy to see that this behavior cannot be general for `CoroutineDispatcher`s, at least because it breaks the contract
of `LimitedDispatcher` (one that can be acquired via `.limitedParallelism`). It means that parallelism compensation
cannot work for `LimitedDispatcher`, so `runBlockingWithParallelismCompensation` can still cause starvation issues there, but it seems rather 
expected.

Parallelism compensation support is internal and is implemented for `Dispatchers.Default` and `Dispatchers.IO`.
To acquire an analogue of `limitedParallelism` dispatcher which supports parallelism compensation, use 
`IntellijCoroutines.softLimitedParallelism`. Be advised that not every `.limitedParallelism` call can be substituted
with `.softLimitedParallelism`, e.g., `.limitedParallelism(1)` may be used as a synchronization manager and in this case
exceeding the parallelism limit would eliminate this (likely expected) side effect.

### API
- `runBlockingWithParallelismCompensation` - an analogue of `runBlocking` which also compensates parallelism of the
  associated coroutine dispatcher when it decides to park the thread
- `CoroutineDispatcher.softLimitedParallelism` – an analogue of `.limitedParallelism` which supports
  parallelism compensation

## Asynchronous stack traces for flows in the IDEA debugger

The agent needs three entities to establish a proper asynchronous stack traces connection:
- a capture point — method that indicates the stack trace that precedes the current stack trace;
- an insertion point — method within the current stack trace;
- a key — an object that is present in both points and is unique enough to bridge two stack traces properly.

The key for MutableStateFlow is the element itself. For MutableSharedFlow, the element is wrapped into a unique object to prevent bridging mistakes when two equal elements are emitted from different places.

Also, operators `debounce` and `sample` are supported. As they use an intermediary flow inside, the transferred values are wrapped and unwrapped the same way as in MutableSharedFlow.
It means there may be all-library async stack traces between a stack trace containing `emit` and a stack trace containing `collect`.

### API

No new public methods are introduced; some logic was extracted to separate methods so that the debugger agent could instrument it properly:

- `kotlinx.coroutines.flow.internal.FlowValueWrapperInternal` -- wrapper class used to create a unique object for the debugger agent
- `kotlinx.coroutines.flow.internal.FlowValueWrapperInternalKt.wrapInternal` -- returns passed argument by default; the agent instruments it to call `wrapInternalDebuggerCapture` instead
- `kotlinx.coroutines.flow.internal.FlowValueWrapperInternalKt.wrapInternalDebuggerCapture` -- wraps passed arguments into a `FlowValueWrapperInternal`; only used after transformation
- `kotlinx.coroutines.flow.internal.FlowValueWrapperInternalKt.unwrapInternal` -- returns passed argument by default; the agent instruments it to call `unwrapInternalDebuggerCapture` instead
- `kotlinx.coroutines.flow.internal.FlowValueWrapperInternalKt.unwrapInternalDebuggerCapture` -- unwraps passed argument so it returns the original value; only used after transformation

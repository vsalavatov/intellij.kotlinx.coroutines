# Included IntelliJ-related patches

## Parallelism compensation for `CoroutineDispatcher`s

If `runBlocking` happens to be invoked on a thread from `CoroutineDispatcher`, it may cause a thread starvation problem
(Kotlin#3983). This happens because `runBlocking` does not release an associated computational permit while it parks the
thread. To fix this, a parallelism compensation mechanism is introduced. When `runBlocking` decides to park a 
`CoroutineDispatcher` thread, it first increases the allowed parallelism limit of the `CoroutineDispatcher`. After
the thread unparks, `runBlocking` notifies the dispatcher that the parallelism limit should be lowered back. It is 
important that the effective parallelism may temporarily exceed the current allowed parallelism limit. The
`CoroutineDispatcher`'s worker take care of adjusting the effective parallelism if it needs to be decreased.

It is easy to see that this behavior cannot be general for `CoroutineDispatcher`s, at least because it breaks the contract
of `LimitedDispatcher` (one that can be acquired via `.limitedParallelism`). It means that parallelism compensation
cannot work for `LimitedDispatcher`, so `runBlocking` can still cause starvation issues there, but it seems rather 
expected.

Parallelism compensation support is internal and is implemented for `Dispatchers.Default` and `Dispatchers.IO`.
To acquire an analogue of `limitedParallelism` dispatcher which supports parallelism compensation, use 
`IntellijCoroutines.softLimitedParallelism`. Be advised that not every `.limitedParallelism` call can be substituted
with `.softLimitedParallelism`, e.g., `.limitedParallelism(1)` may be used as a synchronization manager and in this case
exceeding the parallelism limit would eliminate this (likely expected) side effect.

### API
- `CoroutineDispatcher.softLimitedParallelism` – an analogue of `.limitedParallelism` which supports
  parallelism compensation

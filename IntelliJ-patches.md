# Included IntelliJ-related patches

## Asynchronous stack traces for flows in the IDEA debugger

The agent needs three entities to establish a proper asynchronous stack traces connection:
- a capture point — method that indicates the stack trace that precedes the current stack trace;
- an insertion point — method within the current stack trace;
- a key — an object that is present in both points and is unique enough to bridge two stack traces properly.

The key for MutableStateFlow is the element itself. For MutableSharedFlow, the element is wrapped into a unique object to prevent bridging mistakes when two equal elements are emitted from different places.

Most of the operators applicable to flows (such as `map`, `scan`, `debounce`, `buffer`) are supported. As some of them use an intermediary flow inside, the transferred values are wrapped and unwrapped the same way as in MutableSharedFlow.
It means there may be all-library async stack traces between a stack trace containing `emit` and a stack trace containing `collect`.

There is no support yet for many operators that heavily use `Channel`s inside (such as `timeout`), as well as for functions that convert flows to channels and vice versa (such as `produceIn`).

### API

Some logic related to instrumentation was extracted to separate methods so that the debugger agent could instrument it properly:

- `kotlinx.coroutines.flow.internal.FlowValueWrapperInternal` -- wrapper class used to create a unique object for the debugger agent
- `kotlinx.coroutines.flow.internal.FlowValueWrapperInternalKt.wrapInternal` -- returns passed argument by default; the agent instruments it to call `wrapInternalDebuggerCapture` instead
- `kotlinx.coroutines.flow.internal.FlowValueWrapperInternalKt.wrapInternalDebuggerCaptureX` -- wraps passed arguments into a `FlowValueWrapperInternal`; only used after transformation.
  `X` may mean `Strict` or `Lenient`. Both methods handle double-wrapping, which is always an error that is hard to investigate if it arises naturally.
    - `Strict` throws an exception, thus allowing fail-fast strategy
    - `Lenient` returns its argument without wrapping it again
  Debugger agent decides which version to use based on IDE settings.
- `kotlinx.coroutines.flow.internal.FlowValueWrapperInternalKt.unwrapInternal` -- returns passed argument by default; the agent instruments it to call `unwrapInternalDebuggerCapture` instead
- `kotlinx.coroutines.flow.internal.FlowValueWrapperInternalKt.unwrapInternalDebuggerCapture` -- unwraps passed argument so it returns the original value; only used after transformation
- `kotlinx.coroutines.flow.internal.FlowValueWrapperInternalKt.emitInternal(FlowCollector, value)` -- common insertion point for a debugger agent; simplifies instrumentation; the value is always being unwrapped inside

One internal method was added to `BufferedChannelIterator`: `nextInternal` -- same as `next` but may return a wrapped value. It should only be used with a function that is capable of unwrapping the value (see `BufferedChannel.emitAll` and `BufferedChannelIterator.next`), so there's a guarantee a wrapped value will always unwrap before emitting.

Why not just let `next` return a maybe wrapped value? That's because it is heavily used outside a currently supported scope. For example, one may just indirectly call it from a for-loop. In this case, unwrapping will never happen, and a user will get a handful of `ClassCastException`s.

One public method was added to support `buffer` and operators that use it inside:
- `ReceiveChannel.emitAll`. It encapsulates emitting values in `FlowCollector.emitAllImpl` and has a special implementation in `BufferedChannel`.

# Included IntelliJ-related patches

## Asynchronous stack traces for flows in the IDEA debugger

The agent needs three entities to establish a proper asynchronous stack traces connection:
- a capture point — method that indicates the stack trace that precedes the current stack trace;
- an insertion point — method within the current stack trace;
- a key — an object that is present in both points and is unique enough to bridge two stack traces properly.

The key for MutableStateFlow is the element itself. For MutableSharedFlow, the element is wrapped into a unique object to prevent bridging mistakes when two equal elements are emitted from different places.

Most of the operators applicable to flows (such as `map`, `scan`, `debounce`, `timeout`, `buffer`) are supported. As some of them use an intermediary flow inside, the transferred values are wrapped and unwrapped the same way as in MutableSharedFlow.
It means there may be all-library async stack traces between a stack trace containing `emit` and a stack trace containing `collect`.

### API

Some logic related to instrumentation was extracted to separate methods so that the debugger agent could instrument it properly:

- `kotlinx.coroutines.flow.internal.FlowValueWrapperInternal` -- wrapper class used to create a unique object for the debugger agent
- `kotlinx.coroutines.flow.internal.FlowValueWrapperInternalKt.wrapInternal` -- returns passed argument by default; the agent instruments it to call `wrapInternalDebuggerCapture` instead
- `kotlinx.coroutines.flow.internal.FlowValueWrapperInternalKt.wrapInternalDebuggerCapture` -- wraps passed arguments into a `FlowValueWrapperInternal`; only used after transformation.
- `kotlinx.coroutines.flow.internal.FlowValueWrapperInternalKt.unwrapInternal` -- returns passed argument by default; the agent instruments it to call `unwrapInternalDebuggerCapture` instead
- `kotlinx.coroutines.flow.internal.FlowValueWrapperInternalKt.unwrapInternalDebuggerCapture` -- unwraps passed argument so it returns the original value; only used after transformation
- `kotlinx.coroutines.flow.internal.FlowValueWrapperInternalKt.unwrapTyped` -- utility function served to ease casting to a real underlying type
- `kotlinx.coroutines.flow.internal.FlowValueWrapperInternalKt.emitInternal(FlowCollector, value)` -- alternative of a regular `FlowCollector.emit` that supports insertion points; if there is a `FlowCollector`, its `emit` call can be replaced with `emitInternal` so this case would also be supported for constructing async stack traces 
- `kotlinx.coroutines.flow.internal.FlowValueWrapperInternalKt.debuggerCapture` -- common insertion point for a debugger agent; simplifies instrumentation; the value is always being unwrapped inside. `emitInternal` uses this method.

One internal method was added to `BufferedChannelIterator`: `nextInternal` -- same as `next` but may return a wrapped value. It should only be used with a function that is capable of unwrapping the value (see `BufferedChannel.emitAll` and `BufferedChannelIterator.next`), so there's a guarantee a wrapped value will always unwrap before emitting.

Why not just let `next` return a maybe wrapped value? That's because it is heavily used outside a currently supported scope. For example, one may just indirectly call it from a for-loop. In this case, unwrapping will never happen, and a user will get a handful of `ClassCastException`s.

One public method was added to support `buffer` and operators that use it inside:
- `ReceiveChannel.emitAll`. It encapsulates emitting values in `FlowCollector.emitAllImpl` and has a special implementation in `BufferedChannel`.

Changes were made to lambda parameter `onElementRetrieved` in `BufferedChannel<E>` methods: now they accept `Any?` instead of `E` because now they may be given a wrapped value.

`SelectImplementation.complete` now uses `debuggerCapture` to properly propagate value that might come from flows. 

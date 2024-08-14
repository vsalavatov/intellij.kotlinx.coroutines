# Included IntelliJ-related patches

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

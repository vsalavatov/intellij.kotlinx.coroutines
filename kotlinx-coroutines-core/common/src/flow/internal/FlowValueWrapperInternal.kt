package kotlinx.coroutines.flow.internal

/**
 * Used by IDEA debugger agent to support asynchronous stack traces in flows.
 * The agent requires a unique object present in both current and async stack traces,
 * so, without a wrapper, if two flows, `f1` and `f2`, emit equal values,
 * the agent could suggest `f1` as emitter for `f2.collect` and `f2` as emitter for `f1.collect`.
 */
internal class FlowValueWrapperInternal<T>(val value: T)

internal fun <T> wrapInternal(value: T): T = value
internal fun <T> unwrapInternal(value: T): T = value

// debugger agent transforms wrapInternal so it returns wrapInternalDebuggerCapture(value) instead of just value
private fun wrapInternalDebuggerCapture(value: Any?): Any = FlowValueWrapperInternal(value)

// debugger agent transforms unwrapInternal so it returns unwrapInternalDebuggerCapture(value) instead of just value
//
// normally, value is always FlowValueWrapperInternal, but potentially instrumentation may start
// in the middle of the execution (for example, when the debugger was attached to a running application),
// and the emitted value hadn't been wrapped
private fun unwrapInternalDebuggerCapture(value: Any?): Any? = (value as? FlowValueWrapperInternal<*>)?.value ?: value

package com.ajays.modernjava.payments.context;

import java.util.NoSuchElementException;
import lombok.NonNull;

/// Who is asking and for which request, visible to every method on the call path without
/// being passed as a parameter.
///
/// ## Feature: scoped values (JEP 506, final in Java 25)
///
/// A [ScopedValue] is a one-way, immutable, *bounded* channel from a caller to code it calls
/// (however deep), on the same thread and, with structured concurrency, its child threads:
///
/// ```
/// new RequestContext("corr-1", "merchant-7").call(() -> gateway.submit(request, profile));
///
/// // anywhere below, e.g. in an issuer client:
/// var key = RequestContext.current().correlationId();
/// ```
///
/// The binding exists only while `call` runs. When it returns or throws, the value is gone.
/// Nothing needs cleaning up, and nothing leaks into the next request served by the thread.
///
/// ## Why not `ThreadLocal`?
///
/// `ThreadLocal` was the Java 8 answer (and is what SLF4J's `MDC` uses). It has three
/// problems that virtual threads make worse:
///
/// | | `ThreadLocal` | `ScopedValue` |
/// |---|---|---|
/// | Mutability | `set()` anywhere, by anyone, any time | Immutable; rebinding is only possible for a nested scope |
/// | Lifetime | Until `remove()`. Forget it on a pooled thread and the next request sees stale data | Exactly the dynamic extent of `call`/`run` |
/// | Child threads | `InheritableThreadLocal` *copies* the map into every child: costly with millions of virtual threads | Child threads in a `StructuredTaskScope` share the parent's bindings, no copying |
/// | Reads | Hash lookup in a per-thread map | Cached per thread; designed for frequent reads on hot paths |
///
/// Keep `ThreadLocal` for per-thread *caches* of expensive objects on platform threads.
/// For passing context down a call chain, use a scoped value.
///
/// ## Trap: plain executors do **not** inherit bindings
///
/// A task submitted to an `ExecutorService` (even a virtual-thread one) runs with **no**
/// bindings, because the executor's threads aren't children of the submitting scope. Rebind
/// inside the task, as [com.ajays.modernjava.payments.capture.CaptureRun] does. Only
/// `StructuredTaskScope` (preview; see `src/preview`) propagates bindings automatically,
/// because its structure guarantees the children finish before the scope ends.
///
/// ## Why a record, with the `ScopedValue` as a static field?
///
/// One immutable value per binding keeps every field consistent with every other. The
/// `ScopedValue` key is a `static final` constant, like a `ThreadLocal` would be, and it's
/// private: callers go through [#call(ScopedValue.CallableOp)] and [#current()], so nobody
/// can bind a half-built context.
///
/// @param correlationId identifies one client request across logs and downstream calls;
///                      also used as the issuer's idempotency key
/// @param merchantId    merchant on whose behalf the request runs
public record RequestContext(@NonNull String correlationId, @NonNull String merchantId) {

    private static final ScopedValue<RequestContext> CURRENT = ScopedValue.newInstance();

    public RequestContext {
        if (correlationId.isBlank() || merchantId.isBlank()) {
            throw new IllegalArgumentException("correlationId and merchantId are required");
        }
    }

    /// Runs `op` with this context bound, and returns its result. Checked exceptions thrown
    /// by `op` pass through with their own type (`X`), so no wrapping is needed.
    public <T, X extends Throwable> T call(ScopedValue.CallableOp<? extends T, X> op) throws X {
        return ScopedValue.where(CURRENT, this).call(op);
    }

    /// Runs `op` with this context bound.
    public void run(Runnable op) {
        ScopedValue.where(CURRENT, this).run(op);
    }

    /// The context bound by the nearest enclosing [#call(ScopedValue.CallableOp)].
    ///
    /// @throws NoSuchElementException if called outside any request
    public static RequestContext current() {
        return CURRENT.orElseThrow(() -> new NoSuchElementException(
                "No RequestContext bound: call this inside RequestContext.call/run"));
    }

    /// Whether code is running inside a request.
    public static boolean isBound() {
        return CURRENT.isBound();
    }
}

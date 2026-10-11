package com.ajays.modernjava.payments.capture;

import com.ajays.modernjava.payments.context.RequestContext;
import com.ajays.modernjava.payments.domain.Payment;
import com.ajays.modernjava.payments.domain.PaymentCommand.Capture;
import com.ajays.modernjava.payments.issuer.IssuerClient;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import lombok.NonNull;

/// End-of-day job: capture every authorized payment with the issuer.
///
/// Thousands of payments, each needing a ~100 ms blocking issuer call. On a pool of 50
/// platform threads, 10 000 captures take 20 s. With one virtual thread per capture, the run
/// is bounded only by how many calls the issuer lets us have in flight.
///
/// ## Feature: virtual threads (JEP 444, final in Java 21)
///
/// ```
/// var factory = Thread.ofVirtual().name("capture-", 0).factory();
/// try (var executor = Executors.newThreadPerTaskExecutor(factory)) {
///     futures = payments.stream().map(p -> executor.submit(() -> captureOne(p))).toList();
/// }   // close() waits for every task
/// ```
///
/// - **One new virtual thread per task, never a pool.** Virtual threads are cheap to create
///   and are meant to be thrown away. Pooling them adds nothing and reintroduces the old
///   question of how big the pool should be.
/// - **Name them.** `Thread.ofVirtual().name("capture-", 0)` gives `capture-0`,
///   `capture-1`, ... in thread dumps (`jcmd <pid> Thread.dump_to_file -format=json`), which
///   include virtual threads. Unnamed virtual threads have an empty name.
/// - **`ExecutorService` is `AutoCloseable`** (Java 19). `close()` waits for submitted
///   tasks to finish, so try-with-resources is a join point and no task outlives the method.
/// - **`Future.state()`, `resultNow()`, `exceptionNow()`** (Java 19) read a finished future
///   without `get()`'s checked exceptions. The `switch` over `Future.State` below has no
///   `default`, following this project's rule for enums.
///
/// ## Limit concurrency with a semaphore, not a pool size
///
/// Old code limited load on a downstream system by sizing a thread pool. With virtual threads,
/// threads are no longer the scarce resource; the issuer's rate limit is. So limit *that*,
/// directly, with a [Semaphore] of `maxInFlight` permits (a *bulkhead*). Tasks waiting for a
/// permit are blocked virtual threads, which cost almost nothing.
///
/// ## Trap: scoped values are not inherited by executor tasks
///
/// The caller binds a [RequestContext] for the run. The tasks don't see it: an executor's
/// threads are not children of the caller's scope. `captureAll` reads the run's context on the
/// calling thread and binds a per-payment context inside each task. That's also the right
/// semantics here, because each capture needs its own idempotency key.
///
/// ## Why not structured concurrency here?
///
/// `StructuredTaskScope` (preview in JDK 27) is built for **one unit of work split into
/// subtasks**: if one fails, the others are pointless and get cancelled. A capture run is the
/// opposite: **independent jobs**, where one issuer error must not cancel the other 9 999.
/// An executor plus per-task outcomes fits that. See `ParallelRiskSignals` in `src/preview`
/// for the case structured concurrency is made for.
///
/// ## When virtual threads don't help
///
/// They make *waiting* cheap; they don't make *computing* faster. CPU-bound work still has only
/// as many cores as the machine, so use a platform-thread pool (or parallel streams) for it.
public final class CaptureRun {

    private final IssuerClient issuer;
    private final Clock clock;
    private final int maxInFlight;

    /// @param maxInFlight most issuer calls allowed at the same time
    public CaptureRun(@NonNull IssuerClient issuer, @NonNull Clock clock, int maxInFlight) {
        if (maxInFlight <= 0) {
            throw new IllegalArgumentException("maxInFlight must be positive: " + maxInFlight);
        }
        this.issuer = issuer;
        this.clock = clock;
        this.maxInFlight = maxInFlight;
    }

    /// What happened to each payment.
    ///
    /// @param captured payments now in `Captured` state, in input order
    /// @param failed   payments left unchanged, with the reason
    public record Result(List<Payment> captured, List<Failure> failed) {
        public Result {
            captured = List.copyOf(captured);
            failed = List.copyOf(failed);
        }
    }

    /// A payment that could not be captured.
    public record Failure(@NonNull Payment payment, @NonNull Throwable cause) {}

    /// Captures every payment, one virtual thread each, at most `maxInFlight` issuer calls at a
    /// time. Never throws for a single payment's failure; failures are reported in the result.
    ///
    /// Must be called inside a [RequestContext]; each capture runs under
    /// `<run correlation id>/<payment id>`.
    public Result captureAll(@NonNull List<Payment> payments) {
        var run = RequestContext.current();   // read on the caller's thread: tasks can't see it
        var permits = new Semaphore(maxInFlight);
        var factory = Thread.ofVirtual().name("capture-", 0).factory();

        List<Future<Payment>> futures;
        try (var executor = Executors.newThreadPerTaskExecutor(factory)) {
            futures = payments.stream()
                    .map(payment -> executor.submit(() -> captureOne(payment, run, permits)))
                    .toList();
        }   // waits for all tasks; if this thread is interrupted, cancels the rest and still waits

        var captured = new ArrayList<Payment>();
        var failed = new ArrayList<Failure>();
        for (int i = 0; i < payments.size(); i++) {
            var future = futures.get(i);
            var payment = payments.get(i);
            switch (future.state()) {
                case SUCCESS -> captured.add(future.resultNow());
                case FAILED -> failed.add(new Failure(payment, future.exceptionNow()));
                case CANCELLED -> failed.add(new Failure(payment, new CancellationException("capture run interrupted")));
                case RUNNING -> throw new IllegalStateException("executor closed with a task still running");
            }
        }
        return new Result(captured, failed);
    }

    private Payment captureOne(Payment payment, RequestContext run, Semaphore permits) throws InterruptedException {
        var next = payment.apply(new Capture(), clock);   // check the state machine before calling out
        var context = new RequestContext(run.correlationId() + "/" + payment.id().value(), run.merchantId());

        permits.acquire();   // blocks this virtual thread only
        try {
            return context.call(() -> {
                issuer.capture(payment);
                return next;
            });
        } finally {
            permits.release();
        }
    }
}

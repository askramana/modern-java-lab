package com.ajays.modernjava.payments.issuer;

import com.ajays.modernjava.payments.context.RequestContext;
import com.ajays.modernjava.payments.domain.DeclineReason;
import com.ajays.modernjava.payments.domain.Payment;
import com.ajays.modernjava.payments.issuer.AuthorizationResult.Approved;
import com.ajays.modernjava.payments.issuer.AuthorizationResult.Declined;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import lombok.NonNull;

/// An in-memory issuer with network-like latency. Stands in for an HTTP client in tests and
/// tours.
///
/// Every call **sleeps** for the configured latency, which is a real blocking call: on a
/// virtual thread it unmounts from its carrier exactly as a socket read would.
///
/// ## Feature: `synchronized` no longer pins virtual threads (JEP 491, Java 24)
///
/// The idempotency ledger below is guarded by plain `synchronized` blocks, and thousands of
/// virtual threads may contend for them during a capture run.
///
/// In Java 21 – 23 that was a trap. A virtual thread inside `synchronized`, or *waiting to
/// enter* it, was **pinned**: it couldn't unmount, so it held its carrier thread. With only as
/// many carriers as CPU cores, a handful of virtual threads blocked in or on a monitor could
/// stall every other virtual thread in the JVM. The advice was "replace `synchronized` with
/// `ReentrantLock`", and libraries were rewritten for it.
///
/// Since Java 24 the JVM tracks monitor ownership per virtual thread, so blocking in, or on,
/// a monitor unmounts like any other blocking call. On the JDK 25 baseline:
///
/// - Don't rewrite working `synchronized` code to `ReentrantLock` for Loom's sake. Choose a
///   lock for its features (`tryLock`, timeouts, fairness, conditions), not for pinning.
/// - Remaining pinning cases are rare: blocking inside a **native** frame (JNI, or a
///   `Linker` downcall), and class initializers. JFR's `jdk.VirtualThreadPinned` event
///   reports them. (`-Djdk.tracePinnedThreads` was removed in Java 24.)
///
/// Note the latency is spent *outside* the lock. Holding any lock across a slow call
/// serializes callers; that was bad advice before Loom and still is.
public final class SimulatedIssuer implements IssuerClient {

    private static final BigDecimal DEFAULT_AVAILABLE_BALANCE = new BigDecimal("1000000");

    private final Duration latency;
    private final BigDecimal availableBalance;

    // guarded by `this`; keyed by RequestContext.correlationId()
    private final Map<String, AuthorizationResult> authorizations = new HashMap<>();
    private final Set<String> captures = new HashSet<>();

    /// @param latency          how long each call blocks
    /// @param availableBalance payments above this amount are declined for insufficient funds
    public SimulatedIssuer(@NonNull Duration latency, @NonNull BigDecimal availableBalance) {
        this.latency = latency;
        this.availableBalance = availableBalance;
    }

    public SimulatedIssuer(Duration latency) {
        this(latency, DEFAULT_AVAILABLE_BALANCE);
    }

    @Override
    public AuthorizationResult authorize(@NonNull Payment payment) {
        var key = RequestContext.current().correlationId();   // read first: fail fast outside a request
        networkRoundTrip();
        synchronized (this) {
            // A retry with the same key gets the original answer, not a second hold.
            return authorizations.computeIfAbsent(key, _ -> decide(payment));
        }
    }

    @Override
    public void capture(@NonNull Payment payment) {
        var key = RequestContext.current().correlationId();
        networkRoundTrip();
        synchronized (this) {
            captures.add(key);   // idempotent: capturing twice is a no-op
        }
    }

    /// Number of distinct captures received. For tests and tours.
    public synchronized int captureCount() {
        return captures.size();
    }

    private AuthorizationResult decide(Payment payment) {
        if (payment.amount().amount().compareTo(availableBalance) > 0) {
            return new Declined(DeclineReason.INSUFFICIENT_FUNDS);
        }
        return new Approved("AUTH" + ThreadLocalRandom.current().nextInt(100_000, 1_000_000));
    }

    private void networkRoundTrip() {
        try {
            Thread.sleep(latency);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();   // keep the flag so callers (and scopes) can see it
            throw new IssuerException("Interrupted while waiting for the issuer");
        }
    }
}

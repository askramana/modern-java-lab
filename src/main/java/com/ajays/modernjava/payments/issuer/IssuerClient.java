package com.ajays.modernjava.payments.issuer;

import com.ajays.modernjava.payments.domain.Payment;

/// The card issuer or bank that holds the customer's money.
///
/// ## Feature: blocking is fine again (virtual threads, JEP 444, final in Java 21)
///
/// Both methods **block** until the issuer answers, often for hundreds of milliseconds. This is
/// intentionally a plain synchronous interface, not `CompletableFuture<AuthorizationResult>` or
/// a reactive `Mono`.
///
/// Before Java 21, every blocked call held an OS thread (about 1 MB of stack, plus scheduler
/// cost). A server with 200 platform threads could have only 200 issuer calls in flight, so
/// high-throughput code went asynchronous: callbacks, `thenCompose` chains, stack traces that
/// end in a framework's event loop, and `try`/`catch` that no longer works across stages.
///
/// A virtual thread is a `Thread` that the JDK schedules onto a few carrier threads. When it
/// blocks on I/O, a lock or `sleep`, it **unmounts**: its stack is saved to the heap and the
/// carrier runs something else. Millions of blocked virtual threads cost only memory. So the
/// simple style (call, wait, use the result, catch the exception on the next line) scales
/// again, and stack traces and debuggers show the real call chain.
///
/// Callers decide where to run this: [com.ajays.modernjava.payments.capture.CaptureRun] runs
/// one virtual thread per capture. The interface itself knows nothing about threads, and that
/// is the point.
///
/// ## Idempotency comes from the request context
///
/// Networks fail after the issuer has acted but before the reply arrives, so clients retry.
/// Implementations use [com.ajays.modernjava.payments.context.RequestContext#current()]'s
/// correlation id as the idempotency key, so a retried request gets the original answer
/// instead of a second hold on the customer's money. Both methods must therefore be called
/// inside a bound `RequestContext`.
public interface IssuerClient {

    /// Asks the issuer to reserve the payment's amount. A business decline is a normal result,
    /// not an exception.
    AuthorizationResult authorize(Payment payment);

    /// Asks the issuer to move previously authorized funds.
    ///
    /// @throws IssuerException if the issuer can't be reached or refuses
    void capture(Payment payment);
}

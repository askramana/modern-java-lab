package com.ajays.modernjava.preview;

import com.ajays.modernjava.payments.domain.CustomerId;
import com.ajays.modernjava.payments.risk.CustomerProfile;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Subtask;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/// **PREVIEW**: structured concurrency (JEP 533, seventh preview in JDK 27). Compiled only by
/// `mvn -Ppreview verify` on JDK 27.
///
/// Builds the [CustomerProfile] that `RiskEngine` needs from three services, called **at the
/// same time**: KYC (home country), accounts (age) and a velocity counter. Sequentially that is
/// the *sum* of three latencies on every payment; in parallel it's the *max*.
///
/// ## The problem with unstructured concurrency
///
/// ```
/// Future<String> country = executor.submit(() -> kyc.homeCountry(id));
/// Future<Integer> age = executor.submit(() -> accounts.ageDays(id));
/// return new CustomerProfile(id, country.get(), age.get(), ...);
/// ```
///
/// If `country.get()` throws, `age` keeps running: nobody cancels it, and it may outlive the
/// request (a *thread leak*). If the calling thread is interrupted, neither subtask notices. A
/// thread dump shows unrelated pool threads, with nothing linking them to the request.
///
/// ## The structured version
///
/// ```
/// try (var scope = StructuredTaskScope.open(cfg -> cfg.withTimeout(timeout))) {
///     Subtask<String> country = scope.fork(() -> sources.homeCountry(id));
///     Subtask<Integer> age = scope.fork(() -> sources.accountAgeDays(id));
///     scope.join();                       // wait for all; throws if any failed
///     return new CustomerProfile(id, country.get(), age.get(), ...);
/// }                                       // close(): no subtask outlives this block
/// ```
///
/// Concurrency gets the same shape as a block of sequential code: subtasks start inside the
/// `try`, and they are guaranteed finished when it exits.
///
/// - **Fail fast, cancel the rest.** The default policy (no `Joiner` argument) waits for all
///   subtasks to succeed. The first failure cancels the scope: the others are **interrupted**
///   and `join()` throws `ExecutionException` with that failure as the cause.
/// - **Timeouts** come from configuration. On expiry the scope is cancelled and `join()`
///   throws `ExecutionException` caused by `StructuredTaskScope.CancelledByTimeoutException`.
/// - **Each fork is a new virtual thread** by default. They block on I/O cheaply.
/// - **Scoped values are inherited.** The subtasks see the caller's
///   [com.ajays.modernjava.payments.context.RequestContext], with no copying. That is exactly
///   what a plain executor can't do (see `CaptureRun`).
/// - **Observable.** `jcmd <pid> Thread.dump_to_file -format=json` shows the scope as a tree:
///   the request's thread, its scope, and each subtask thread under it.
///
/// ## Why still preview after seven rounds
///
/// The *idea* has been stable since JDK 19; the *API* has not. JDK 25 replaced subclassing
/// (`ShutdownOnFailure`) with `open(Joiner)`. JDK 27 added the third type parameter `R_X`,
/// so a joiner chooses the exception `join()` throws (see [FxQuotes]). Code written for one
/// preview usually doesn't compile on the next. Use it in production only if you accept
/// rewriting it on every JDK upgrade.
@RequiredArgsConstructor
public final class ParallelRiskSignals {

    /// The three services. Each method blocks for a network call.
    public interface Sources {
        String homeCountry(CustomerId customerId) throws Exception;

        int accountAgeDays(CustomerId customerId) throws Exception;

        int paymentsInLastHour(CustomerId customerId) throws Exception;
    }

    private final @NonNull Sources sources;
    private final @NonNull Duration timeout;

    /// Calls all three sources concurrently and combines their answers.
    ///
    /// @throws ExecutionException  if any source failed (its exception is the cause) or the
    ///                             timeout expired (cause: `CancelledByTimeoutException`)
    /// @throws InterruptedException if the calling thread was interrupted while waiting
    public CustomerProfile profileFor(@NonNull CustomerId customerId) throws ExecutionException, InterruptedException {
        try (var scope = StructuredTaskScope.open(cfg -> cfg.withName("risk-signals").withTimeout(timeout))) {
            Subtask<String> homeCountry = scope.fork(() -> sources.homeCountry(customerId));
            Subtask<Integer> accountAgeDays = scope.fork(() -> sources.accountAgeDays(customerId));
            Subtask<Integer> paymentsInLastHour = scope.fork(() -> sources.paymentsInLastHour(customerId));

            scope.join();   // after this, every subtask has succeeded, so get() can't fail

            return new CustomerProfile(customerId, homeCountry.get(), accountAgeDays.get(), paymentsInLastHour.get());
        }
    }
}

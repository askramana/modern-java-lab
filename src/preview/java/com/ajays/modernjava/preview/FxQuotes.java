package com.ajays.modernjava.preview;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Currency;
import java.util.List;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Joiner;
import lombok.NonNull;

/// **PREVIEW**: structured concurrency, "first answer wins" (JEP 533, seventh preview in
/// JDK 27). Compiled only by `mvn -Ppreview verify` on JDK 27.
///
/// Settling a USD card payment in INR needs an exchange rate. Several FX providers can quote
/// it, and their latency varies. Ask them all and take the **first successful** quote: a
/// *hedged request*. The slower calls are cancelled as soon as one quote arrives.
///
/// ## Feature: joiners choose the policy, and (since JDK 27) the exception
///
/// A [Joiner] decides when `join()` returns and what it returns. The built-in ones:
///
/// | Joiner | `join()` returns | Cancels the rest when |
/// |---|---|---|
/// | *(none: `open()`)* | `null` (read each `Subtask`) | any subtask fails |
/// | `allSuccessfulOrThrow()` | `List<T>` of results, in fork order | any subtask fails |
/// | `anySuccessfulOrThrow()` | the first successful result | any subtask succeeds |
/// | `awaitAllSuccessfulOrThrow()` | `null` | any subtask fails |
/// | `allUntil(predicate)` | `List<Subtask<T>>` | the predicate is true for a completed subtask |
///
/// By default, a failed outcome makes `join()` throw `ExecutionException`. JDK 27 adds
/// overloads that take a `Function<Throwable, R_X>`, so the scope throws **your** exception
/// type, checked or not, with no catch-and-wrap at the call site:
///
/// ```
/// StructuredTaskScope.open(
///         Joiner.<FxQuote, FxUnavailableException>anySuccessfulOrThrow(cause -> new FxUnavailableException(...)),
///         cfg -> cfg.withTimeout(timeout))
/// ```
///
/// That's why the scope type is now `StructuredTaskScope<T, R, R_X>`: `R_X` is what `join()`
/// throws. When every provider fails, the cause is one provider's exception. On timeout, it's
/// `StructuredTaskScope.CancelledByTimeoutException`.
///
/// ## Trap: only hedge idempotent reads
///
/// Racing requests is safe here because asking for a quote changes nothing. **Never** race
/// authorizations or captures across issuers or routes: the loser is cancelled on *our* side,
/// but the remote system may already have moved the money.
public final class FxQuotes {

    /// A rate source. `rate` blocks for a network call.
    public interface Provider {
        String name();

        BigDecimal rate(Currency from, Currency to) throws Exception;
    }

    /// A quote from one provider.
    ///
    /// @param rate units of `to` per unit of `from`
    public record FxQuote(@NonNull Currency from, @NonNull Currency to, @NonNull BigDecimal rate, @NonNull String provider) {}

    /// No provider produced a quote in time.
    public static final class FxUnavailableException extends Exception {
        FxUnavailableException(Currency from, Currency to, Throwable cause) {
            super("No FX quote for %s/%s".formatted(from, to), cause);
        }
    }

    private final List<Provider> providers;
    private final Duration timeout;

    public FxQuotes(@NonNull List<Provider> providers, @NonNull Duration timeout) {
        if (providers.isEmpty()) {
            throw new IllegalArgumentException("at least one provider is required");
        }
        this.providers = List.copyOf(providers);
        this.timeout = timeout;
    }

    /// Asks every provider at once and returns the first quote that arrives.
    ///
    /// @throws FxUnavailableException if every provider failed or none answered within the timeout
    /// @throws InterruptedException   if the calling thread was interrupted while waiting
    public FxQuote fastest(@NonNull Currency from, @NonNull Currency to) throws FxUnavailableException, InterruptedException {
        var joiner = Joiner.<FxQuote, FxUnavailableException>anySuccessfulOrThrow(
                cause -> new FxUnavailableException(from, to, cause));

        try (var scope = StructuredTaskScope.open(joiner, cfg -> cfg.withName("fx-quotes").withTimeout(timeout))) {
            for (var provider : providers) {
                scope.fork(() -> new FxQuote(from, to, provider.rate(from, to), provider.name()));
            }
            return scope.join();   // throws FxUnavailableException, not ExecutionException
        }
    }
}

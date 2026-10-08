package com.ajays.modernjava.payments.domain;

/// Why a payment was declined.
///
/// ## Feature: switch expressions (JEP 361, final in Java 14)
///
/// [#isRetryable()] is a `switch` *expression*: arrow labels (no fall-through, no `break`),
/// several constants per label, and a value as the result.
///
/// It deliberately has **no `default`**. A switch expression must be exhaustive, and
/// listing every constant satisfies that. When someone adds `VELOCITY_LIMIT` next quarter,
/// every such switch stops compiling and points at the exact line that needs a decision.
/// With a `default`, the new constant would silently fall into whatever bucket `default`
/// picks. If stale bytecode meets a new constant at runtime anyway, Java 21+ throws
/// `MatchException` instead of misbehaving quietly.
///
/// **Rule:** for enums and sealed types you own, never write `default` in a switch that
/// maps every variant to a decision.
public enum DeclineReason {
    INSUFFICIENT_FUNDS,
    SUSPECTED_FRAUD,
    EXPIRED_INSTRUMENT,
    LIMIT_EXCEEDED,
    ISSUER_UNAVAILABLE,
    INVALID_REQUEST;

    /// Whether a client may retry the same payment later without changing anything.
    public boolean isRetryable() {
        return switch (this) {
            case ISSUER_UNAVAILABLE, INSUFFICIENT_FUNDS -> true;
            case SUSPECTED_FRAUD, EXPIRED_INSTRUMENT, LIMIT_EXCEEDED, INVALID_REQUEST -> false;
        };
    }
}

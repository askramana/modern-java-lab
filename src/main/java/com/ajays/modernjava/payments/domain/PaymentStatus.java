package com.ajays.modernjava.payments.domain;

import java.time.Instant;
import lombok.NonNull;

/// Lifecycle state of a [Payment].
///
/// ## Pattern: state as a sealed hierarchy of records (instead of an enum + nullable fields)
///
/// The Java 8 way is an enum `status` plus fields such as `authCode`, `declineReason` and
/// `refundedAmount` on the payment, most of which are `null` most of the time. Nothing stops
/// a `CAPTURED` payment from having a `declineReason`.
///
/// Here each state carries **exactly the data that exists in that state**:
///
/// ```
/// Initiated ──authorize──▶ Authorized ──capture──▶ Captured ──refund──▶ Refunded ⟲ refund
///     │                        │
///     └────────decline─────────┴──▶ Declined
/// ```
///
/// An `Authorized` without an auth code can't be constructed, and a `Declined` always has
/// a reason. Illegal states are unrepresentable instead of being checked at runtime.
///
/// A sealed interface can also permit an `enum` (e.g. for stateless states). Records were
/// chosen here so every state records *when* it happened.
public sealed interface PaymentStatus {

    /// When this state was entered.
    Instant at();

    /// Created, not yet sent to the issuer.
    record Initiated(@NonNull Instant at) implements PaymentStatus {}

    /// Issuer reserved the funds.
    ///
    /// @param authCode issuer authorization code
    record Authorized(@NonNull String authCode, @NonNull Instant at) implements PaymentStatus {
        public Authorized {
            if (authCode.isBlank()) {
                throw new IllegalArgumentException("authCode required");
            }
        }
    }

    /// Funds moved. Terminal unless refunded.
    record Captured(@NonNull Instant at) implements PaymentStatus {}

    /// Rejected by risk or by the issuer. Terminal.
    record Declined(@NonNull DeclineReason reason, @NonNull Instant at) implements PaymentStatus {}

    /// Partly or fully refunded.
    ///
    /// @param totalRefunded cumulative amount refunded so far
    record Refunded(@NonNull Money totalRefunded, @NonNull Instant at) implements PaymentStatus {}
}

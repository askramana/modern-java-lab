package com.ajays.modernjava.payments.domain;

import java.time.Instant;
import java.util.Objects;

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
    record Initiated(Instant at) implements PaymentStatus {
        public Initiated { Objects.requireNonNull(at, "at"); }
    }

    /// Issuer reserved the funds.
    ///
    /// @param authCode issuer authorization code
    record Authorized(String authCode, Instant at) implements PaymentStatus {
        public Authorized {
            Objects.requireNonNull(at, "at");
            if (authCode == null || authCode.isBlank()) {
                throw new IllegalArgumentException("authCode required");
            }
        }
    }

    /// Funds moved. Terminal unless refunded.
    record Captured(Instant at) implements PaymentStatus {
        public Captured { Objects.requireNonNull(at, "at"); }
    }

    /// Rejected by risk or by the issuer. Terminal.
    record Declined(DeclineReason reason, Instant at) implements PaymentStatus {
        public Declined {
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(at, "at");
        }
    }

    /// Partly or fully refunded.
    ///
    /// @param totalRefunded cumulative amount refunded so far
    record Refunded(Money totalRefunded, Instant at) implements PaymentStatus {
        public Refunded {
            Objects.requireNonNull(totalRefunded, "totalRefunded");
            Objects.requireNonNull(at, "at");
        }
    }
}

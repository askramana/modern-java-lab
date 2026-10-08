package com.ajays.modernjava.payments.risk;

import com.ajays.modernjava.payments.domain.DeclineReason;
import java.util.List;
import lombok.NonNull;

/// Outcome of a risk evaluation.
///
/// Before sealed types, results like this were modelled as a class with a `type` enum plus
/// optional fields, or as checked exceptions for the negative cases. A sealed result type
/// forces every caller to handle every outcome, and the compiler checks that it does.
public sealed interface RiskDecision {

    /// Proceed to the issuer.
    record Approve() implements RiskDecision {}

    /// Hold for manual review.
    ///
    /// ## Trap: records are only *shallowly* immutable
    ///
    /// Without the compact constructor below, a caller could do:
    ///
    /// ```
    /// var reasons = new ArrayList<String>();
    /// var review = new Review(reasons);
    /// reasons.add("tampered");   // review.reasons() changes too
    /// ```
    ///
    /// `List.copyOf` takes an immutable snapshot. It returns the same instance if the input
    /// is already an immutable list, so it costs nothing in the common case. It also rejects
    /// `null` elements.
    ///
    /// @param reasons non-empty, human-readable reasons
    record Review(@NonNull List<String> reasons) implements RiskDecision {
        public Review {
            reasons = List.copyOf(reasons);
            if (reasons.isEmpty()) {
                throw new IllegalArgumentException("A review needs at least one reason");
            }
        }

        public Review(String reason) {   // non-canonical constructors must delegate to the canonical one
            this(List.of(reason));
        }
    }

    /// Decline before reaching the issuer.
    record Reject(@NonNull DeclineReason reason, @NonNull String detail) implements RiskDecision {}
}

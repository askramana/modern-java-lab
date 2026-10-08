package com.ajays.modernjava.payments.domain;

import java.util.Objects;

/// Something that can be asked of a [Payment]. Applied by [Payment#apply(PaymentCommand, java.time.Clock)].
///
/// Commands are sealed for the same reason as states: the state machine is a `switch` over
/// *state × command*, and both sides being closed lets the compiler verify that every
/// combination has been considered.
///
/// [Capture] has no components. An empty record is still useful: it's a distinct type that
/// can be pattern-matched, with `equals`/`hashCode`/`toString` for free.
public sealed interface PaymentCommand {

    /// Mark the payment authorized by the issuer.
    ///
    /// @param authCode issuer authorization code
    record Authorize(String authCode) implements PaymentCommand {
        public Authorize { Objects.requireNonNull(authCode, "authCode"); }
    }

    /// Move the authorized funds.
    record Capture() implements PaymentCommand {}

    /// Reject the payment.
    record Decline(DeclineReason reason) implements PaymentCommand {
        public Decline { Objects.requireNonNull(reason, "reason"); }
    }

    /// Return some or all of a captured amount. May be applied more than once.
    record Refund(Money amount) implements PaymentCommand {
        public Refund { Objects.requireNonNull(amount, "amount"); }
    }
}

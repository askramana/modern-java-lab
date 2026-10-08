package com.ajays.modernjava.payments.domain;

import com.ajays.modernjava.payments.domain.PaymentCommand.Authorize;
import com.ajays.modernjava.payments.domain.PaymentCommand.Capture;
import com.ajays.modernjava.payments.domain.PaymentCommand.Decline;
import com.ajays.modernjava.payments.domain.PaymentCommand.Refund;
import com.ajays.modernjava.payments.domain.PaymentStatus.*;
import lombok.Builder;
import lombok.With;

import java.time.Clock;
import java.util.Objects;

/// A payment and its current lifecycle state. Immutable: every transition returns a new
/// `Payment`.
///
/// ## Feature: pattern matching for `switch` (JEP 441, final in Java 21)
///
/// [#apply(PaymentCommand, Clock)] is the whole state machine, written as two nested
/// switches over sealed types:
///
/// - **Type patterns** (`case Captured _`) test the runtime type.
/// - **Record patterns** (`case Refund(var amount)`, JEP 440, Java 21) test the type *and*
///   pull out components in one step, with no casts and no accessor calls.
/// - **Guards** (`case Refund(var r) when r.isGreaterThan(amount)`) add a boolean condition.
///   A guarded case must come before the unguarded case for the same pattern, or the
///   compiler reports it as *dominated* (unreachable).
/// - **Exhaustiveness without `default`.** Both `PaymentStatus` and `PaymentCommand` are
///   sealed, so the compiler checks that every (state, command) pair is handled. Add a
///   `Chargeback` command and this method stops compiling until you decide what a chargeback
///   means in each state. That is the main payoff of sealing.
///
/// ## Feature: unnamed patterns and variables `_` (JEP 456, final in Java 22)
///
/// `case Capture _` says "I care about the type, not the value". `Refunded(var already, _)`
/// ignores the timestamp. `case Authorize _, Refund _ ->` puts several patterns on one
/// label, which is only allowed when none of them binds a variable.
///
/// ## Why nested switches instead of one switch over a (state, command) pair?
///
/// You *can* write `switch (new Step(status, command))` with a local `record Step` and
/// patterns like `case Step(Initiated _, Authorize(var code))`. It reads well, but a
/// catch-all `case Step _ -> throw ...` is usually needed at the end, and once it's there the
/// compiler stops warning you about new combinations. Nested switches with no catch-all keep
/// the exhaustiveness guarantee intact.
///
/// ## Records + Lombok: filling the gaps, not replacing records
///
/// - `@With` generates `withStatus(PaymentStatus)` etc. Java still has no built-in way to
///   copy a record with one component changed. *Derived record creation*
///   (`payment with { status = next; }`, JEP 468) has been proposed but has not shipped.
///   Until it does, `@With` avoids hand-written copy methods that rot when a component is
///   added. The generated code calls the canonical constructor, so validation still runs.
/// - `@Builder` makes test fixtures with five components readable. Production code uses
///   [#initiate(CustomerId, Money, PaymentMethod, Clock)], which encodes the domain rule that
///   new payments always start as `Initiated`.
///
/// @param id         identity
/// @param customerId who pays
/// @param amount     original amount
/// @param method     how they pay
/// @param status     current lifecycle state
@With
@Builder(toBuilder = true)
public record Payment(PaymentId id, CustomerId customerId, Money amount, PaymentMethod method, PaymentStatus status) {

    public Payment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(status, "status");
    }

    public static Payment initiate(CustomerId customerId, Money amount, PaymentMethod method, Clock clock) {
        return new Payment(PaymentId.newId(), customerId, amount, method, new Initiated(clock.instant()));
    }

    /// Applies a command and returns the payment in its next state.
    ///
    /// @throws IllegalTransitionException if the command is not valid in the current state
    /// @throws IllegalArgumentException   if a refund would exceed the captured amount
    public Payment apply(PaymentCommand command, Clock clock) {
        Objects.requireNonNull(command, "command");
        var now = clock.instant();

        PaymentStatus next = switch (status) {
            case Initiated _ -> switch (command) {
                case Authorize(var authCode) -> new Authorized(authCode, now);
                case Decline(var reason) -> new Declined(reason, now);
                case Capture _, Refund _ -> throw illegal(command);
            };
            case Authorized _ -> switch (command) {
                case Capture _ -> new Captured(now);
                case Decline(var reason) -> new Declined(reason, now);
                case Authorize _, Refund _ -> throw illegal(command);
            };
            case Captured _ -> switch (command) {
                case Refund(var refund) when refund.isGreaterThan(amount) -> throw overRefund(refund);
                case Refund(var refund) -> new Refunded(refund, now);
                case Authorize _, Capture _, Decline _ -> throw illegal(command);
            };
            case Refunded(var alreadyRefunded, _) -> switch (command) {
                case Refund(var more) when alreadyRefunded.plus(more).isGreaterThan(amount) -> throw overRefund(more);
                case Refund(var more) -> new Refunded(alreadyRefunded.plus(more), now);
                case Authorize _, Capture _, Decline _ -> throw illegal(command);
            };
            case Declined _ -> throw illegal(command);
        };
        return withStatus(next);
    }

    /// How much can still be refunded. Shows several type patterns sharing one label.
    public Money refundableBalance() {
        return switch (status) {
            case Captured _ -> amount;
            case Refunded(var refunded, _) -> amount.minus(refunded);
            case Initiated _, Authorized _, Declined _ -> Money.zero(amount.currency());
        };
    }

    private IllegalTransitionException illegal(PaymentCommand command) {
        return new IllegalTransitionException(id, status, command);
    }

    private IllegalArgumentException overRefund(Money requested) {
        return new IllegalArgumentException(
                "Refund of %s exceeds refundable balance %s".formatted(requested, refundableBalance()));
    }
}

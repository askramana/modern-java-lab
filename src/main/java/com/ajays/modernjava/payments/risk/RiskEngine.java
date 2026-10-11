package com.ajays.modernjava.payments.risk;

import com.ajays.modernjava.payments.domain.Payment;
import com.ajays.modernjava.payments.domain.PaymentMethod.BankTransfer;
import com.ajays.modernjava.payments.domain.PaymentMethod.Card;
import com.ajays.modernjava.payments.domain.PaymentMethod.ExternalWallet;
import com.ajays.modernjava.payments.domain.PaymentMethod.Upi;
import com.ajays.modernjava.payments.risk.RiskDecision.Approve;
import com.ajays.modernjava.payments.risk.RiskDecision.Reject;
import com.ajays.modernjava.payments.risk.RiskDecision.Review;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.YearMonth;

import static com.ajays.modernjava.payments.domain.DeclineReason.EXPIRED_INSTRUMENT;
import static com.ajays.modernjava.payments.domain.DeclineReason.LIMIT_EXCEEDED;

/// Pre-authorization risk rules.
///
/// ## Feature: pattern matching as a rules table
///
/// [#evaluate(Payment, CustomerProfile)] is a `switch` over the sealed
/// [com.ajays.modernjava.payments.domain.PaymentMethod]. Each `case` is one rule, read top
/// to bottom, and the first match wins. Compare it with the Java 8 equivalent:
///
/// ```
/// if (method instanceof Card) {
///     Card card = (Card) method;
///     if (card.getExpiry().isBefore(now)) return Decision.reject(...);
///     if (!card.getIssuingCountry().equals(home) && ...) return Decision.review(...);
/// } else if (method instanceof Upi) {
///     ...
/// }
/// return Decision.approve();   // also reached by any NEW payment method nobody thought about
/// ```
///
/// The modern version:
///
/// - **Destructures in the case label**: `case Card(_, _, _, var country)` binds only what
///   the rule needs and ignores the rest with `_`. (Not always the best choice; see the
///   trade-off below.)
/// - **Keeps rule conditions on the label**: `when` guards keep each rule on one line.
/// - **Fails closed at compile time**: the last label lists every permitted subtype
///   explicitly instead of using `default`. Add `Bnpl` to `PaymentMethod` and this class stops
///   compiling, so nobody can ship a new rail that silently bypasses risk.
///
/// ### Ordering and dominance
///
/// `case Card c when c.isExpiredAt(...)` must come before the unguarded `case Card _` (here
/// inside the final multi-pattern label). Put the unguarded one first and javac rejects the
/// guarded case as *dominated*. Guards are evaluated in order, like an if/else-if chain.
///
/// ### Record pattern vs type pattern + accessor
///
/// The expired-card and cross-border rules deliberately use the two styles side by side:
///
/// ```
/// case Card card when card.isExpiredAt(...)                         // type pattern + method
/// case Card(_, _, _, var issuingCountry) when !issuingCountry.equals(...)   // record pattern
/// ```
///
/// Both compile to roughly the same thing (deconstruction calls the accessors), and a
/// guarded case never counts toward exhaustiveness in either style. They differ in coupling:
///
/// | | Record pattern `Card(_, _, _, var c)` | Type pattern `Card card` + `card.issuingCountry()` |
/// |---|---|---|
/// | Binds by | **Position** | **Name** |
/// | Components reordered | Still compiles, **silently binds the wrong value** if types match (`last4` and `issuingCountry` are both `String`) | Unaffected |
/// | Component added | Every pattern breaks until it gets another `_` | Unaffected |
/// | Readability on wide records | Reader counts underscores | Self-describing |
/// | Nested shapes | One test across levels: `Payment(_, _, _, Card(_, var l4, _, _), Authorized(var code, _))` | Nested `instanceof` checks and casts |
/// | Exhaustiveness (unguarded) | Can prove coverage, e.g. over `Step(PaymentStatus, PaymentCommand)` pairs | Only at the top-level type |
///
/// **Rule of thumb:** use a record pattern when the *shape* matters (nesting, unguarded
/// exhaustiveness) or when you use most components of a small record (`Refund(var amount)`,
/// `Declined(var reason, _)`). Use a type pattern + accessor to pull one field out of a wide
/// record, especially one with several components of the same type. Java has no named
/// record patterns, so positional patterns get riskier as records get wider.
///
/// ### What patterns can't do (yet)
///
/// There are no *constant patterns*: you can't write `case Card(CardNetwork.AMEX, _, _, _)`.
/// Bind the component and use a guard instead: `case Card(var n, _, _, _) when n == AMEX`.
///
/// ## Lombok: `@Slf4j` and `@RequiredArgsConstructor`
///
/// This is a service with dependencies, not data, so it's a regular `final` class. Lombok
/// removes the logger and constructor boilerplate that records don't cover.
@Slf4j
@RequiredArgsConstructor
public final class RiskEngine {

    private final RiskPolicy policy;
    private final Clock clock;

    public RiskDecision evaluate(@NonNull Payment payment, @NonNull CustomerProfile profile) {
        var amount = payment.amount().amount();

        RiskDecision decision = switch (payment.method()) {
            case Card card when card.isExpiredAt(YearMonth.now(clock)) ->
                    new Reject(EXPIRED_INSTRUMENT, "Card expired " + card.expiry());

            // Positional: binds the 4th component. Reordering Card's two String components would
            // still compile and silently bind last4 here. See "Record pattern vs type pattern" above.
            case Card(_, _, _, var issuingCountry)
                    when !issuingCountry.equals(profile.homeCountry())
                    && amount.compareTo(policy.crossBorderReviewAbove()) > 0 ->
                    new Review("Cross-border card (%s) above %s".formatted(issuingCountry, policy.crossBorderReviewAbove()));

            case Upi upi when profile.paymentsInLastHour() >= policy.upiVelocityLimit() ->
                    new Review("UPI velocity %d/h via @%s".formatted(profile.paymentsInLastHour(), upi.handle()));

            case BankTransfer _
                    when profile.accountAgeDays() < policy.newAccountDays()
                    && amount.compareTo(policy.newAccountTransferLimit()) > 0 ->
                    new Reject(LIMIT_EXCEEDED, "New account transfer above " + policy.newAccountTransferLimit());

            // non-sealed branch: we can't list partner wallets, so we match the open interface itself
            case ExternalWallet wallet when !policy.trustedWalletProviders().contains(wallet.provider()) ->
                    new Review("Unvetted wallet provider: " + wallet.provider());

            case Card _, Upi _, BankTransfer _, ExternalWallet _ -> new Approve();
        };

        log.info("Risk decision for {}: {}", payment.id().value(), decision);
        return decision;
    }
}

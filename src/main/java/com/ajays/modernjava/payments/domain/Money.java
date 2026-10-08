package com.ajays.modernjava.payments.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import lombok.NonNull;

/// An amount of money in a single currency.
///
/// ## Feature: records (JEP 395, final in Java 16)
///
/// A record is a *transparent carrier for immutable data*: its API **is** its state.
/// From the header `(BigDecimal amount, Currency currency)` the compiler generates
/// `private final` fields, a canonical constructor, accessors named `amount()` and
/// `currency()` (not `getAmount()`), and `equals`/`hashCode`/`toString` over all components.
///
/// ## What a record gives you that Lombok's `@Value` doesn't:
///
/// - **Semantics, not just boilerplate removal.** The language *knows* a record is the
///   sum of its components, which is what lets pattern matching deconstruct it:
///   `if (x instanceof Money(var amount, var currency))`.
/// - **Safe serialization.** Deserialization always goes through the canonical
///   constructor, so the validation below also runs on untrusted bytes.
/// - **No annotation processor**, so IDEs, static analysers and javadoc see real code.
///
/// ## Feature: compact canonical constructor
///
/// `public Money { ... }` has no parameter list. It runs *before* the implicit field
/// assignments, so it's the place to validate and **normalize** components. Reassign the
/// *parameter* (`amount = ...`), never `this.amount`.
///
/// ## Why normalization matters here: the `BigDecimal` equality trap
///
/// `new BigDecimal("10.0").equals(new BigDecimal("10.00"))` is `false`: `BigDecimal.equals`
/// compares scale too. A record's generated `equals` delegates to each component's `equals`,
/// so without normalization `Money.of("10.0", "INR")` and `Money.of("10.00", "INR")` would
/// be unequal and land in different `HashMap` buckets. Setting the scale to the currency's
/// minor units in the constructor makes record equality mean *the same amount of money*.
///
/// ## Always-valid domain objects
///
/// The constructor rejects anything that isn't a valid amount of money, so every `Money`
/// in the system is valid. Boundary validation of user input (Jakarta Validation) is a
/// separate concern; see [com.ajays.modernjava.payments.ingest.PaymentRequest].
///
/// @param amount   non-negative, scaled to the currency's default fraction digits
/// @param currency ISO-4217 currency
public record Money(@NonNull BigDecimal amount, @NonNull Currency currency) implements Comparable<Money> {

    public Money {
        // null checks are injected here by Lombok's @NonNull; only real invariants remain
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Money cannot be negative: " + amount);
        }
        // Throws ArithmeticException if this would silently drop precision (e.g. 10.005 INR).
        amount = amount.setScale(currency.getDefaultFractionDigits(), RoundingMode.UNNECESSARY);
    }

    /// Static factories are still idiomatic on records; the canonical constructor stays the
    /// single place where invariants are enforced.
    public static Money of(String amount, String currencyCode) {
        return new Money(new BigDecimal(amount), Currency.getInstance(currencyCode));
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public boolean isGreaterThan(Money other) {
        return compareTo(other) > 0;
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount);
    }

    /// Records may override generated members. Keep overrides consistent with the
    /// components, otherwise you break the "API is the state" contract.
    @Override
    public String toString() {
        return currency.getCurrencyCode() + " " + amount.toPlainString();
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("Currency mismatch: " + currency + " vs " + other.currency);
        }
    }
}

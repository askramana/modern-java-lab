package com.ajays.modernjava.payments.risk;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;

/// Tunable thresholds for [RiskEngine].
///
/// ## Pattern: records as configuration
///
/// A record is a natural fit for immutable configuration: one place to validate, value
/// equality (handy for detecting "config changed"), and readable `toString` in logs.
/// Static factory methods such as [#defaults()] replace telescoping constructors.
///
/// @param crossBorderReviewAbove     amounts above this on a foreign-issued card go to review
/// @param upiVelocityLimit           UPI payments per hour that trigger review
/// @param newAccountDays             accounts younger than this are "new"
/// @param newAccountTransferLimit    bank transfers above this from new accounts are rejected
/// @param trustedWalletProviders     partner wallets that skip review
public record RiskPolicy(
        BigDecimal crossBorderReviewAbove,
        int upiVelocityLimit,
        int newAccountDays,
        BigDecimal newAccountTransferLimit,
        Set<String> trustedWalletProviders) {

    public RiskPolicy {
        Objects.requireNonNull(crossBorderReviewAbove, "crossBorderReviewAbove");
        Objects.requireNonNull(newAccountTransferLimit, "newAccountTransferLimit");
        trustedWalletProviders = Set.copyOf(trustedWalletProviders);   // defensive, immutable copy
        if (upiVelocityLimit <= 0 || newAccountDays < 0) {
            throw new IllegalArgumentException("invalid limits");
        }
    }

    public static RiskPolicy defaults() {
        return new RiskPolicy(new BigDecimal("50000"), 10, 7, new BigDecimal("100000"), Set.of("acmepay"));
    }
}

package com.ajays.modernjava.payments.wallets;

import com.ajays.modernjava.payments.domain.PaymentMethod.ExternalWallet;

/// A wallet contributed from outside the `domain` package.
///
/// This compiles only because [ExternalWallet] is `non-sealed`. Try implementing
/// `PaymentMethod` directly from this package: the compiler rejects it with
/// *"class is not allowed to extend sealed class: PaymentMethod"*.
///
/// @param provider partner name
/// @param walletId partner's wallet identifier
public record PartnerWallet(String provider, String walletId) implements ExternalWallet {

    public PartnerWallet {
        if (provider == null || provider.isBlank() || walletId == null || walletId.isBlank()) {
            throw new IllegalArgumentException("provider and walletId are required");
        }
    }
}

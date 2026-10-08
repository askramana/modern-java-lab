package com.ajays.modernjava.payments.wallets;

import com.ajays.modernjava.payments.domain.PaymentMethod.ExternalWallet;
import lombok.NonNull;

/// A wallet contributed from outside the `domain` package.
///
/// This compiles only because [ExternalWallet] is `non-sealed`. Try implementing
/// `PaymentMethod` directly from this package: the compiler rejects it with
/// *"class is not allowed to extend sealed class: PaymentMethod"*.
///
/// @param provider partner name
/// @param walletId partner's wallet identifier
public record PartnerWallet(@NonNull String provider, @NonNull String walletId) implements ExternalWallet {

    public PartnerWallet {
        if (provider.isBlank() || walletId.isBlank()) {
            throw new IllegalArgumentException("provider and walletId are required");
        }
    }
}

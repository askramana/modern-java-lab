package com.ajays.modernjava.payments.issuer;

import com.ajays.modernjava.payments.domain.DeclineReason;
import lombok.NonNull;

/// The issuer's answer to [IssuerClient#authorize(com.ajays.modernjava.payments.domain.Payment)].
///
/// A sealed result rather than an exception for declines: a decline is an expected business
/// outcome that every caller must handle, so the compiler should make them handle it.
/// Infrastructure failures stay exceptions ([IssuerException]).
public sealed interface AuthorizationResult {

    /// Funds reserved.
    ///
    /// @param authCode issuer authorization code
    record Approved(@NonNull String authCode) implements AuthorizationResult {}

    /// Issuer said no.
    record Declined(@NonNull DeclineReason reason) implements AuthorizationResult {}
}

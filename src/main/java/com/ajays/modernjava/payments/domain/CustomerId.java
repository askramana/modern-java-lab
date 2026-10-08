package com.ajays.modernjava.payments.domain;

import lombok.NonNull;

/// Strongly typed customer identifier. See [PaymentId] for why single-component records
/// are worth it.
///
/// @param value non-blank opaque identifier
public record CustomerId(@NonNull String value) {

    public CustomerId {
        if (value.isBlank()) {
            throw new IllegalArgumentException("CustomerId must not be blank");
        }
    }
}

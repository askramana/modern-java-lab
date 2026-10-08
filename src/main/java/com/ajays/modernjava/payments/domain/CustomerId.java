package com.ajays.modernjava.payments.domain;

/// Strongly typed customer identifier. See [PaymentId] for why single-component records
/// are worth it.
///
/// @param value non-blank opaque identifier
public record CustomerId(String value) {

    public CustomerId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("CustomerId must not be blank");
        }
    }
}

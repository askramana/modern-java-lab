package com.ajays.modernjava.payments.domain;

import java.util.UUID;
import lombok.NonNull;

/// Strongly typed identifier for a [Payment].
///
/// ## Pattern: single-component records as type-safe IDs
///
/// Before records, wrapping a `String` in its own type cost ~40 lines, so most code passed
/// raw strings and mixed up `paymentId` and `customerId` in method calls. A one-line record
/// makes the compiler catch that mistake at zero ceremony.
///
/// Today this is a reference type with an extra object header and a pointer hop. Project
/// Valhalla's value classes (JEP 401, preview in JDK 28) are designed to let a wrapper like
/// this be flattened to just its `String` field. That is one reason to adopt the pattern now.
///
/// @param value non-blank opaque identifier
public record PaymentId(@NonNull String value) {

    public PaymentId {
        if (value.isBlank()) {
            throw new IllegalArgumentException("PaymentId must not be blank");
        }
    }

    public static PaymentId newId() {
        return new PaymentId("pay_" + UUID.randomUUID());
    }
}

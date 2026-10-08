package com.ajays.modernjava.payments.ingest;

import com.ajays.modernjava.payments.ingest.Validated.Invalid;
import com.ajays.modernjava.payments.ingest.Validated.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;

/// Validates [PaymentRequest]s with Jakarta Validation and returns a [Validated] instead of
/// throwing, so callers are forced by the type system to handle the invalid case.
///
/// `Stream.toList()` (Java 16) replaces `collect(Collectors.toList())` and returns an
/// **unmodifiable** list. That's a behavioural difference: `Collectors.toList()` makes no
/// guarantee, but in practice returns a mutable `ArrayList`.
public final class PaymentRequestValidator {

    /// Thread-safe; build once. `ParameterMessageInterpolator` avoids needing Jakarta EL.
    private static final Validator VALIDATOR = Validation.byDefaultProvider()
            .configure()
            .messageInterpolator(new ParameterMessageInterpolator())
            .buildValidatorFactory()
            .getValidator();

    public Validated<PaymentRequest> validate(PaymentRequest request) {
        if (request == null) {
            return new Invalid<>(List.of("request: must not be null"));
        }
        var violations = VALIDATOR.validate(request).stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .distinct()   // a component constraint lands on field, accessor and parameter; report it once
                .sorted()
                .toList();
        return violations.isEmpty() ? new Valid<>(request) : new Invalid<>(violations);
    }
}

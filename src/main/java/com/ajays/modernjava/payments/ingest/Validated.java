package com.ajays.modernjava.payments.ingest;

import java.util.List;
import java.util.Objects;

/// Result of validating a value: either [Valid] with the value, or [Invalid] with every
/// problem found.
///
/// ## Generic sealed types and record-pattern inference
///
/// Sealed hierarchies work with generics. When you deconstruct, the type arguments are
/// **inferred** (JEP 440), so you don't repeat them:
///
/// ```
/// switch (validator.validate(request)) {           // Validated<PaymentRequest>
///     case Valid(var req)        -> ...;             // req is PaymentRequest
///     case Invalid(var problems) -> ...;             // problems is List<String>
/// }
/// ```
///
/// This is the Java equivalent of `Result`/`Either` in Rust, Scala or Kotlin, without a
/// library. Use it for **expected** failures that callers must handle. Keep exceptions for
/// bugs and infrastructure failures.
///
/// @param <T> type of the validated value
public sealed interface Validated<T> {

    /// The value passed every check.
    record Valid<T>(T value) implements Validated<T> {
        public Valid { Objects.requireNonNull(value, "value"); }
    }

    /// One or more checks failed.
    ///
    /// @param violations human-readable problems, e.g. `amount: must be greater than 0`
    record Invalid<T>(List<String> violations) implements Validated<T> {
        public Invalid {
            violations = List.copyOf(violations);
            if (violations.isEmpty()) {
                throw new IllegalArgumentException("Invalid requires at least one violation");
            }
        }
    }
}

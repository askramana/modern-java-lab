package com.ajays.modernjava.payments.ingest;

import com.ajays.modernjava.payments.domain.PaymentMethod;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Builder;

/// Inbound request to create a payment: a DTO at the system boundary.
///
/// ## Records as DTOs, with Jakarta Validation on the components
///
/// Annotations on a record component propagate to every place they're applicable: the
/// private field, the accessor, and the canonical constructor parameter. Hibernate
/// Validator (8.0+) understands records, so `validator.validate(request)` works as it would
/// on a bean. Jackson (2.12+) binds JSON to records through the canonical constructor.
///
/// ## Two kinds of validation, on purpose
///
/// | Where | Mechanism | Purpose |
/// |-------|-----------|---------|
/// | This DTO | Jakarta annotations, checked by [PaymentRequestValidator] | Collect **all** problems in user input and report them together |
/// | Domain records ([com.ajays.modernjava.payments.domain.Money], ...) | Compact constructors that throw | Make invalid domain objects **impossible to construct** |
///
/// A DTO may hold invalid data until it's validated; that's its job. A domain object may not.
/// That's why this record has no compact constructor.
///
/// ## Lombok `@Builder` on a record
///
/// Five components, one optional (`description`). Records have only the canonical
/// constructor, so optional components mean passing `null` positionally. `@Builder` gives
/// named, order-independent construction and still ends in the canonical constructor.
///
/// @param customerId  caller's customer identifier
/// @param amount      major units, up to 2 decimal places
/// @param currency    ISO-4217 code
/// @param method      payment method (already parsed)
/// @param description optional note shown on the receipt
@Builder
public record PaymentRequest(
        @NotBlank String customerId,
        @NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal amount,
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter ISO-4217 code") String currency,
        @NotNull PaymentMethod method,
        @Size(max = 140) String description) {
}

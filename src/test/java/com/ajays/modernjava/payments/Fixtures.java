package com.ajays.modernjava.payments;

import com.ajays.modernjava.payments.domain.CustomerId;
import com.ajays.modernjava.payments.domain.Money;
import com.ajays.modernjava.payments.domain.Payment;
import com.ajays.modernjava.payments.domain.PaymentId;
import com.ajays.modernjava.payments.domain.PaymentMethod.Card;
import com.ajays.modernjava.payments.domain.PaymentMethod.CardNetwork;
import com.ajays.modernjava.payments.domain.PaymentStatus.Initiated;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;

/// Shared test data. Note how Lombok's `@Builder(toBuilder = true)` on the `Payment` record
/// lets each test override only the component it cares about.
public final class Fixtures {

    public static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-08T10:00:00Z"), ZoneOffset.UTC);
    public static final CustomerId CUSTOMER = new CustomerId("cust-1");

    private Fixtures() {}

    public static Card domesticCard() {
        return new Card(CardNetwork.VISA, "4242", YearMonth.of(2028, 12), "IN");
    }

    public static Payment newPayment() {
        return Payment.builder()
                .id(new PaymentId("pay_test"))
                .customerId(CUSTOMER)
                .amount(Money.of("1000.00", "INR"))
                .method(domesticCard())
                .status(new Initiated(CLOCK.instant()))
                .build();
    }
}

package com.ajays.modernjava.payments.report;

import static com.ajays.modernjava.payments.Fixtures.CLOCK;
import static com.ajays.modernjava.payments.Fixtures.newPayment;
import static org.assertj.core.api.Assertions.assertThat;

import com.ajays.modernjava.payments.domain.Money;
import com.ajays.modernjava.payments.domain.PaymentCommand.Authorize;
import com.ajays.modernjava.payments.domain.PaymentCommand.Capture;
import com.ajays.modernjava.payments.domain.PaymentCommand.Refund;
import com.ajays.modernjava.payments.domain.PaymentMethod.Upi;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReceiptRendererTest {

    private final ReceiptRenderer renderer = new ReceiptRenderer();

    @Test
    void receiptForAuthorizedCardPayment() {
        var payment = newPayment().apply(new Authorize("AUTH777"), CLOCK);

        // A text block as the expected value: the test reads like the output.
        assertThat(renderer.receipt(payment)).isEqualTo("""
                ================================
                PAYMENT RECEIPT
                ================================
                Payment : pay_test
                Amount  : INR 1000.00
                Method  : VISA •••• 4242 (exp 2028-12)
                Status  : AUTHORIZED
                Auth    : AUTH777 (card ••4242)
                ================================
                """);
    }

    @Test
    void nestedPatternDoesNotMatchOtherMethods() {
        var upi = newPayment().withMethod(new Upi("ajay@okbank")).apply(new Authorize("AUTH1"), CLOCK);
        assertThat(renderer.receipt(upi)).contains("Auth    : -");
    }

    @Test
    void lineContinuationProducesSingleLineJson() {
        assertThat(renderer.toJson(newPayment())).isEqualTo(
                "{\"id\":\"pay_test\",\"customer\":\"cust-1\",\"amount\":\"1000.00\",\"currency\":\"INR\",\"status\":\"PENDING\"}");
    }

    @Test
    void settlementNetsRefundsPerCurrency() {
        var captured = newPayment().apply(new Authorize("A"), CLOCK).apply(new Capture(), CLOCK);
        var refunded = captured.apply(new Refund(Money.of("250", "INR")), CLOCK);
        var pending = newPayment();

        var lines = new SettlementReport().settle(List.of(captured, refunded, pending));

        assertThat(lines).singleElement().satisfies(line -> {
            assertThat(line.payments()).isEqualTo(2);
            assertThat(line.gross()).isEqualTo(Money.of("2000", "INR"));
            assertThat(line.refunded()).isEqualTo(Money.of("250", "INR"));
            assertThat(line.net()).isEqualTo(Money.of("1750", "INR"));
        });
    }
}

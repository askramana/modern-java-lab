package com.ajays.modernjava.payments;

import static com.ajays.modernjava.payments.Fixtures.CLOCK;
import static com.ajays.modernjava.payments.Fixtures.CUSTOMER;
import static com.ajays.modernjava.payments.Fixtures.domesticCard;
import static org.assertj.core.api.Assertions.assertThat;

import com.ajays.modernjava.payments.PaymentGateway.Outcome.Processed;
import com.ajays.modernjava.payments.PaymentGateway.Outcome.Rejected;
import com.ajays.modernjava.payments.domain.PaymentStatus.Authorized;
import com.ajays.modernjava.payments.domain.PaymentStatus.Declined;
import com.ajays.modernjava.payments.domain.PaymentStatus.Initiated;
import com.ajays.modernjava.payments.domain.PaymentMethod.BankTransfer;
import com.ajays.modernjava.payments.domain.PaymentMethod.Upi;
import com.ajays.modernjava.payments.ingest.PaymentRequest;
import com.ajays.modernjava.payments.ingest.PaymentRequestValidator;
import com.ajays.modernjava.payments.risk.CustomerProfile;
import com.ajays.modernjava.payments.risk.RiskDecision.Review;
import com.ajays.modernjava.payments.risk.RiskEngine;
import com.ajays.modernjava.payments.risk.RiskPolicy;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PaymentGatewayTest {

    private final PaymentGateway gateway =
            new PaymentGateway(new PaymentRequestValidator(), new RiskEngine(RiskPolicy.defaults(), CLOCK), CLOCK);
    private final CustomerProfile regular = new CustomerProfile(CUSTOMER, "IN", 400, 0);

    private PaymentRequest.PaymentRequestBuilder valid() {
        return PaymentRequest.builder()
                .customerId("cust-1")
                .amount(new BigDecimal("1200.00"))
                .currency("INR")
                .method(domesticCard());
    }

    @Test
    void approvedRequestIsAuthorized() {
        var outcome = gateway.submit(valid().build(), regular);
        assertThat(outcome).isInstanceOfSatisfying(Processed.class,
                p -> assertThat(p.payment().status()).isInstanceOf(Authorized.class));
    }

    @Test
    void jakartaValidationCollectsEveryViolation() {
        var bad = valid().customerId(" ").amount(new BigDecimal("-5.123")).currency("rupees").method(null).build();

        // Record pattern straight on the outcome: test it's Rejected and bind its list in one go.
        if (gateway.submit(bad, regular) instanceof Rejected(var violations)) {
            assertThat(violations).containsExactly(
                    "amount: must be greater than 0",
                    "amount: numeric value out of bounds (<12 digits>.<2 digits> expected)",
                    "currency: must be a 3-letter ISO-4217 code",
                    "customerId: must not be blank",
                    "method: must not be null");
        } else {
            throw new AssertionError("expected Rejected");
        }
    }

    @Test
    void reviewLeavesPaymentInitiated() {
        var busy = new CustomerProfile(CUSTOMER, "IN", 400, 50);
        var outcome = gateway.submit(valid().method(new Upi("ajay@okbank")).build(), busy);
        assertThat(outcome).isInstanceOfSatisfying(Processed.class, p -> {
            assertThat(p.decision()).isInstanceOf(Review.class);
            assertThat(p.payment().status()).isInstanceOf(Initiated.class);
        });
    }

    @Test
    void riskRejectionDeclinesPayment() {
        var fresh = new CustomerProfile(CUSTOMER, "IN", 1, 0);
        var request = valid().method(new BankTransfer("SBIN0000001", "1234")).amount(new BigDecimal("500000")).build();
        var outcome = gateway.submit(request, fresh);
        assertThat(outcome).isInstanceOfSatisfying(Processed.class,
                p -> assertThat(p.payment().status()).isInstanceOf(Declined.class));
    }
}

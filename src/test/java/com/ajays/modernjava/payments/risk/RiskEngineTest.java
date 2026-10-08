package com.ajays.modernjava.payments.risk;

import static com.ajays.modernjava.payments.Fixtures.CLOCK;
import static com.ajays.modernjava.payments.Fixtures.CUSTOMER;
import static com.ajays.modernjava.payments.Fixtures.newPayment;
import static org.assertj.core.api.Assertions.assertThat;

import com.ajays.modernjava.payments.domain.DeclineReason;
import com.ajays.modernjava.payments.domain.Money;
import com.ajays.modernjava.payments.domain.PaymentMethod.BankTransfer;
import com.ajays.modernjava.payments.domain.PaymentMethod.Card;
import com.ajays.modernjava.payments.domain.PaymentMethod.CardNetwork;
import com.ajays.modernjava.payments.domain.PaymentMethod.Upi;
import com.ajays.modernjava.payments.risk.RiskDecision.Approve;
import com.ajays.modernjava.payments.risk.RiskDecision.Reject;
import com.ajays.modernjava.payments.risk.RiskDecision.Review;
import com.ajays.modernjava.payments.wallets.PartnerWallet;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class RiskEngineTest {

    private final RiskEngine engine = new RiskEngine(RiskPolicy.defaults(), CLOCK);
    private final CustomerProfile regular = new CustomerProfile(CUSTOMER, "IN", 400, 1);

    @Test
    void approvesOrdinaryDomesticCard() {
        assertThat(engine.evaluate(newPayment(), regular)).isEqualTo(new Approve());
    }

    @Test
    void rejectsExpiredCard() {
        var expired = new Card(CardNetwork.MASTERCARD, "1111", YearMonth.of(2026, 9), "IN");
        var decision = engine.evaluate(newPayment().withMethod(expired), regular);
        assertThat(decision).isInstanceOfSatisfying(Reject.class,
                r -> assertThat(r.reason()).isEqualTo(DeclineReason.EXPIRED_INSTRUMENT));
    }

    @Test
    void cardValidThroughCurrentMonthIsNotExpired() {
        var endsThisMonth = new Card(CardNetwork.RUPAY, "1111", YearMonth.of(2026, 10), "IN");
        assertThat(engine.evaluate(newPayment().withMethod(endsThisMonth), regular)).isEqualTo(new Approve());
    }

    @Test
    void reviewsLargeCrossBorderCard() {
        var foreign = new Card(CardNetwork.AMEX, "0005", YearMonth.of(2029, 1), "US");
        var payment = newPayment().withMethod(foreign).withAmount(Money.of("75000", "INR"));
        assertThat(engine.evaluate(payment, regular)).isInstanceOf(Review.class);
        // below the threshold the same card is fine
        assertThat(engine.evaluate(newPayment().withMethod(foreign), regular)).isEqualTo(new Approve());
    }

    @Test
    void reviewsUpiVelocity() {
        var busy = new CustomerProfile(CUSTOMER, "IN", 400, 12);
        var decision = engine.evaluate(newPayment().withMethod(new Upi("ajay@okbank")), busy);
        assertThat(decision).isEqualTo(new Review("UPI velocity 12/h via @okbank"));
    }

    @Test
    void rejectsLargeTransferFromNewAccount() {
        var fresh = new CustomerProfile(CUSTOMER, "IN", 2, 0);
        var payment = newPayment().withMethod(new BankTransfer("HDFC0001234", "9876")).withAmount(Money.of("200000", "INR"));
        assertThat(engine.evaluate(payment, fresh)).isInstanceOf(Reject.class);
    }

    @Test
    void nonSealedBranchHandlesUnknownWallets() {
        var trusted = newPayment().withMethod(new PartnerWallet("acmepay", "w-1"));
        var unknown = newPayment().withMethod(new PartnerWallet("shadywallet", "w-2"));
        assertThat(engine.evaluate(trusted, regular)).isEqualTo(new Approve());
        assertThat(engine.evaluate(unknown, regular)).isInstanceOf(Review.class);
    }
}

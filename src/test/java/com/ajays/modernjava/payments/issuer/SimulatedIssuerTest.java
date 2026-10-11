package com.ajays.modernjava.payments.issuer;

import static com.ajays.modernjava.payments.Fixtures.newPayment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ajays.modernjava.payments.context.RequestContext;
import com.ajays.modernjava.payments.domain.DeclineReason;
import com.ajays.modernjava.payments.domain.Money;
import com.ajays.modernjava.payments.issuer.AuthorizationResult.Approved;
import com.ajays.modernjava.payments.issuer.AuthorizationResult.Declined;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class SimulatedIssuerTest {

    private final SimulatedIssuer issuer = new SimulatedIssuer(Duration.ZERO, new BigDecimal("5000"));

    @Test
    void sameCorrelationIdGetsTheSameAnswer() {
        var context = new RequestContext("corr-1", "merchant-1");
        var first = context.call(() -> issuer.authorize(newPayment()));
        var retry = context.call(() -> issuer.authorize(newPayment()));
        assertThat(first).isInstanceOf(Approved.class).isEqualTo(retry);
    }

    @Test
    void declinesAboveTheAvailableBalance() {
        var big = newPayment().withAmount(Money.of("5000.01", "INR"));
        var result = new RequestContext("corr-2", "merchant-1").call(() -> issuer.authorize(big));
        assertThat(result).isEqualTo(new Declined(DeclineReason.INSUFFICIENT_FUNDS));
    }

    @Test
    void refusesToRunOutsideARequest() {
        assertThatThrownBy(() -> issuer.authorize(newPayment())).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void contendedSynchronizedLedgerFromManyVirtualThreads() {
        // 10 000 virtual threads contend for the same monitor. Since JDK 24 (JEP 491) waiting for a
        // monitor unmounts the virtual thread instead of pinning its carrier.
        var slow = new SimulatedIssuer(Duration.ofMillis(10));
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            IntStream.range(0, 10_000).forEach(i -> executor.submit(() ->
                    new RequestContext("corr-" + i, "merchant-1").run(() -> slow.capture(newPayment()))));
        }
        assertThat(slow.captureCount()).isEqualTo(10_000);
    }

    @Test
    void interruptionKeepsTheFlagAndFailsTheCall() throws Exception {
        var slow = new SimulatedIssuer(Duration.ofSeconds(30));
        var failure = new AtomicReference<IssuerException>();
        var stillInterrupted = new AtomicBoolean();
        var thread = Thread.ofVirtual().start(() -> {
            try {
                new RequestContext("corr-3", "merchant-1").run(() -> slow.capture(newPayment()));
            } catch (IssuerException e) {
                failure.set(e);
                stillInterrupted.set(Thread.currentThread().isInterrupted());
            }
        });
        thread.interrupt();
        assertThat(thread.join(Duration.ofSeconds(5))).isTrue();
        assertThat(failure.get()).hasMessageContaining("Interrupted");
        assertThat(stillInterrupted).isTrue();
        assertThat(slow.captureCount()).isZero();
    }
}

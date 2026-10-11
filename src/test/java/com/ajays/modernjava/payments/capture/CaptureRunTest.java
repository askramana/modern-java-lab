package com.ajays.modernjava.payments.capture;

import static com.ajays.modernjava.payments.Fixtures.CLOCK;
import static com.ajays.modernjava.payments.Fixtures.newPayment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ajays.modernjava.payments.context.RequestContext;
import com.ajays.modernjava.payments.domain.IllegalTransitionException;
import com.ajays.modernjava.payments.domain.Payment;
import com.ajays.modernjava.payments.domain.PaymentCommand.Authorize;
import com.ajays.modernjava.payments.domain.PaymentId;
import com.ajays.modernjava.payments.domain.PaymentStatus.Captured;
import com.ajays.modernjava.payments.issuer.AuthorizationResult;
import com.ajays.modernjava.payments.issuer.IssuerClient;
import com.ajays.modernjava.payments.issuer.IssuerException;
import com.ajays.modernjava.payments.issuer.SimulatedIssuer;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class CaptureRunTest {

    private final RequestContext run = new RequestContext("eod-2026-10-08", "merchant-1");

    private static List<Payment> authorized(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> newPayment().withId(new PaymentId("pay_" + i)).apply(new Authorize("AUTH" + i), CLOCK))
                .toList();
    }

    @Test
    void capturesThousandsOfBlockingCallsOnVirtualThreads() {
        var issuer = new SimulatedIssuer(Duration.ofMillis(100));
        var payments = authorized(5_000);

        long start = System.nanoTime();
        var result = run.call(() -> new CaptureRun(issuer, CLOCK, 5_000).captureAll(payments));
        var elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(result.failed()).isEmpty();
        assertThat(result.captured()).hasSize(5_000).allSatisfy(p -> assertThat(p.status()).isInstanceOf(Captured.class));
        assertThat(issuer.captureCount()).isEqualTo(5_000);
        // 5 000 × 100 ms sequentially is 500 s; on 200 platform threads 2.5 s. Here: all at once.
        assertThat(elapsed).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    void semaphoreBoundsCallsInFlight() {
        var issuer = new GaugedIssuer(Duration.ofMillis(20));
        var result = run.call(() -> new CaptureRun(issuer, CLOCK, 25).captureAll(authorized(500)));

        assertThat(result.captured()).hasSize(500);
        assertThat(issuer.maxInFlight.get()).isLessThanOrEqualTo(25).isGreaterThan(1);
    }

    @Test
    void eachCaptureRunsOnItsOwnNamedVirtualThreadWithItsOwnContext() {
        var issuer = new GaugedIssuer(Duration.ZERO);
        run.run(() -> new CaptureRun(issuer, CLOCK, 10).captureAll(authorized(3)));

        assertThat(issuer.threads).allSatisfy(t -> assertThat(t).startsWith("capture-").endsWith("virtual=true"));
        assertThat(issuer.correlationIds)
                .containsExactlyInAnyOrder("eod-2026-10-08/pay_0", "eod-2026-10-08/pay_1", "eod-2026-10-08/pay_2");
    }

    @Test
    void oneFailureDoesNotStopTheOthers() {
        var flaky = new GaugedIssuer(Duration.ZERO) {
            @Override
            public void capture(Payment payment) {
                if (payment.id().value().equals("pay_1")) {
                    throw new IssuerException("issuer timeout");
                }
                super.capture(payment);
            }
        };
        var result = run.call(() -> new CaptureRun(flaky, CLOCK, 10).captureAll(authorized(3)));

        assertThat(result.captured()).extracting(p -> p.id().value()).containsExactly("pay_0", "pay_2");
        assertThat(result.failed()).singleElement().satisfies(f -> {
            assertThat(f.payment().id().value()).isEqualTo("pay_1");
            assertThat(f.cause()).isInstanceOf(IssuerException.class).hasMessage("issuer timeout");
        });
    }

    @Test
    void paymentsNotYetAuthorizedFailBeforeReachingTheIssuer() {
        var issuer = new GaugedIssuer(Duration.ZERO);
        var result = run.call(() -> new CaptureRun(issuer, CLOCK, 10).captureAll(List.of(newPayment())));

        assertThat(result.failed()).singleElement()
                .satisfies(f -> assertThat(f.cause()).isInstanceOf(IllegalTransitionException.class));
        assertThat(issuer.calls.get()).isZero();
    }

    @Test
    void requiresARunContext() {
        var captureRun = new CaptureRun(new GaugedIssuer(Duration.ZERO), CLOCK, 1);
        assertThatThrownBy(() -> captureRun.captureAll(authorized(1))).hasMessageContaining("No RequestContext bound");
    }

    /// Records what the issuer saw: concurrency, thread names and idempotency keys.
    private static class GaugedIssuer implements IssuerClient {
        final Duration latency;
        final AtomicInteger inFlight = new AtomicInteger();
        final AtomicInteger maxInFlight = new AtomicInteger();
        final AtomicInteger calls = new AtomicInteger();
        final Set<String> threads = ConcurrentHashMap.newKeySet();
        final Set<String> correlationIds = ConcurrentHashMap.newKeySet();

        GaugedIssuer(Duration latency) {
            this.latency = latency;
        }

        @Override
        public AuthorizationResult authorize(Payment payment) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void capture(Payment payment) {
            calls.incrementAndGet();
            var current = Thread.currentThread();
            threads.add(current.getName() + " virtual=" + current.isVirtual());
            correlationIds.add(RequestContext.current().correlationId());
            maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            try {
                Thread.sleep(latency);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IssuerException("interrupted");
            } finally {
                inFlight.decrementAndGet();
            }
        }
    }
}

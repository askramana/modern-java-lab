package com.ajays.modernjava.preview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ajays.modernjava.payments.context.RequestContext;
import com.ajays.modernjava.payments.domain.CustomerId;
import com.ajays.modernjava.payments.risk.CustomerProfile;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope.CancelledByTimeoutException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ParallelRiskSignalsTest {

    private static final CustomerId CUSTOMER = new CustomerId("cust-1");

    /// Each source sleeps `latency` and records which context and thread it ran with.
    private static class SlowSources implements ParallelRiskSignals.Sources {
        final Duration latency;
        final Set<String> seen = ConcurrentHashMap.newKeySet();

        SlowSources(Duration latency) {
            this.latency = latency;
        }

        private void call(String source) throws InterruptedException {
            seen.add(source + " " + RequestContext.current().correlationId() + " virtual=" + Thread.currentThread().isVirtual());
            Thread.sleep(latency);
        }

        @Override
        public String homeCountry(CustomerId id) throws Exception {
            call("kyc");
            return "IN";
        }

        @Override
        public int accountAgeDays(CustomerId id) throws Exception {
            call("accounts");
            return 400;
        }

        @Override
        public int paymentsInLastHour(CustomerId id) throws Exception {
            call("velocity");
            return 3;
        }
    }

    @Test
    void callsAllSourcesConcurrentlyWithTheCallersContext() throws Exception {
        var sources = new SlowSources(Duration.ofMillis(300));
        var signals = new ParallelRiskSignals(sources, Duration.ofSeconds(5));

        long start = System.nanoTime();
        var profile = new RequestContext("corr-7", "merchant-1").call(() -> signals.profileFor(CUSTOMER));
        var elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(profile).isEqualTo(new CustomerProfile(CUSTOMER, "IN", 400, 3));
        assertThat(elapsed).isLessThan(Duration.ofMillis(800));   // ~300 ms, not 900 ms
        // Forked subtasks inherited the scoped value: no rebinding needed, unlike an executor.
        assertThat(sources.seen).containsExactlyInAnyOrder(
                "kyc corr-7 virtual=true", "accounts corr-7 virtual=true", "velocity corr-7 virtual=true");
    }

    @Test
    void firstFailureCancelsTheOtherSubtasks() {
        var slowSourceInterrupted = new CountDownLatch(1);
        var sources = new SlowSources(Duration.ZERO) {
            @Override
            public String homeCountry(CustomerId id) {
                throw new IllegalStateException("KYC service down");
            }

            @Override
            public int accountAgeDays(CustomerId id) throws Exception {
                try {
                    Thread.sleep(Duration.ofSeconds(30));
                    return 400;
                } catch (InterruptedException e) {
                    slowSourceInterrupted.countDown();   // cancelled by the scope
                    throw e;
                }
            }
        };
        var signals = new ParallelRiskSignals(sources, Duration.ofSeconds(60));

        assertThatThrownBy(() -> new RequestContext("corr-8", "merchant-1").call(() -> signals.profileFor(CUSTOMER)))
                .isInstanceOf(ExecutionException.class)
                .cause().hasMessage("KYC service down");
        // close() waited for the slow subtask to finish, so it was interrupted before we got here.
        assertThat(slowSourceInterrupted.getCount()).isZero();
    }

    @Test
    void timeoutCancelsTheScope() throws Exception {
        var signals = new ParallelRiskSignals(new SlowSources(Duration.ofSeconds(30)), Duration.ofMillis(100));

        long start = System.nanoTime();
        assertThatThrownBy(() -> new RequestContext("corr-9", "merchant-1").call(() -> signals.profileFor(CUSTOMER)))
                .isInstanceOf(ExecutionException.class)
                .cause().isInstanceOf(CancelledByTimeoutException.class);
        assertThat(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start)).isLessThan(5);
    }
}

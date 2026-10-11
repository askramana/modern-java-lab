package com.ajays.modernjava.preview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ajays.modernjava.preview.FxQuotes.FxQuote;
import com.ajays.modernjava.preview.FxQuotes.FxUnavailableException;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Currency;
import java.util.List;
import java.util.concurrent.StructuredTaskScope.CancelledByTimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class FxQuotesTest {

    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency INR = Currency.getInstance("INR");

    /// A provider that answers after `latency`, or fails if `rate` is null.
    record StubProvider(String name, Duration latency, BigDecimal rate, AtomicBoolean interrupted) implements FxQuotes.Provider {

        StubProvider(String name, Duration latency, BigDecimal rate) {
            this(name, latency, rate, new AtomicBoolean());
        }

        @Override
        public BigDecimal rate(Currency from, Currency to) throws Exception {
            try {
                Thread.sleep(latency);
            } catch (InterruptedException e) {
                interrupted.set(true);
                throw e;
            }
            if (rate == null) {
                throw new IOException(name + " unavailable");
            }
            return rate;
        }
    }

    @Test
    void fastestQuoteWinsAndTheSlowerCallsAreCancelled() throws Exception {
        var slow = new StubProvider("slowfx", Duration.ofSeconds(30), new BigDecimal("83.10"));
        var fast = new StubProvider("fastfx", Duration.ofMillis(50), new BigDecimal("83.25"));

        var quote = new FxQuotes(List.of(slow, fast), Duration.ofSeconds(5)).fastest(USD, INR);

        assertThat(quote).isEqualTo(new FxQuote(USD, INR, new BigDecimal("83.25"), "fastfx"));
        assertThat(slow.interrupted()).isTrue();   // the scope was closed only after the loser stopped
    }

    @Test
    void aFailingProviderDoesNotMatterIfAnotherSucceeds() throws Exception {
        var broken = new StubProvider("brokenfx", Duration.ZERO, null);
        var ok = new StubProvider("okfx", Duration.ofMillis(50), new BigDecimal("83.20"));

        assertThat(new FxQuotes(List.of(broken, ok), Duration.ofSeconds(5)).fastest(USD, INR).provider()).isEqualTo("okfx");
    }

    @Test
    void allFailingThrowsOurOwnExceptionType() {
        var quotes = new FxQuotes(List.of(
                new StubProvider("a", Duration.ZERO, null),
                new StubProvider("b", Duration.ZERO, null)), Duration.ofSeconds(5));

        // JDK 27: the joiner's exception function sets what join() throws. No ExecutionException.
        assertThatThrownBy(() -> quotes.fastest(USD, INR))
                .isExactlyInstanceOf(FxUnavailableException.class)
                .hasMessage("No FX quote for USD/INR")
                .cause().isInstanceOf(IOException.class);
    }

    @Test
    void timeoutAlsoSurfacesAsOurExceptionType() {
        var quotes = new FxQuotes(List.of(new StubProvider("slowfx", Duration.ofSeconds(30), BigDecimal.ONE)), Duration.ofMillis(50));

        assertThatThrownBy(() -> quotes.fastest(USD, INR))
                .isExactlyInstanceOf(FxUnavailableException.class)
                .cause().isInstanceOf(CancelledByTimeoutException.class);
    }
}

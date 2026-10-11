package com.ajays.modernjava.payments.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class RequestContextTest {

    private final RequestContext outer = new RequestContext("corr-outer", "merchant-1");

    @Test
    void boundOnlyForTheDurationOfTheCall() {
        assertThat(RequestContext.isBound()).isFalse();
        var seen = outer.call(() -> RequestContext.current().correlationId());
        assertThat(seen).isEqualTo("corr-outer");
        assertThat(RequestContext.isBound()).isFalse();   // nothing to clean up, nothing leaks
    }

    @Test
    void nestedBindingShadowsThenRestores() {
        outer.run(() -> {
            new RequestContext("corr-inner", "merchant-1")
                    .run(() -> assertThat(RequestContext.current().correlationId()).isEqualTo("corr-inner"));
            assertThat(RequestContext.current().correlationId()).isEqualTo("corr-outer");
        });
    }

    @Test
    void checkedExceptionsPassThroughWithTheirOwnType() {
        // CallableOp<T, X> carries the exception type, so no wrapping in RuntimeException.
        assertThatThrownBy(() -> outer.call(() -> { throw new IOException("issuer socket closed"); }))
                .isExactlyInstanceOf(IOException.class);
    }

    @Test
    void unboundReadFailsWithAClearMessage() {
        assertThatThrownBy(RequestContext::current)
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("No RequestContext bound");
    }

    @Test
    void executorTasksDoNotInheritTheBinding() throws Exception {
        // The trap CaptureRun works around: a plain executor's threads are not children of the scope.
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var boundInTask = outer.call(() -> executor.submit(RequestContext::isBound).get());
            assertThat(boundInTask).isFalse();
        }
    }

    @Test
    void nullsAndBlanksAreRejected() {
        assertThatThrownBy(() -> new RequestContext(null, "m")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RequestContext(" ", "m")).isInstanceOf(IllegalArgumentException.class);
    }
}

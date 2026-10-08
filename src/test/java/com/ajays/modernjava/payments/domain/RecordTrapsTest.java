package com.ajays.modernjava.payments.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ajays.modernjava.payments.risk.RiskDecision.Review;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/// Executable versions of the record traps from Lesson 1.
class RecordTrapsTest {

    /// Nested records are implicitly `static`. Handy for test-only shapes.
    record NaiveTeam(String name, List<String> members) {}

    record Digest(byte[] bytes) {}

    @Test
    void recordsAreOnlyShallowlyImmutable() {
        var members = new ArrayList<>(List.of("a", "b"));
        var team = new NaiveTeam("core", members);
        members.add("mallory");
        assertThat(team.members()).contains("mallory");   // leaked mutation
    }

    @Test
    void defensiveCopyInCompactConstructorFixesIt() {
        var reasons = new ArrayList<>(List.of("velocity"));
        var review = new Review(reasons);
        reasons.add("tampered");
        assertThat(review.reasons()).containsExactly("velocity");
        assertThatThrownBy(() -> review.reasons().add("x")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void arrayComponentsBreakEquals() {
        var a = new Digest(new byte[] {1, 2});
        var b = new Digest(new byte[] {1, 2});
        assertThat(a).isNotEqualTo(b);                       // arrays use identity equality
        assertThat(Arrays.equals(a.bytes(), b.bytes())).isTrue();
    }

    @Test
    void accessorsAreNotJavaBeanGetters() throws NoSuchMethodException {
        assertThat(Money.class.getMethod("amount")).isNotNull();
        assertThatThrownBy(() -> Money.class.getMethod("getAmount")).isInstanceOf(NoSuchMethodException.class);
    }

    @Test
    void recordReflectionExposesComponents() {
        assertThat(Payment.class.isRecord()).isTrue();
        assertThat(Arrays.stream(Payment.class.getRecordComponents()).map(c -> c.getName()))
                .containsExactly("id", "customerId", "amount", "method", "status");
    }
}

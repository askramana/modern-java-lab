package com.ajays.modernjava.preview;

import static org.assertj.core.api.Assertions.assertThat;

import com.ajays.modernjava.preview.PrimitivePatterns.Reading;
import org.junit.jupiter.api.Test;

class PrimitivePatternsTest {

    @Test
    void instanceofIntTestsForLosslessNarrowing() {
        assertThat(PrimitivePatterns.toIntExactly(42L)).hasValue(42);
        assertThat(PrimitivePatterns.toIntExactly(3_000_000_000L)).isEmpty();   // a cast would give -1294967296
    }

    @Test
    void primitiveSwitchesWithGuards() {
        assertThat(PrimitivePatterns.riskBand(0)).isEqualTo("NO_SIGNAL");
        assertThat(PrimitivePatterns.riskBand(95)).isEqualTo("BLOCK");
        assertThat(PrimitivePatterns.riskBand(70)).isEqualTo("REVIEW");
        assertThat(PrimitivePatterns.riskBand(10)).isEqualTo("ALLOW");

        assertThat(PrimitivePatterns.fxRateSanity(Double.NaN)).isEqualTo("INVALID");
        assertThat(PrimitivePatterns.fxRateSanity(83.2)).isEqualTo("OK");
        assertThat(PrimitivePatterns.flag(true)).isEqualTo("ON");
    }

    @Test
    void recordPatternNarrowsComponent() {
        assertThat(PrimitivePatterns.describe(new Reading(7.0))).isEqualTo("whole number 7");
        assertThat(PrimitivePatterns.describe(new Reading(7.5))).isEqualTo("fractional 7.5");
    }
}

package com.ajays.modernjava.payments.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void bigDecimalEqualsIsScaleSensitiveButMoneyIsNot() {
        assertThat(new BigDecimal("10.0")).isNotEqualTo(new BigDecimal("10.00"));   // the trap
        assertThat(Money.of("10.0", "INR")).isEqualTo(Money.of("10.00", "INR"));     // fixed by normalization
        assertThat(Set.of(Money.of("10", "INR"))).contains(Money.of("10.00", "INR"));
    }

    @Test
    void normalizesToCurrencyMinorUnits() {
        assertThat(Money.of("5", "JPY").amount().scale()).isZero();
        assertThat(Money.of("5", "BHD").amount().scale()).isEqualTo(3);
    }

    @Test
    void compactConstructorRejectsInvalidValues() {
        assertThatThrownBy(() -> Money.of("-1", "INR")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.of("10.005", "INR")).isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> new Money(null, Currency.getInstance("INR"))).isInstanceOf(NullPointerException.class);
    }

    @Test
    void arithmeticRequiresSameCurrency() {
        assertThat(Money.of("10", "INR").plus(Money.of("5.50", "INR"))).isEqualTo(Money.of("15.5", "INR"));
        assertThatThrownBy(() -> Money.of("10", "INR").plus(Money.of("1", "USD")))
                .hasMessageContaining("Currency mismatch");
    }

    @Test
    void recordsCanBeDeconstructed() {
        Object value = Money.of("99.99", "USD");
        if (value instanceof Money(var amount, var currency)) {
            assertThat(amount).isEqualByComparingTo("99.99");
            assertThat(currency.getCurrencyCode()).isEqualTo("USD");
        } else {
            throw new AssertionError("pattern should match");
        }
    }
}

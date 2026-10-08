package com.ajays.modernjava.payments.risk;

import com.ajays.modernjava.payments.domain.CustomerId;
import lombok.NonNull;

/// Risk-relevant facts about a customer at evaluation time.
///
/// A record used as a read-only *snapshot*. In Java 8 this would have been a mutable bean
/// or a `Map<String, Object>` of "signals".
///
/// @param customerId         who
/// @param homeCountry        ISO alpha-2 country of residence
/// @param accountAgeDays     days since sign-up
/// @param paymentsInLastHour velocity signal
public record CustomerProfile(@NonNull CustomerId customerId, @NonNull String homeCountry, int accountAgeDays, int paymentsInLastHour) {

    public CustomerProfile {
        if (accountAgeDays < 0 || paymentsInLastHour < 0) {
            throw new IllegalArgumentException("counters must be non-negative");
        }
    }
}

package com.ajays.modernjava.payments.report;

import module java.base;

import com.ajays.modernjava.payments.domain.Money;
import com.ajays.modernjava.payments.domain.Payment;
import com.ajays.modernjava.payments.domain.PaymentStatus.Authorized;
import com.ajays.modernjava.payments.domain.PaymentStatus.Captured;
import com.ajays.modernjava.payments.domain.PaymentStatus.Declined;
import com.ajays.modernjava.payments.domain.PaymentStatus.Initiated;
import com.ajays.modernjava.payments.domain.PaymentStatus.Refunded;

/// End-of-day settlement totals per currency.
///
/// ## Feature: module import declarations (JEP 511, final in Java 25)
///
/// `import module java.base;` imports every public top-level type in every package the
/// module exports: `java.util`, `java.util.stream`, `java.math`, `java.time` and so on.
/// You don't need a `module-info.java`; this project is on the classpath.
///
/// Rules worth knowing:
///
/// - **Ambiguity is a compile error at the point of use.** `import module java.base;` plus
///   `import module java.desktop;` makes `List` ambiguous (`java.util.List` vs
///   `java.awt.List`). Fix it with a single-type import (`import java.util.List;`), which
///   always shadows module imports.
/// - It imports *types*, not static members, and not packages a module doesn't export.
/// - Compact source files (see `scripts/AmberTour.java`) import `java.base` implicitly.
///
/// **Team guidance:** it's excellent for scripts, tests, prototypes and teaching. In large
/// production codebases most teams will keep explicit imports managed by the IDE, because
/// they show at a glance which APIs a file depends on. This file uses it as a demo.
///
/// ## Feature: local records (Java 16)
///
/// [#settle(List)] declares `record Settled(...)` *inside the method*. It exists only to
/// carry an intermediate result between stream stages. Before records, people abused
/// `Map.Entry`, `Pair<A, B>` or `Object[]` for this. Local records are implicitly `static`,
/// so they can't capture local variables or `this`.
///
/// ## Bonus: `Stream.mapMulti` (Java 16)
///
/// `mapMulti` combines filter and map without allocating a stream per element as
/// `flatMap` does. It pairs well with an exhaustive `switch` that emits zero or one results.
public final class SettlementReport {

    /// One line of the report.
    ///
    /// @param currency settlement currency
    /// @param payments number of captured or refunded payments
    /// @param gross    total captured
    /// @param refunded total refunded
    /// @param net      gross minus refunded
    public record Line(Currency currency, long payments, Money gross, Money refunded, Money net) {}

    public List<Line> settle(List<Payment> payments) {
        record Settled(Currency currency, Money gross, Money refunded) {}

        Map<Currency, List<Settled>> byCurrency = payments.stream()
                .<Settled>mapMulti((payment, sink) -> {
                    var zero = Money.zero(payment.amount().currency());
                    switch (payment.status()) {
                        case Captured _ -> sink.accept(new Settled(payment.amount().currency(), payment.amount(), zero));
                        case Refunded(var refunded, _) ->
                                sink.accept(new Settled(payment.amount().currency(), payment.amount(), refunded));
                        case Initiated _, Authorized _, Declined _ -> { }   // not settled
                    }
                })
                .collect(Collectors.groupingBy(Settled::currency));

        return byCurrency.entrySet().stream()
                .map(entry -> {
                    var zero = Money.zero(entry.getKey());
                    var gross = entry.getValue().stream().map(Settled::gross).reduce(zero, Money::plus);
                    var refunded = entry.getValue().stream().map(Settled::refunded).reduce(zero, Money::plus);
                    return new Line(entry.getKey(), entry.getValue().size(), gross, refunded, gross.minus(refunded));
                })
                .sorted(Comparator.comparing((Line line) -> line.currency().getCurrencyCode()))
                .toList();
    }
}

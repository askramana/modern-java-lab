/// # Amber tour: a compact source file
///
/// ## Feature: compact source files and instance `main` methods (JEP 512, final in Java 25)
///
/// This file has **no class declaration**. The compiler wraps the top-level methods and
/// fields in an implicitly declared final class. Also:
///
/// - `main` needs no `public`, no `static`, and no `String[] args`.
/// - `java.base` is imported automatically (as if by `import module java.base;`), so
///   `List`, `Map`, `Clock`, `BigDecimal` and others need no imports.
/// - `IO.println` / `IO.readln` (class `java.lang.IO`) replace `System.out.println` for
///   simple console I/O.
///
/// Run it straight from source with the compiled project on the classpath:
///
/// ```
/// mvn -q compile
/// java -cp target/classes scripts/AmberTour.java
/// ```
///
/// The source launcher (JEP 330, Java 11; multi-file since JEP 458, Java 22) compiles in
/// memory and runs; no build step is needed for the script itself. This is the "on-ramp"
/// philosophy: scripts and first programs without the ceremony, and the same file can grow
/// into a normal class later.

import com.ajays.modernjava.payments.domain.CustomerId;
import com.ajays.modernjava.payments.domain.DeclineReason;
import com.ajays.modernjava.payments.domain.Money;
import com.ajays.modernjava.payments.domain.Payment;
import com.ajays.modernjava.payments.domain.PaymentCommand;
import com.ajays.modernjava.payments.domain.PaymentMethod.CardNetwork;
import com.ajays.modernjava.payments.domain.PaymentMethod.Card;
import com.ajays.modernjava.payments.domain.PaymentMethod.Upi;
import com.ajays.modernjava.payments.report.ReceiptRenderer;
import com.ajays.modernjava.payments.report.SettlementReport;

final Clock clock = Clock.systemUTC();
final ReceiptRenderer renderer = new ReceiptRenderer();

void main() {
    var customer = new CustomerId("cust-42");
    var card = new Card(CardNetwork.RUPAY, "4242", YearMonth.now(clock).plusYears(2), "IN");

    var captured = Payment.initiate(customer, Money.of("1499.50", "INR"), card, clock)
            .apply(new PaymentCommand.Authorize("AUTH123456"), clock)
            .apply(new PaymentCommand.Capture(), clock);
    var refunded = captured.apply(new PaymentCommand.Refund(Money.of("499.50", "INR")), clock);
    var declined = Payment.initiate(customer, Money.of("250", "INR"), new Upi("ajay@okbank"), clock)
            .apply(new PaymentCommand.Decline(DeclineReason.INSUFFICIENT_FUNDS), clock);

    IO.println(renderer.receipt(refunded));
    IO.println(renderer.toJson(declined));

    IO.println("\nSettlement:");
    for (var line : new SettlementReport().settle(List.of(captured, refunded, declined))) {
        IO.println("  " + line);
    }

    try {
        declined.apply(new PaymentCommand.Capture(), clock);
    } catch (IllegalStateException e) {
        IO.println("\nExpected failure: " + e.getMessage());
    }
}

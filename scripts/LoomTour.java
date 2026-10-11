/// # Loom tour: ten thousand blocking issuer calls
///
/// Captures 10 000 authorized payments against an issuer that takes 200 ms per call, once on
/// a fixed pool of 100 platform threads and once with a virtual thread per capture. Same
/// blocking code, same `IssuerClient`; only the threads differ.
///
/// ```
/// mvn -q compile
/// java -cp target/classes scripts/LoomTour.java
/// ```
///
/// Expect roughly 20 s for the pool (10 000 × 200 ms / 100 threads) and well under 1 s for
/// virtual threads. Watch it live with `jcmd <pid> Thread.dump_to_file -format=json dump.json`
/// while it runs: the virtual threads show up named `capture-N`.

import com.ajays.modernjava.payments.capture.CaptureRun;
import com.ajays.modernjava.payments.context.RequestContext;
import com.ajays.modernjava.payments.domain.CustomerId;
import com.ajays.modernjava.payments.domain.Money;
import com.ajays.modernjava.payments.domain.Payment;
import com.ajays.modernjava.payments.domain.PaymentCommand;
import com.ajays.modernjava.payments.domain.PaymentMethod.Upi;
import com.ajays.modernjava.payments.issuer.SimulatedIssuer;

final Clock clock = Clock.systemUTC();
final Duration latency = Duration.ofMillis(200);
final int payments = 10_000;

void main() throws Exception {
    var authorized = IntStream.range(0, payments)
            .mapToObj(i -> Payment.initiate(new CustomerId("cust-" + i), Money.of("499", "INR"), new Upi("c" + i + "@okbank"), clock)
                    .apply(new PaymentCommand.Authorize("AUTH" + i), clock))
            .toList();

    IO.println("Capturing %,d payments, %d ms issuer latency each%n".formatted(payments, latency.toMillis()));

    // 1. The Java 8 way: a bounded platform-thread pool. Each blocked call holds an OS thread.
    var pooled = new SimulatedIssuer(latency);
    var start = System.nanoTime();
    try (var pool = Executors.newFixedThreadPool(100)) {
        for (var payment : authorized) {
            pool.submit(() -> new RequestContext("pool/" + payment.id().value(), "tour").run(() -> pooled.capture(payment)));
        }
    }
    report("Fixed pool, 100 platform threads", pooled.captureCount(), start);

    // 2. Loom: CaptureRun forks one virtual thread per payment; a semaphore caps calls in flight.
    var virtual = new SimulatedIssuer(latency);
    start = System.nanoTime();
    var result = new RequestContext("eod-tour", "tour")
            .call(() -> new CaptureRun(virtual, clock, payments).captureAll(authorized));
    report("Virtual thread per capture", result.captured().size(), start);
}

void report(String label, int captured, long startNanos) {
    IO.println("  %-34s %,6d captured in %,6d ms".formatted(
            label, captured, Duration.ofNanos(System.nanoTime() - startNanos).toMillis()));
}

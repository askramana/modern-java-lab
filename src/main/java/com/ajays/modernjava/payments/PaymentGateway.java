package com.ajays.modernjava.payments;

import com.ajays.modernjava.payments.domain.CustomerId;
import com.ajays.modernjava.payments.domain.DeclineReason;
import com.ajays.modernjava.payments.domain.Money;
import com.ajays.modernjava.payments.domain.Payment;
import com.ajays.modernjava.payments.domain.PaymentCommand.Authorize;
import com.ajays.modernjava.payments.domain.PaymentCommand.Decline;
import com.ajays.modernjava.payments.ingest.PaymentRequest;
import com.ajays.modernjava.payments.ingest.PaymentRequestValidator;
import com.ajays.modernjava.payments.ingest.Validated.Invalid;
import com.ajays.modernjava.payments.ingest.Validated.Valid;
import com.ajays.modernjava.payments.risk.CustomerProfile;
import com.ajays.modernjava.payments.risk.RiskDecision;
import com.ajays.modernjava.payments.risk.RiskDecision.Approve;
import com.ajays.modernjava.payments.risk.RiskDecision.Reject;
import com.ajays.modernjava.payments.risk.RiskDecision.Review;
import com.ajays.modernjava.payments.risk.RiskEngine;
import java.time.Clock;
import java.util.Currency;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;

/// Entry point: validate, create, risk-check and authorize a payment.
///
/// This class ties the Amber features together. Each step returns a sealed type, and the
/// next step pattern-matches on it, so control flow follows the data and the compiler checks
/// every branch.
///
/// The issuer call is simulated. In the Loom lesson it becomes a real blocking call on a
/// virtual thread, with the risk check and issuer call run concurrently under structured
/// concurrency.
@RequiredArgsConstructor
public final class PaymentGateway {

    private final PaymentRequestValidator validator;
    private final RiskEngine riskEngine;
    private final Clock clock;

    /// Result of [#submit(PaymentRequest, CustomerProfile)].
    public sealed interface Outcome {

        /// The request never became a payment.
        record Rejected(List<String> violations) implements Outcome {
            public Rejected { violations = List.copyOf(violations); }
        }

        /// A payment was created; `payment.status()` says how far it got.
        record Processed(Payment payment, RiskDecision decision) implements Outcome {}
    }

    public Outcome submit(PaymentRequest request, CustomerProfile profile) {
        return switch (validator.validate(request)) {
            case Invalid(var violations) -> new Outcome.Rejected(violations);
            case Valid(var valid) -> process(valid, profile);
        };
    }

    private Outcome process(PaymentRequest request, CustomerProfile profile) {
        // `var` where the right-hand side names the type; explicit type where it's a decision.
        var amount = new Money(request.amount(), Currency.getInstance(request.currency()));
        var payment = Payment.initiate(new CustomerId(request.customerId()), amount, request.method(), clock);
        RiskDecision decision = riskEngine.evaluate(payment, profile);

        Payment next = switch (decision) {
            case Approve _ -> payment.apply(new Authorize(simulateIssuerAuthCode()), clock);
            case Review _ -> payment;   // stays Initiated, waiting for an analyst
            case Reject(DeclineReason reason, _) -> payment.apply(new Decline(reason), clock);
        };
        return new Outcome.Processed(next, decision);
    }

    private static String simulateIssuerAuthCode() {
        return "AUTH" + ThreadLocalRandom.current().nextInt(100_000, 1_000_000);
    }
}

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
import com.ajays.modernjava.payments.issuer.AuthorizationResult.Approved;
import com.ajays.modernjava.payments.issuer.AuthorizationResult.Declined;
import com.ajays.modernjava.payments.issuer.IssuerClient;
import com.ajays.modernjava.payments.risk.CustomerProfile;
import com.ajays.modernjava.payments.risk.RiskDecision;
import com.ajays.modernjava.payments.risk.RiskDecision.Approve;
import com.ajays.modernjava.payments.risk.RiskDecision.Reject;
import com.ajays.modernjava.payments.risk.RiskDecision.Review;
import com.ajays.modernjava.payments.risk.RiskEngine;
import java.time.Clock;
import java.util.Currency;
import java.util.List;
import lombok.RequiredArgsConstructor;

/// Entry point: validate, create, risk-check and authorize a payment.
///
/// This class ties the Amber features together. Each step returns a sealed type, and the
/// next step pattern-matches on it, so control flow follows the data and the compiler checks
/// every branch.
///
/// ## Loom: the issuer call blocks, and that's fine
///
/// [IssuerClient#authorize(Payment)] blocks for a network round trip. There is no
/// `CompletableFuture` here: run `submit` on a virtual thread (one per request, as a modern
/// server does) and the wait costs a few hundred bytes of heap instead of an OS thread. See
/// [IssuerClient] for the full argument.
///
/// `submit` must be called inside a [com.ajays.modernjava.payments.context.RequestContext]:
/// the issuer reads the correlation id from it as an idempotency key. The context is a scoped
/// value, so it reaches the issuer without being threaded through `RiskEngine` and every
/// method in between.
///
/// Risk and authorization stay **sequential** on purpose: authorizing first would put a hold
/// on the customer's money for a payment that risk might reject. What *can* run in parallel
/// is gathering the risk signals; see `ParallelRiskSignals` in `src/preview`.
@RequiredArgsConstructor
public final class PaymentGateway {

    private final PaymentRequestValidator validator;
    private final RiskEngine riskEngine;
    private final IssuerClient issuer;
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
            case Approve _ -> authorize(payment);
            case Review _ -> payment;   // stays Initiated, waiting for an analyst
            case Reject(DeclineReason reason, _) -> payment.apply(new Decline(reason), clock);
        };
        return new Outcome.Processed(next, decision);
    }

    private Payment authorize(Payment payment) {
        return switch (issuer.authorize(payment)) {   // blocks; on a virtual thread that's cheap
            case Approved(var authCode) -> payment.apply(new Authorize(authCode), clock);
            case Declined(var reason) -> payment.apply(new Decline(reason), clock);
        };
    }
}

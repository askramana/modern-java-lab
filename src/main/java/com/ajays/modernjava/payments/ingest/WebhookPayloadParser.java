package com.ajays.modernjava.payments.ingest;

import com.ajays.modernjava.payments.domain.PaymentMethod;
import com.ajays.modernjava.payments.domain.PaymentMethod.BankTransfer;
import com.ajays.modernjava.payments.domain.PaymentMethod.Card;
import com.ajays.modernjava.payments.domain.PaymentMethod.CardNetwork;
import com.ajays.modernjava.payments.domain.PaymentMethod.Upi;
import com.ajays.modernjava.payments.wallets.PartnerWallet;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Locale;
import java.util.Map;

/// Turns an untyped webhook payload (a JSON object already parsed into `Map<String, Object>`)
/// into a [PaymentRequest].
///
/// Untyped data is where type patterns help most. JSON libraries hand you `Object`s that
/// may be `Integer`, `Long`, `Double`, `BigDecimal`, `String`, `Map`, `List` or `null`, and
/// Java 8 code handled that with `instanceof`-then-cast chains.
///
/// ## Feature: pattern matching for `instanceof` (JEP 394, final in Java 16)
///
/// `if (raw instanceof String s)` tests and binds in one step; `s` is in scope only where
/// the test is known to be true. That scope is **flow-sensitive**:
///
/// ```
/// if (!(payload instanceof Map<?, ?> root)) {
///     throw new IllegalArgumentException(...);
/// }
/// root.get("amount");          // in scope here: the method only gets here if the test passed
///
/// if (x instanceof String s && !s.isBlank()) { ... }   // OK: && only evaluates the right side when true
/// if (x instanceof String s || s.isBlank())  { ... }   // compile error: s may not be bound
/// ```
///
/// ## Feature: `switch` on any type, with `case null` (JEP 441, Java 21)
///
/// [#toAmount(Object)] switches on an `Object`. Without `case null`, a `null` selector
/// still throws `NullPointerException`, as switch always has. With it, `null` becomes an
/// ordinary case. Switching on `Object` is not exhaustive by nature, so a `default` is
/// required here, and that's correct: the set of types is open.
public final class WebhookPayloadParser {

    public PaymentRequest parse(Object payload) {
        if (!(payload instanceof Map<?, ?> root)) {
            throw new IllegalArgumentException("Payload must be a JSON object");
        }
        return PaymentRequest.builder()
                .customerId(requiredString(root, "customer"))
                .amount(toAmount(root.get("amount")))
                .currency(requiredString(root, "currency").toUpperCase(Locale.ROOT))
                .method(toMethod(root.get("method")))
                .description(root.get("description") instanceof String d ? d : null)
                .build();
    }

    /// Normalizes the numeric shapes JSON libraries produce.
    ///
    /// Note the case order: the guarded `Double` case must precede the plain `Double` case,
    /// and `default` comes last.
    static BigDecimal toAmount(Object raw) {
        return switch (raw) {
            case null -> throw new IllegalArgumentException("amount is required");
            case BigDecimal bd -> bd;
            case Integer i -> BigDecimal.valueOf(i);
            case Long l -> BigDecimal.valueOf(l);
            case Double d when d.isNaN() || d.isInfinite() -> throw new IllegalArgumentException("amount is not finite");
            // valueOf(double) goes through Double.toString, so 1499.5 becomes 1499.5, not 1499.4999999...
            case Double d -> BigDecimal.valueOf(d);
            case String s when s.isBlank() -> throw new IllegalArgumentException("amount is blank");
            case String s -> new BigDecimal(s.strip());
            default -> throw new IllegalArgumentException("Unsupported amount type: " + raw.getClass().getName());
        };
    }

    /// Parses the `method` object. The outer `instanceof` patterns validate shape *and* bind,
    /// and the string `switch` uses arrow labels. A string switch can't be exhaustive, so
    /// `default` is required.
    static PaymentMethod toMethod(Object raw) {
        if (!(raw instanceof Map<?, ?> m && m.get("type") instanceof String type)) {
            throw new IllegalArgumentException("method must be an object with a string 'type'");
        }
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "card" -> new Card(
                    CardNetwork.valueOf(requiredString(m, "network").toUpperCase(Locale.ROOT)),
                    requiredString(m, "last4"),
                    YearMonth.parse(requiredString(m, "expiry")),
                    requiredString(m, "country"));
            case "upi" -> new Upi(requiredString(m, "vpa"));
            case "bank_transfer", "neft", "imps" -> new BankTransfer(requiredString(m, "ifsc"), requiredString(m, "accountLast4"));
            case "wallet" -> new PartnerWallet(requiredString(m, "provider"), requiredString(m, "walletId"));
            default -> throw new IllegalArgumentException("Unknown method type: " + type);
        };
    }

    private static String requiredString(Map<?, ?> map, String key) {
        if (map.get(key) instanceof String s && !s.isBlank()) {
            return s;
        }
        throw new IllegalArgumentException("'" + key + "' must be a non-blank string");
    }
}

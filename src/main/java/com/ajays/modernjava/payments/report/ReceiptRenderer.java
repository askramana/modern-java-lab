package com.ajays.modernjava.payments.report;

import com.ajays.modernjava.payments.domain.Payment;
import com.ajays.modernjava.payments.domain.PaymentMethod;
import com.ajays.modernjava.payments.domain.PaymentMethod.BankTransfer;
import com.ajays.modernjava.payments.domain.PaymentMethod.Card;
import com.ajays.modernjava.payments.domain.PaymentMethod.ExternalWallet;
import com.ajays.modernjava.payments.domain.PaymentMethod.Upi;
import com.ajays.modernjava.payments.domain.PaymentStatus;
import com.ajays.modernjava.payments.domain.PaymentStatus.Authorized;
import com.ajays.modernjava.payments.domain.PaymentStatus.Captured;
import com.ajays.modernjava.payments.domain.PaymentStatus.Declined;
import com.ajays.modernjava.payments.domain.PaymentStatus.Initiated;
import com.ajays.modernjava.payments.domain.PaymentStatus.Refunded;

/// Renders payments for humans (receipt) and machines (JSON).
///
/// ## Feature: text blocks (JEP 378, final in Java 15)
///
/// A text block is a multi-line string literal between `"""` delimiters. The rules that
/// matter:
///
/// 1. The opening `"""` must be followed by a line break.
/// 2. **Incidental indentation is stripped.** The compiler finds the least-indented line,
///    *counting the closing `"""`*, and removes that much leading whitespace from every line.
///    Moving the closing delimiter left therefore indents the content.
/// 3. Trailing spaces on each line are removed. Use `\s` to keep one.
/// 4. A `\` at the end of a line joins it to the next line (no newline is inserted).
/// 5. Line endings are always `\n`, whatever the source file uses.
///
/// Text blocks are **not** string interpolation. String templates were previewed in Java 21
/// and 22 and then withdrawn because the design wasn't right. Use `String.formatted(...)`.
///
/// ## Trap: text blocks are not escaping
///
/// [#toJson(Payment)] is fine for a demo, but formatting JSON yourself breaks as soon as a
/// value contains a quote. Real code uses Jackson. Text blocks are ideal for SQL, test
/// fixtures, templates and expected output in tests.
public final class ReceiptRenderer {

    public String receipt(Payment payment) {
        return """
                ================================
                PAYMENT RECEIPT
                ================================
                Payment : %s
                Amount  : %s
                Method  : %s
                Status  : %s
                %s
                ================================
                """.formatted(
                payment.id().value(),
                payment.amount(),
                describe(payment.method()),
                label(payment.status()),
                authLine(payment));
    }

    /// The trailing `\` joins lines, so a long single-line JSON string stays readable in
    /// source without embedding newlines in the value. Closing `"""` on the last content line
    /// means no trailing newline.
    public String toJson(Payment payment) {
        return """
                {"id":"%s","customer":"%s",\
                "amount":"%s","currency":"%s",\
                "status":"%s"}""".formatted(
                payment.id().value(),
                payment.customerId().value(),
                payment.amount().amount().toPlainString(),
                payment.amount().currency().getCurrencyCode(),
                label(payment.status()));
    }

    /// One exhaustive switch per *operation*, outside the data types. Adding a payment method
    /// breaks the build here until its receipt text is written.
    public static String describe(PaymentMethod method) {
        return switch (method) {
            case Card(var network, var last4, var expiry, _) -> "%s •••• %s (exp %s)".formatted(network, last4, expiry);
            case Upi(var vpa) -> "UPI " + vpa;
            case BankTransfer(var ifsc, var last4) -> "Bank transfer %s / ••%s".formatted(ifsc, last4);
            case ExternalWallet wallet -> "Wallet: " + wallet.provider();
        };
    }

    public static String label(PaymentStatus status) {
        return switch (status) {
            case Initiated _ -> "PENDING";
            case Authorized _ -> "AUTHORIZED";
            case Captured _ -> "PAID";
            case Declined(var reason, _) -> "DECLINED (" + reason + ")";
            case Refunded(var total, _) -> "REFUNDED " + total;
        };
    }

    /// ## Nested record patterns
    ///
    /// One `instanceof` checks four things at once: it's a `Payment`, paid by `Card`, in the
    /// `Authorized` state, and binds the card's last four digits and the auth code. The Java 8
    /// version needs two `instanceof` checks, two casts and four accessor calls.
    private static String authLine(Payment payment) {
        if (payment instanceof Payment(_, _, _, Card(_, var last4, _, _), Authorized(var authCode, _))) {
            return "Auth    : %s (card ••%s)".formatted(authCode, last4);
        }
        return "Auth    : -";
    }
}

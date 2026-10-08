package com.ajays.modernjava.payments.domain;

import java.time.YearMonth;
import java.util.regex.Pattern;

/// How the customer pays.
///
/// ## Feature: sealed classes and interfaces (JEP 409, final in Java 17)
///
/// `sealed` lets a type list exactly which types may extend it. Here the subtypes are
/// declared in the same file, so the `permits` clause can be omitted and the compiler
/// infers it. In a separate-file layout you would write
/// `sealed interface PaymentMethod permits Card, Upi, BankTransfer, ExternalWallet`.
///
/// Every permitted subtype must pick one of three options, and each is a design decision:
///
/// | Modifier     | Meaning                                | Used here for |
/// |--------------|----------------------------------------|---------------|
/// | `final`      | Leaf; nothing extends it (records are implicitly final) | [Card], [Upi], [BankTransfer] |
/// | `sealed`     | Its own closed set of subtypes          | (not needed here) |
/// | `non-sealed` | Re-opens the hierarchy at this point    | [ExternalWallet] |
///
/// ### Why seal at all? Isn't that anti-OOP?
///
/// Sealing is about **exhaustiveness**. Because the compiler knows the full set of payment
/// methods, a `switch` over a `PaymentMethod` needs no `default` and fails to compile when
/// a new method is added. Every switch over this type becomes a checklist the compiler
/// maintains for you. See [com.ajays.modernjava.payments.risk.RiskEngine].
///
/// Sealed types are Java's *sum types* ("a payment method is a Card **or** a UPI **or** …");
/// records are its *product types* ("a Card is a network **and** last4 **and** …").
/// Together they are algebraic data types, the core of data-oriented programming.
///
/// ### The `non-sealed` escape hatch
///
/// We own cards, UPI and bank transfers, but wallets come from partners we don't control.
/// [ExternalWallet] is `non-sealed`, so any package can add a wallet (for example
/// [com.ajays.modernjava.payments.wallets.PartnerWallet]) without touching this file.
/// The price: switches can't enumerate wallets, so they must handle `ExternalWallet` as one
/// case. Exhaustiveness checking still works for the closed part.
///
/// ### Rules that bite
///
/// - In the unnamed module (no `module-info.java`, like this project), permitted subtypes
///   must be in the **same package**. In a named module, the same module.
/// - Records nested in an interface are implicitly `public static`.
public sealed interface PaymentMethod {

    /// A card. Only non-sensitive data is held; the PAN never enters this system.
    ///
    /// @param network        card scheme
    /// @param last4          last four digits of the card number
    /// @param expiry         month the card expires (valid through the end of that month)
    /// @param issuingCountry ISO-3166 alpha-2 country of the issuing bank
    record Card(CardNetwork network, String last4, YearMonth expiry, String issuingCountry)
            implements PaymentMethod {

        private static final Pattern LAST4 = Pattern.compile("\\d{4}");
        private static final Pattern COUNTRY = Pattern.compile("[A-Z]{2}");

        public Card {
            if (network == null || expiry == null) {
                throw new IllegalArgumentException("network and expiry are required");
            }
            if (last4 == null || !LAST4.matcher(last4).matches()) {
                throw new IllegalArgumentException("last4 must be 4 digits: " + last4);
            }
            if (issuingCountry == null || !COUNTRY.matcher(issuingCountry).matches()) {
                throw new IllegalArgumentException("issuingCountry must be ISO alpha-2: " + issuingCountry);
            }
        }

        public boolean isExpiredAt(YearMonth month) {
            return expiry.isBefore(month);
        }
    }

    /// UPI (India's real-time payments rail), addressed by a virtual payment address.
    ///
    /// @param vpa virtual payment address such as `ajay@okbank`
    record Upi(String vpa) implements PaymentMethod {

        private static final Pattern VPA = Pattern.compile("[\\w.\\-]{2,256}@[a-zA-Z]{2,64}");

        public Upi {
            if (vpa == null || !VPA.matcher(vpa).matches()) {
                throw new IllegalArgumentException("Invalid UPI VPA: " + vpa);
            }
        }

        public String handle() {
            return vpa.substring(vpa.indexOf('@') + 1);
        }
    }

    /// Bank transfer (NEFT/RTGS/IMPS style).
    ///
    /// @param ifsc         11-character IFSC code of the destination branch
    /// @param accountLast4 last four digits of the account number
    record BankTransfer(String ifsc, String accountLast4) implements PaymentMethod {

        private static final Pattern IFSC = Pattern.compile("[A-Z]{4}0[A-Z0-9]{6}");

        public BankTransfer {
            if (ifsc == null || !IFSC.matcher(ifsc).matches()) {
                throw new IllegalArgumentException("Invalid IFSC: " + ifsc);
            }
            if (accountLast4 == null || !accountLast4.matches("\\d{4}")) {
                throw new IllegalArgumentException("accountLast4 must be 4 digits");
            }
        }
    }

    /// Extension point for partner wallets. `non-sealed`: anyone may implement it.
    non-sealed interface ExternalWallet extends PaymentMethod {

        /// Partner name, used for routing and risk rules.
        String provider();

        /// The partner's identifier for the customer's wallet.
        String walletId();
    }

    /// Card schemes we accept.
    enum CardNetwork { VISA, MASTERCARD, RUPAY, AMEX }
}

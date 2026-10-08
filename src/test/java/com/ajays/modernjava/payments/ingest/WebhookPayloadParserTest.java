package com.ajays.modernjava.payments.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ajays.modernjava.payments.domain.PaymentMethod.Card;
import com.ajays.modernjava.payments.domain.PaymentMethod.CardNetwork;
import com.ajays.modernjava.payments.domain.PaymentMethod.Upi;
import com.ajays.modernjava.payments.wallets.PartnerWallet;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class WebhookPayloadParserTest {

    private final WebhookPayloadParser parser = new WebhookPayloadParser();

    static List<Object> numericShapes() {
        return List.of(1500, 1500L, 1500.0, "1500", " 1500.00 ", new BigDecimal("1500"));
    }

    @ParameterizedTest
    @MethodSource("numericShapes")
    void normalizesEveryJsonNumberShape(Object raw) {
        assertThat(WebhookPayloadParser.toAmount(raw)).isEqualByComparingTo("1500");
    }

    @Test
    void doubleIsConvertedViaItsDecimalString() {
        assertThat(WebhookPayloadParser.toAmount(0.1)).isEqualTo(new BigDecimal("0.1"));   // new BigDecimal(0.1) would not be
    }

    @Test
    void rejectsBadAmounts() {
        assertThatThrownBy(() -> WebhookPayloadParser.toAmount(null)).hasMessage("amount is required");
        assertThatThrownBy(() -> WebhookPayloadParser.toAmount(Double.NaN)).hasMessage("amount is not finite");
        assertThatThrownBy(() -> WebhookPayloadParser.toAmount(List.of(1))).hasMessageStartingWith("Unsupported amount type");
    }

    @Test
    void parsesCardPayload() {
        Map<String, Object> payload = Map.of(
                "customer", "cust-9",
                "amount", 2499.5,
                "currency", "inr",
                "method", Map.of("type", "card", "network", "rupay", "last4", "4242", "expiry", "2028-02", "country", "IN"),
                "description", "Order #77");

        var request = parser.parse(payload);

        assertThat(request.currency()).isEqualTo("INR");
        assertThat(request.amount()).isEqualByComparingTo("2499.50");
        assertThat(request.method()).isEqualTo(new Card(CardNetwork.RUPAY, "4242", YearMonth.of(2028, 2), "IN"));
        assertThat(request.description()).isEqualTo("Order #77");
    }

    @Test
    void parsesUpiAndWallet() {
        assertThat(WebhookPayloadParser.toMethod(Map.of("type", "UPI", "vpa", "ajay@okbank"))).isEqualTo(new Upi("ajay@okbank"));
        assertThat(WebhookPayloadParser.toMethod(Map.of("type", "wallet", "provider", "acmepay", "walletId", "w-1")))
                .isEqualTo(new PartnerWallet("acmepay", "w-1"));
    }

    @Test
    void rejectsMalformedShapes() {
        assertThatThrownBy(() -> parser.parse(List.of())).hasMessage("Payload must be a JSON object");
        assertThatThrownBy(() -> WebhookPayloadParser.toMethod(Map.of("type", 7))).hasMessageContaining("string 'type'");
        assertThatThrownBy(() -> WebhookPayloadParser.toMethod(Map.of("type", "crypto"))).hasMessage("Unknown method type: crypto");
    }
}

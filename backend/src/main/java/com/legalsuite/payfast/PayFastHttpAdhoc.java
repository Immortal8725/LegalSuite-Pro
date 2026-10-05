package com.legalsuite.payfast;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

@Component
public class PayFastHttpAdhoc implements PayFastAdhocGateway {
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx").withZone(ZoneOffset.UTC);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final PayFastProperties props;
    private final HttpClient http;

    public PayFastHttpAdhoc(PayFastProperties props) {
        this.props = props;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public AdhocReceipt charge(AdhocCharge charge) {
        if (charge == null || charge.token() == null || !charge.token().matches("[A-Za-z0-9-]{8,80}")) {
            return AdhocReceipt.failed("PayFast token is missing");
        }
        if (!props.merchantPresent() || !props.envKnown()) {
            return AdhocReceipt.failed("PayFast merchant id and key are not set");
        }
        String timestamp = TIMESTAMP.format(java.time.Instant.now().truncatedTo(ChronoUnit.SECONDS));
        PayFastAdhocRequest.Prepared prepared = PayFastAdhocRequest.prepare(
                props.getMerchantId(),
                props.getPassphrase(),
                timestamp,
                Long.toString(charge.amountCents()),
                charge.itemName(),
                charge.itemDescription(),
                charge.mPaymentId());
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(props.adhocUrl(charge.token())))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("merchant-id", props.getMerchantId())
                    .header("version", "v1")
                    .header("timestamp", timestamp)
                    .header("signature", prepared.signature())
                    .POST(HttpRequest.BodyPublishers.ofString(prepared.body()))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return read(response.statusCode(), response.body());
        } catch (Exception ex) {
            return AdhocReceipt.failed("PayFast adhoc request failed");
        }
    }

    static AdhocReceipt read(int httpStatus, String body) {
        String text = body == null ? "" : body.trim();
        if (text.length() > 500) text = text.substring(0, 500);
        try {
            JsonNode node = JSON.readTree(body == null ? "" : body);
            String status = node.path("status").asText("");
            JsonNode data = node.path("data");
            String responseFlag = data.path("response").asText("");
            String pf = first(data.path("pf_payment_id").asText(""), node.path("pf_payment_id").asText(""));
            String message = first(data.path("message").asText(""), node.path("data").path("message").asText(""), status);
            boolean accepted = httpStatus == 200
                    && ("success".equalsIgnoreCase(status) || "true".equalsIgnoreCase(responseFlag));
            if (accepted) return AdhocReceipt.ok(pf, message.isBlank() ? "accepted" : message);
            if (!message.isBlank()) return AdhocReceipt.failed(message);
        } catch (Exception ignored) {
            // Fall through to a generic failure. The body is not logged.
        }
        return AdhocReceipt.failed(httpStatus == 200 ? "PayFast did not accept the charge" : "PayFast returned " + httpStatus);
    }

    private static String first(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return "";
    }
}

package com.legalsuite.payfast;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Component;

/** Server confirm against PayFast's validate host. Sandbox and live hosts are fixed. */
@Component
public class PayFastHttpConfirm implements PayFastServerConfirm {
    private final PayFastProperties props;
    private final HttpClient http;

    public PayFastHttpConfirm(PayFastProperties props) {
        this.props = props;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public boolean confirmed(List<PayFastSignature.Field> fieldsInOrder) {
        if (!props.envKnown() || !props.merchantPresent()) return false;
        String body = PayFastSignature.formBody(fieldsInOrder);
        if (body.isBlank()) return false;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(props.validateUrl()))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            String text = response.body() == null ? "" : response.body().trim();
            return response.statusCode() == 200 && text.startsWith("VALID");
        } catch (Exception ex) {
            return false;
        }
    }
}

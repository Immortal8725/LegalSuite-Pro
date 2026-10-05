package com.legalsuite.voice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legalsuite.common.ApiException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class TwilioRestGateway implements TwilioGateway {
    private final TwilioProperties props;
    private final ObjectMapper json;
    private final HttpClient http;

    public TwilioRestGateway(TwilioProperties props, ObjectMapper json) {
        this.props = props;
        this.json = json;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @Override
    public List<Map<String, String>> searchLocal(String country, String areaCode, String contains, String locality) {
        String iso = requireCountry(country);
        List<String> query = new ArrayList<>();
        query.add("VoiceEnabled=true");
        query.add("PageSize=10");
        if (areaCode != null && !areaCode.isBlank()) query.add("AreaCode=" + enc(areaCode));
        if (contains != null && !contains.isBlank()) query.add("Contains=" + enc(contains));
        if (locality != null && !locality.isBlank()) query.add("InLocality=" + enc(locality));
        JsonNode body = get("/AvailablePhoneNumbers/" + iso + "/Local.json?" + String.join("&", query));
        List<Map<String, String>> out = new ArrayList<>();
        JsonNode rows = body.path("available_phone_numbers");
        if (rows.isArray()) {
            for (JsonNode row : rows) {
                Map<String, String> item = new LinkedHashMap<>();
                item.put("phoneNumber", text(row, "phone_number"));
                item.put("friendlyName", text(row, "friendly_name"));
                item.put("locality", text(row, "locality"));
                item.put("region", text(row, "region"));
                item.put("country", text(row, "iso_country"));
                out.add(item);
            }
        }
        return out;
    }

    @Override
    public Map<String, String> buyLocal(String e164, String friendlyName, String voiceUrl, String statusCallback) {
        JsonNode body = postForm("/IncomingPhoneNumbers.json", List.of(
                pair("PhoneNumber", e164),
                pair("FriendlyName", friendlyName),
                pair("VoiceUrl", voiceUrl),
                pair("VoiceMethod", "POST"),
                pair("StatusCallback", statusCallback),
                pair("StatusCallbackMethod", "POST")));
        return Map.of(
                "sid", text(body, "sid"),
                "phoneNumber", text(body, "phone_number"));
    }

    @Override
    public void releaseIncoming(String sid) {
        delete("/IncomingPhoneNumbers/" + requireSid(sid) + ".json");
    }

    @Override
    public Map<String, String> startCallerIdVerification(String e164, String friendlyName, String statusCallback) {
        JsonNode body = postForm("/OutgoingCallerIds.json", List.of(
                pair("PhoneNumber", e164),
                pair("FriendlyName", friendlyName),
                pair("StatusCallback", statusCallback),
                pair("StatusCallbackMethod", "POST")));
        Map<String, String> out = new LinkedHashMap<>();
        out.put("validationCode", text(body, "validation_code"));
        out.put("callSid", text(body, "call_sid"));
        out.put("phoneNumber", text(body, "phone_number"));
        return out;
    }

    @Override
    public String findVerifiedCallerIdSid(String e164) {
        JsonNode body = get("/OutgoingCallerIds.json?PhoneNumber=" + enc(e164));
        JsonNode rows = body.path("outgoing_caller_ids");
        if (!rows.isArray()) return null;
        for (JsonNode row : rows) {
            if (e164.equals(text(row, "phone_number"))) {
                String sid = text(row, "sid");
                return sid.isBlank() ? null : sid;
            }
        }
        return null;
    }

    @Override
    public void releaseCallerId(String sid) {
        delete("/OutgoingCallerIds/" + requireSid(sid) + ".json");
    }

    @Override
    public String createCall(String to, String from, String url, String statusCallback) {
        JsonNode body = postForm("/Calls.json", List.of(
                pair("To", to),
                pair("From", from),
                pair("Url", url),
                pair("Method", "POST"),
                pair("StatusCallback", statusCallback),
                pair("StatusCallbackMethod", "POST"),
                pair("StatusCallbackEvent", "answered"),
                pair("StatusCallbackEvent", "completed")));
        String sid = text(body, "sid");
        if (sid.isBlank()) {
            throw ApiException.badRequest("Twilio did not return a call id.");
        }
        return sid;
    }

    private JsonNode get(String pathAndQuery) {
        HttpRequest request = base(pathAndQuery).GET().build();
        return send(request);
    }

    private JsonNode postForm(String path, List<String[]> fields) {
        HttpRequest request = base(path)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(fields)))
                .build();
        return send(request);
    }

    private void delete(String path) {
        HttpRequest request = base(path).DELETE().build();
        send(request);
    }

    private HttpRequest.Builder base(String pathAndQuery) {
        if (!props.configured()) {
            throw ApiException.badRequest(
                    "Set TWILIO_ACCOUNT_SID and TWILIO_AUTH_TOKEN in the server environment. Do not paste them into the app.");
        }
        String sid = accountSid();
        String token = props.getAuthToken();
        String basic = Base64.getEncoder().encodeToString((sid + ":" + token).getBytes(StandardCharsets.UTF_8));
        String root = props.getApiRoot();
        while (root.endsWith("/")) root = root.substring(0, root.length() - 1);
        return HttpRequest.newBuilder()
                .uri(URI.create(root + "/2010-04-01/Accounts/" + sid + pathAndQuery))
                .timeout(Duration.ofSeconds(25))
                .header("Authorization", "Basic " + basic)
                .header("Accept", "application/json");
    }

    private JsonNode send(HttpRequest request) {
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body() == null ? "" : response.body();
            if (response.statusCode() >= 300) {
                throw ApiException.badRequest(twilioMessage(body, response.statusCode()));
            }
            if (body.isBlank()) return json.createObjectNode();
            return json.readTree(body);
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw ApiException.badRequest("Twilio could not be reached. Check the server network and try again.");
        }
    }

    private String twilioMessage(String body, int status) {
        try {
            JsonNode node = json.readTree(body);
            if (node.hasNonNull("message")) return node.get("message").asText();
        } catch (Exception ignored) {
            /* fall through */
        }
        return "Twilio refused the request (" + status + ").";
    }

    private String accountSid() {
        String sid = props.getAccountSid().trim();
        if (!sid.matches("[A-Za-z0-9]{10,64}")) {
            throw ApiException.badRequest("TWILIO_ACCOUNT_SID is not a valid account id.");
        }
        return sid;
    }

    private static String requireCountry(String country) {
        if (country == null || !country.trim().toUpperCase().matches("[A-Z]{2}")) {
            throw ApiException.badRequest("Choose a two-letter country code such as ZA or US.");
        }
        return country.trim().toUpperCase();
    }

    private static String requireSid(String sid) {
        if (sid == null || !sid.matches("[A-Za-z0-9]{10,64}")) {
            throw ApiException.badRequest("That carrier id is not valid.");
        }
        return sid;
    }

    private static String[] pair(String name, String value) {
        return new String[] {name, value == null ? "" : value};
    }

    private static String form(List<String[]> fields) {
        StringBuilder sb = new StringBuilder();
        for (String[] field : fields) {
            if (sb.length() > 0) sb.append('&');
            sb.append(enc(field[0])).append('=').append(enc(field[1]));
        }
        return sb.toString();
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText();
    }
}

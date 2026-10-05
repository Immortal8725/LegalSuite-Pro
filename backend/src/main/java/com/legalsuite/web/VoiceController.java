package com.legalsuite.web;

import com.legalsuite.common.ApiResponse;
import com.legalsuite.service.FirmNumberService;
import com.legalsuite.service.VoiceService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VoiceController {
    private static final String EMPTY_TWIML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response/>";

    private final VoiceService voice;
    private final FirmNumberService numbers;

    public VoiceController(VoiceService voice, FirmNumberService numbers) {
        this.voice = voice;
        this.numbers = numbers;
    }

    @GetMapping("/api/v1/voice/pstn/readiness")
    public ApiResponse<?> readiness() {
        return ApiResponse.ok(numbers.readiness());
    }

    @GetMapping("/api/v1/voice/numbers")
    public ApiResponse<?> listNumbers() {
        return ApiResponse.ok(numbers.list());
    }

    @PostMapping("/api/v1/voice/numbers/search")
    public ApiResponse<?> search(@RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(numbers.search(body));
    }

    @PostMapping("/api/v1/voice/numbers/buy")
    public ApiResponse<?> buy(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(numbers.buy(body), "Local number rented");
    }

    @PostMapping("/api/v1/voice/numbers/verify")
    public ApiResponse<?> verify(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(numbers.startVerification(body), "Twilio is calling that landline");
    }

    @PostMapping("/api/v1/voice/numbers/{id}/refresh")
    public ApiResponse<?> refresh(@PathVariable UUID id) {
        return ApiResponse.ok(numbers.refresh(id));
    }

    @PostMapping("/api/v1/voice/numbers/{id}/default")
    public ApiResponse<?> makeDefault(@PathVariable UUID id) {
        return ApiResponse.ok(numbers.makeDefault(id));
    }

    @PostMapping("/api/v1/voice/numbers/{id}/release")
    public ApiResponse<?> release(@PathVariable UUID id) {
        return ApiResponse.ok(numbers.release(id), "Caller ID released");
    }

    @PostMapping("/api/v1/calls/pstn")
    public ApiResponse<?> placePstn(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(voice.placePstn(body), "Calling your phone. Answer to connect.");
    }

    @PostMapping(value = "/api/v1/voice/twilio/bridge/{token}", produces = "text/xml;charset=UTF-8")
    public ResponseEntity<String> bridge(@PathVariable String token, HttpServletRequest request) {
        signed(request, "/api/v1/voice/twilio/bridge/" + token);
        return xml(voice.bridgeTwiml(token, request.getParameter("CallSid")));
    }

    @PostMapping(value = "/api/v1/voice/twilio/dial-result/{token}", produces = "text/xml;charset=UTF-8")
    public ResponseEntity<String> dialResult(@PathVariable String token, HttpServletRequest request) {
        signed(request, "/api/v1/voice/twilio/dial-result/" + token);
        voice.onDialResult(token, request.getParameter("DialCallStatus"), request.getParameter("DialCallDuration"),
                request.getParameter("RecordingUrl"));
        return xml(EMPTY_TWIML);
    }

    @PostMapping("/api/v1/voice/twilio/status/{token}")
    public ResponseEntity<Void> status(@PathVariable String token, HttpServletRequest request) {
        signed(request, "/api/v1/voice/twilio/status/" + token);
        voice.onParentStatus(token, request.getParameter("CallStatus"), request.getParameter("CallDuration"),
                request.getParameter("RecordingUrl"));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/voice/twilio/recording/{token}")
    public ResponseEntity<Void> recording(@PathVariable String token, HttpServletRequest request) {
        signed(request, "/api/v1/voice/twilio/recording/" + token);
        voice.onRecording(token, request.getParameter("RecordingUrl"), request.getParameter("RecordingStatus"));
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/api/v1/voice/twilio/inbound", produces = "text/xml;charset=UTF-8")
    public ResponseEntity<String> inbound(HttpServletRequest request) {
        signed(request, "/api/v1/voice/twilio/inbound");
        return xml(voice.inboundTwiml(request.getParameter("From"), request.getParameter("To"), request.getParameter("CallSid")));
    }

    @PostMapping("/api/v1/voice/twilio/inbound-status")
    public ResponseEntity<Void> inboundStatus(HttpServletRequest request) {
        signed(request, "/api/v1/voice/twilio/inbound-status");
        voice.onInboundStatus(request.getParameter("CallSid"), request.getParameter("CallStatus"), request.getParameter("CallDuration"));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/voice/twilio/verification")
    public ResponseEntity<Void> verification(HttpServletRequest request) {
        signed(request, "/api/v1/voice/twilio/verification");
        String number = first(request, "To", "Called", "PhoneNumber");
        numbers.markVerifiedFromCarrier(number, request.getParameter("VerificationStatus"), request.getParameter("OutgoingCallerIdSid"));
        return ResponseEntity.noContent().build();
    }

    private void signed(HttpServletRequest request, String path) {
        voice.assertTwilioSignature(path, request.getParameterMap(), request.getHeader("X-Twilio-Signature"));
    }

    private static ResponseEntity<String> xml(String body) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/xml;charset=UTF-8")).body(body);
    }

    private static String first(HttpServletRequest request, String... names) {
        for (String name : names) {
            String value = request.getParameter(name);
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }
}

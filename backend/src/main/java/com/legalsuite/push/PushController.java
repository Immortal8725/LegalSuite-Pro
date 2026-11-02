package com.legalsuite.push;

import com.legalsuite.common.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
public class PushController {
    private final PushService push;

    public PushController(PushService push) {
        this.push = push;
    }

    @GetMapping("/api/v1/public/push/key")
    public ApiResponse<?> key() { return ApiResponse.ok(push.keyView()); }

    // Staff
    @GetMapping("/api/v1/push/subscriptions")
    public ApiResponse<?> staffStatus() { return ApiResponse.ok(push.statusStaff()); }

    @PostMapping("/api/v1/push/subscriptions")
    public ApiResponse<?> staffSubscribe(@RequestBody Map<String, Object> body, @RequestHeader(value = "User-Agent", required = false) String ua) {
        return ApiResponse.ok(push.subscribeStaff(body, ua));
    }

    @PostMapping("/api/v1/push/subscriptions/remove")
    public ApiResponse<?> staffRemove(@RequestBody Map<String, Object> body) { return ApiResponse.ok(push.unsubscribe(body)); }

    // Portal client
    @GetMapping("/api/v1/portal/push/subscriptions")
    public ApiResponse<?> clientStatus() { return ApiResponse.ok(push.statusClient()); }

    @PostMapping("/api/v1/portal/push/subscriptions")
    public ApiResponse<?> clientSubscribe(@RequestBody Map<String, Object> body, @RequestHeader(value = "User-Agent", required = false) String ua) {
        return ApiResponse.ok(push.subscribeClient(body, ua));
    }

    @PostMapping("/api/v1/portal/push/subscriptions/remove")
    public ApiResponse<?> clientRemove(@RequestBody Map<String, Object> body) { return ApiResponse.ok(push.unsubscribe(body)); }
}

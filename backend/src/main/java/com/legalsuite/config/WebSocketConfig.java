package com.legalsuite.config;

import com.legalsuite.voice.SignalingHandler;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final SignalingHandler signalingHandler;
    private final Environment env;
    private final String corsOrigins;
    private final String appUrl;

    public WebSocketConfig(
            SignalingHandler signalingHandler,
            Environment env,
            @Value("${legalsuite.cors.allowed-origins:}") String corsOrigins,
            @Value("${legalsuite.app-url:}") String appUrl) {
        this.signalingHandler = signalingHandler;
        this.env = env;
        this.corsOrigins = corsOrigins;
        this.appUrl = appUrl;
    }

    /** Browser origins allowed to open the signalling socket: the CORS list plus the app's own URL. */
    List<String> allowedOriginPatterns() {
        List<String> out = new ArrayList<>();
        Arrays.stream(corsOrigins.split(",")).map(String::trim).filter(o -> !o.isEmpty() && !"*".equals(o)).forEach(out::add);
        if (appUrl != null && !appUrl.isBlank()) out.add(appUrl.trim().replaceAll("/+$", ""));
        if (!env.acceptsProfiles(Profiles.of("postgres"))) {
            // Local development and the E2E runs use the web app on varying localhost ports.
            out.add("http://localhost:[*]");
            out.add("http://127.0.0.1:[*]");
        }
        return out;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(signalingHandler, "/ws/signal").setAllowedOriginPatterns(allowedOriginPatterns().toArray(String[]::new));
    }
}

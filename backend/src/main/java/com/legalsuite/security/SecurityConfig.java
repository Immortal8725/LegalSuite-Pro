package com.legalsuite.security;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {
    @org.springframework.beans.factory.annotation.Value("${legalsuite.hsts.always:false}")
    private boolean hstsAlways;
    @org.springframework.beans.factory.annotation.Value("${legalsuite.hsts.max-age-seconds:31536000}")
    private long hstsMaxAge;

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"success\":false,\"message\":\"Sign in required\"}");
                }).accessDeniedHandler((request, response, denied) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"success\":false,\"message\":\"You do not have access to this.\"}");
                }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/auth/refresh",
                                "/api/v1/auth/check-slug/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/landing/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/public/sites/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/public/sites/*/subscribe").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/public/directory/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/public/pay/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/public/pay/*/itn").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/public/directory/firms/*/enquiries").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/intake/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/sign/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/sign/**").permitAll()
                        .requestMatchers("/api/v1/plans", "/api/v1/modules", "/api/v1/health", "/api/v1/status")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/ops/backup")
                        .permitAll()
                        .requestMatchers("/api/v1/portal/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/voice/twilio/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/outbound/twilio/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/product-billing/payfast/itn").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/invite/lookup", "/api/v1/auth/invite/accept").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers("/h2-console/**").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Client portal tokens only ever reach the portal endpoints.
                        .requestMatchers("/api/v1/portal/**").hasRole("CLIENT")
                        // Every other route is staff-only: a portal (client) token is refused.
                        .anyRequest().access((authentication, context) -> {
                            var current = authentication.get();
                            boolean staff = current != null
                                    && current.isAuthenticated()
                                    && !(current instanceof AnonymousAuthenticationToken)
                                    && current.getAuthorities().stream()
                                            .noneMatch(a -> "ROLE_CLIENT".equals(a.getAuthority()));
                            return new AuthorizationDecision(staff);
                        }))
                .headers(h -> h
                        // Same-origin framing only (the H2 console in local dev); the API itself is never framed.
                        .frameOptions(f -> f.sameOrigin())
                        .contentTypeOptions(Customizer.withDefaults())
                        .referrerPolicy(r -> r.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .httpStrictTransportSecurity(hsts -> {
                            hsts.includeSubDomains(true).maxAgeInSeconds(hstsMaxAge);
                            // Behind a TLS proxy that does not forward X-Forwarded-Proto, the app cannot see HTTPS.
                            // LEGALSUITE_HSTS_ALWAYS=true sends the header on every response anyway.
                            if (hstsAlways) hsts.requestMatcher(r -> true);
                        })
                        .addHeaderWriter(new org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter(
                                new org.springframework.security.web.util.matcher.NegatedRequestMatcher(
                                        org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.withDefaults().matcher("/h2-console/**")),
                                new org.springframework.security.web.header.writers.StaticHeadersWriter("Content-Security-Policy",
                                        "default-src 'none'; frame-ancestors 'self'; base-uri 'none'; form-action 'none'")))
                        .permissionsPolicyHeader(pp -> pp.policy("camera=(), geolocation=(), microphone=()")))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @org.springframework.beans.factory.annotation.Value("${legalsuite.cors.allowed-origins:}") String allowedOrigins,
            @org.springframework.beans.factory.annotation.Value("${legalsuite.public-site-root:}") String publicSiteRoot) {
        CorsConfiguration config = new CorsConfiguration();
        // Exact origins only. The web app calls the API through its own origin, so this list is for
        // other browser clients (for example a separate admin host). Never "*" with credentials.
        List<String> origins = java.util.Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(o -> !o.isEmpty() && !"*".equals(o))
                .toList();
        config.setAllowedOrigins(origins);
        // Firm sites on <slug>.<root>: one https pattern for that exact parent domain, never a bare "*".
        String root = com.legalsuite.config.PublicSiteUrls.clean(publicSiteRoot);
        if (!root.isEmpty()) config.setAllowedOriginPatterns(List.of("https://*." + root));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization", "Content-Disposition"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}

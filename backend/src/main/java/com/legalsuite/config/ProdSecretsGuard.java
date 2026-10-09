package com.legalsuite.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * The H2 demo may boot with the checked-in development JWT secret.
 * A Postgres process must not.
 */
@Component
public class ProdSecretsGuard implements ApplicationRunner, org.springframework.beans.factory.InitializingBean {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ProdSecretsGuard.class);
    static final String DEV_SECRET_MARK = "change-in-production";

    private final Environment env;
    private final String jwtSecret;

    public ProdSecretsGuard(Environment env, @Value("${legalsuite.jwt.secret:}") String jwtSecret) {
        this.env = env;
        this.jwtSecret = jwtSecret;
    }

    @Override
    public void afterPropertiesSet() {
        check();
    }

    @Override
    public void run(ApplicationArguments args) {
        check();
    }

    private void check() {
        boolean postgres = false;
        for (String profile : env.getActiveProfiles()) {
            if ("postgres".equals(profile)) postgres = true;
        }
        if (!postgres) return;
        if (jwtSecret == null || jwtSecret.isBlank() || jwtSecret.contains(DEV_SECRET_MARK) || jwtSecret.length() < 32) {
            throw new IllegalStateException(
                    "Postgres profile refused to start. Set LEGALSUITE_JWT_SECRET to a unique value of at least 32 characters. Do not commit it.");
        }
        if (blank(env.getProperty("spring.datasource.url")) || blank(env.getProperty("spring.datasource.password"))) {
            throw new IllegalStateException(
                    "Postgres profile refused to start. Set DATABASE_URL, DB_USER and DB_PASSWORD in the environment.");
        }
        String origins = env.getProperty("legalsuite.cors.allowed-origins", "");
        if (origins.contains("*")) {
            throw new IllegalStateException("CORS_ALLOWED_ORIGINS must list exact origins, not *.");
        }
        if ("true".equalsIgnoreCase(env.getProperty("legalsuite.demo-data", "false"))) {
            log.warn("LEGALSUITE_DEMO_DATA=true on the postgres profile. Use this only for a staging or demo database.");
        }
        if ("true".equalsIgnoreCase(env.getProperty("legalsuite.payfast.sandbox-local", "false"))) {
            log.warn("PAYFAST_SANDBOX_LOCAL=true: PayFast confirmations are answered locally. Never on a live system.");
        }
        String appUrl = env.getProperty("legalsuite.app-url", "");
        if (!appUrl.startsWith("https://")) {
            log.warn("APP_URL is {}. Invite links should use the public https address.", appUrl);
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}

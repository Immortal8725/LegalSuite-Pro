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
public class ProdSecretsGuard implements ApplicationRunner {
    static final String DEV_SECRET_MARK = "change-in-production";

    private final Environment env;
    private final String jwtSecret;

    public ProdSecretsGuard(Environment env, @Value("${legalsuite.jwt.secret:}") String jwtSecret) {
        this.env = env;
        this.jwtSecret = jwtSecret;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean postgres = false;
        for (String profile : env.getActiveProfiles()) {
            if ("postgres".equals(profile)) postgres = true;
        }
        if (!postgres) return;
        if (jwtSecret == null || jwtSecret.isBlank() || jwtSecret.contains(DEV_SECRET_MARK) || jwtSecret.length() < 32) {
            throw new IllegalStateException(
                    "Postgres profile refused to start. Set LEGALSUITE_JWT_SECRET to a unique value of at least 32 characters. Do not commit it.");
        }
    }
}

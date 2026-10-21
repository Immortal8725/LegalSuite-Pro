package com.legalsuite.service;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class HealthService {
    private static final java.time.Instant STARTED = java.time.Instant.now();
    private final JdbcTemplate jdbc;
    private final Environment env;

    public HealthService(DataSource dataSource, Environment env) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.env = env;
    }

    public Map<String, Object> status() {
        boolean databaseUp = false;
        long t0 = System.nanoTime();
        try {
            Integer one = jdbc.queryForObject("SELECT 1", Integer.class);
            databaseUp = one != null && one == 1;
        } catch (RuntimeException ignored) {
            databaseUp = false;
        }
        long dbMs = (System.nanoTime() - t0) / 1_000_000;
        String url = env.getProperty("spring.datasource.url", "");
        String database = "unknown";
        if (url.startsWith("jdbc:postgresql:")) database = "postgres";
        else if (url.startsWith("jdbc:h2:")) database = "h2";
        boolean postgresProfile = false;
        for (String profile : env.getActiveProfiles()) {
            if ("postgres".equals(profile)) postgresProfile = true;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", databaseUp ? "up" : "down");
        body.put("database", database);
        body.put("databaseUp", databaseUp);
        body.put("postgresProfile", postgresProfile);
        body.put("service", "owlaw-api");
        body.put("databaseLatencyMs", dbMs);
        String commit = env.getProperty("RENDER_GIT_COMMIT", "");
        body.put("version", commit.length() >= 7 ? commit.substring(0, 7) : "dev");
        body.put("startedAt", STARTED);
        body.put("uptimeSeconds", java.time.Duration.between(STARTED, java.time.Instant.now()).getSeconds());
        body.put("checkedAt", java.time.Instant.now());
        return body;
    }
}

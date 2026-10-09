package com.legalsuite.config;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Column changes that Hibernate's ddl-auto=update will not make on an existing Postgres database.
 * Each patch checks the live column first, so running it again does nothing.
 */
@Component
@Profile("postgres")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PostgresSchemaPatches implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(PostgresSchemaPatches.class);

    private final JdbcTemplate jdbc;

    public PostgresSchemaPatches(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        widenToText("conflict_checks", "results_json");
    }

    void widenToText(String table, String column) {
        List<String> types = jdbc.queryForList(
                "select data_type from information_schema.columns where table_schema = current_schema() and table_name = ? and column_name = ?",
                String.class, table, column);
        if (types.isEmpty() || "text".equalsIgnoreCase(types.get(0))) return;
        log.info("Widening {}.{} from {} to text", table, column, types.get(0));
        jdbc.execute("alter table " + table + " alter column " + column + " type text");
    }
}

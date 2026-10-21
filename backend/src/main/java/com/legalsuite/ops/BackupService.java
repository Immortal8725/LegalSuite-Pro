package com.legalsuite.ops;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Whole-database logical export for disaster recovery.
 *
 * <p>Format: gzip of JSON lines ({@code {"table":..,"columns":[..]}} header then one
 * {@code {"t":table,"r":[values]}} line per row), encrypted with AES-256-GCM.
 * File layout: magic "OWLB1" | 12-byte IV | ciphertext+tag. The key never leaves the server env
 * ({@code BACKUP_ENCRYPTION_KEY}, base64 of 32 bytes). See docs/backup-restore.md.
 */
@Service
public class BackupService {
    static final byte[] MAGIC = "OWLB1".getBytes(StandardCharsets.US_ASCII);
    private final DataSource dataSource;
    private final BackupRunRepository runs;
    private final String keyB64;
    private final String token;
    private final ObjectMapper json = new ObjectMapper();

    public BackupService(DataSource dataSource, BackupRunRepository runs,
                         @Value("${legalsuite.backup.encryption-key:}") String keyB64,
                         @Value("${legalsuite.backup.token:}") String token) {
        this.dataSource = dataSource;
        this.runs = runs;
        this.keyB64 = keyB64 == null ? "" : keyB64.trim();
        this.token = token == null ? "" : token.trim();
    }

    public boolean configured() {
        return !keyB64.isEmpty() && token.length() >= 32;
    }

    /** Constant-time token check. A blank server token never matches. */
    public boolean tokenMatches(String presented) {
        if (!configured() || presented == null) return false;
        return MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), presented.trim().getBytes(StandardCharsets.UTF_8));
    }

    public byte[] export() {
        BackupRun run = runs.save(new BackupRun());
        try {
            byte[] key = Base64.getDecoder().decode(keyB64);
            if (key.length != 32) throw new IllegalStateException("BACKUP_ENCRYPTION_KEY must be 32 bytes, base64.");
            long[] rows = {0};
            int tables;
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            try (GZIPOutputStream gz = new GZIPOutputStream(raw); Connection c = dataSource.getConnection()) {
                List<String> names = tables(c);
                tables = names.size();
                for (String t : names) dumpTable(c, t, gz, rows);
            }
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            byte[] ct = cipher.doFinal(raw.toByteArray());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(MAGIC);
            out.write(iv);
            out.write(ct);
            byte[] file = out.toByteArray();
            run.setStatus("ok");
            run.setTablesCount(tables);
            run.setRowsCount(rows[0]);
            run.setBytesCount((long) file.length);
            run.setSha256(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(file)));
            run.setFinishedAt(Instant.now());
            runs.save(run);
            return file;
        } catch (Exception e) {
            run.setStatus("failed");
            run.setDetail(e.getClass().getSimpleName());
            run.setFinishedAt(Instant.now());
            runs.save(run);
            throw new IllegalStateException("Backup failed", e);
        }
    }

    public Map<String, Object> lastRun() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("configured", configured());
        runs.findTopByStatusOrderByStartedAtDesc("ok").ifPresent(r -> {
            m.put("lastSuccessAt", r.getFinishedAt());
            m.put("lastTables", r.getTablesCount());
            m.put("lastRows", r.getRowsCount());
        });
        runs.findTopByOrderByStartedAtDesc().ifPresent(r -> m.put("lastStatus", r.getStatus()));
        return m;
    }

    private List<String> tables(Connection c) throws Exception {
        List<String> names = new ArrayList<>();
        DatabaseMetaData md = c.getMetaData();
        String schema = md.getDatabaseProductName().toLowerCase().contains("h2") ? "PUBLIC" : "public";
        try (ResultSet rs = md.getTables(null, schema, "%", new String[] {"TABLE"})) {
            while (rs.next()) {
                String n = rs.getString("TABLE_NAME");
                if (!n.toLowerCase().startsWith("flyway_")) names.add(n);
            }
        }
        names.sort(String::compareTo);
        return names;
    }

    private void dumpTable(Connection c, String table, GZIPOutputStream gz, long[] rows) throws Exception {
        if (!table.matches("[A-Za-z0-9_]+")) return;
        try (Statement st = c.createStatement()) {
            st.setFetchSize(500);
            try (ResultSet rs = st.executeQuery("SELECT * FROM \"" + table + "\"")) {
                ResultSetMetaData md = rs.getMetaData();
                List<String> cols = new ArrayList<>();
                for (int i = 1; i <= md.getColumnCount(); i++) cols.add(md.getColumnName(i).toLowerCase());
                line(gz, Map.of("table", table.toLowerCase(), "columns", cols));
                while (rs.next()) {
                    List<Object> vals = new ArrayList<>(cols.size());
                    for (int i = 1; i <= cols.size(); i++) {
                        Object v = rs.getObject(i);
                        if (v instanceof byte[] b) v = Map.of("b64", Base64.getEncoder().encodeToString(b));
                        else if (v != null && !(v instanceof Number) && !(v instanceof Boolean)) v = v.toString();
                        vals.add(v);
                    }
                    line(gz, Map.of("t", table.toLowerCase(), "r", vals));
                    rows[0]++;
                }
            }
        }
    }

    private void line(GZIPOutputStream gz, Object o) throws Exception {
        gz.write(json.writeValueAsBytes(o));
        gz.write('\n');
    }

    /** Used by tests and the restore runbook check: decrypts a file made by {@link #export()}. */
    public static byte[] decrypt(byte[] file, byte[] key) throws Exception {
        for (int i = 0; i < MAGIC.length; i++) if (file[i] != MAGIC[i]) throw new IllegalArgumentException("Not an Owlaw backup");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, file, MAGIC.length, 12));
        return cipher.doFinal(file, MAGIC.length + 12, file.length - MAGIC.length - 12);
    }
}

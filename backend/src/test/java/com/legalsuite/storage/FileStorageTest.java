package com.legalsuite.storage;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class FileStorageTest {

    @TempDir Path tmp;

    @Test
    void localStoresUnderUploadDirAndReadsBack() throws Exception {
        FileStorage fs = new FileStorage(tmp.toString(), "local", "", "", "", "", "");
        String ref = fs.put("t1/doc.txt", "hello".getBytes(), "text/plain");
        assertEquals(tmp.resolve("t1/doc.txt").toAbsolutePath().normalize().toString(), ref);
        assertEquals("hello", new String(fs.read(ref)));
        fs.delete(ref);
        assertFalse(Files.exists(Path.of(ref)));
    }

    @Test
    void localRejectsTraversal() {
        FileStorage fs = new FileStorage(tmp.toString(), "local", "", "", "", "", "");
        assertThrows(IOException.class, () -> fs.put("../evil.txt", new byte[1], null));
        assertThrows(IOException.class, () -> fs.put("/etc/evil", new byte[1], null));
    }

    @Test
    void s3RequiresCredentials() {
        assertThrows(IllegalStateException.class,
                () -> new FileStorage(tmp.toString(), "s3", "http://localhost:1", "us-east-1", "b", "", ""));
    }

    @Test
    void s3PutReadDeleteAgainstPathStyleEndpoint() throws Exception {
        Map<String, byte[]> objects = new ConcurrentHashMap<>();
        Map<String, String> auth = new ConcurrentHashMap<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            String path = ex.getRequestURI().getPath();
            String a = ex.getRequestHeaders().getFirst("Authorization");
            if (a != null) auth.put("last", a);
            byte[] out = new byte[0];
            int code = 200;
            switch (ex.getRequestMethod()) {
                case "PUT" -> objects.put(path, ex.getRequestBody().readAllBytes());
                case "GET" -> {
                    out = objects.get(path);
                    if (out == null) {
                        code = 404;
                        out = "<Error><Code>NoSuchKey</Code></Error>".getBytes();
                    }
                }
                case "DELETE" -> { objects.remove(path); code = 204; }
                default -> code = 405;
            }
            ex.sendResponseHeaders(code, code == 204 ? -1 : out.length);
            if (code != 204) ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
        try {
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/storage/v1/s3";
            FileStorage fs = new FileStorage(tmp.toString(), "s3", endpoint, "eu-central-1", "owlaw-files", "AK", "SK");
            String ref = fs.put("tenant/logo.png", "PNG".getBytes(StandardCharsets.UTF_8), "image/png");
            assertEquals("s3://owlaw-files/tenant/logo.png", ref);
            assertArrayEquals("PNG".getBytes(), objects.get("/storage/v1/s3/owlaw-files/tenant/logo.png"));
            assertTrue(auth.get("last").startsWith("AWS4-HMAC-SHA256 Credential=AK/"));
            assertEquals("PNG", new String(fs.read(ref)));
            fs.delete(ref);
            assertTrue(objects.isEmpty());
            assertThrows(IOException.class, () -> fs.read(ref));
            // Files written before switching to S3 stay readable.
            Path old = tmp.resolve("old.txt");
            Files.writeString(old, "legacy");
            assertEquals("legacy", new String(fs.read(old.toString())));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void s3RefWithoutS3ConfiguredFailsCleanly() {
        FileStorage fs = new FileStorage(tmp.toString(), "local", "", "", "", "", "");
        assertThrows(IOException.class, () -> fs.read("s3://b/k"));
    }
}

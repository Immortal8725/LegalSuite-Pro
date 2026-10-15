package com.legalsuite.common;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Optional ClamAV check (clamd INSTREAM over TCP). Off unless LEGALSUITE_CLAMAV_HOST is set.
 * When on and clamd cannot be reached, uploads are refused (fail closed) so an outage never lets files through unscanned.
 */
@Component
public class VirusScanner {
    private static final Logger log = LoggerFactory.getLogger(VirusScanner.class);
    private final String host;
    private final int port;
    private final int timeoutMs;

    public VirusScanner(@Value("${legalsuite.clamav.host:}") String host,
                        @Value("${legalsuite.clamav.port:3310}") int port,
                        @Value("${legalsuite.clamav.timeout-ms:15000}") int timeoutMs) {
        this.host = host == null ? "" : host.trim();
        this.port = port;
        this.timeoutMs = timeoutMs;
    }

    public boolean enabled() {
        return !host.isEmpty();
    }

    public void scan(MultipartFile file) {
        if (!enabled()) return;
        String reply;
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), timeoutMs);
            s.setSoTimeout(timeoutMs);
            DataOutputStream out = new DataOutputStream(s.getOutputStream());
            out.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
            byte[] buf = new byte[8192];
            try (InputStream in = file.getInputStream()) {
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.writeInt(n);
                    out.write(buf, 0, n);
                }
            }
            out.writeInt(0);
            out.flush();
            reply = new String(s.getInputStream().readAllBytes(), StandardCharsets.US_ASCII).trim();
        } catch (IOException e) {
            log.warn("Virus scanner unreachable at {}:{}: {}", host, port, e.getMessage());
            throw ApiException.badRequest("Files cannot be checked for viruses right now. Try again in a few minutes.");
        }
        if (reply.endsWith("FOUND")) {
            log.warn("Upload refused by virus scanner: {}", reply);
            throw ApiException.badRequest("This file was flagged by the virus scanner and was not saved.");
        }
        if (!reply.endsWith("OK")) {
            log.warn("Unexpected virus scanner reply: {}", reply);
            throw ApiException.badRequest("Files cannot be checked for viruses right now. Try again in a few minutes.");
        }
    }
}

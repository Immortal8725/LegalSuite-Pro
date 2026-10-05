package com.legalsuite.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** SMTP settings come only from the process environment. They are never stored on the tenant. */
@Component
@ConfigurationProperties(prefix = "legalsuite.smtp")
public class SmtpProperties {
    private String host = "";
    private int port = 587;
    private String user = "";
    private String password = "";
    private String from = "";

    public boolean configured() {
        return host != null && !host.isBlank() && from != null && !from.isBlank();
    }

    public String unconfiguredReason() {
        if (host == null || host.isBlank()) {
            return "SMTP is not configured. Set SMTP_HOST, SMTP_PORT, SMTP_USER, SMTP_PASSWORD, and SMTP_FROM on the server. This message was logged on the firm and was not delivered.";
        }
        return "Set SMTP_FROM to the address clients should see. This message was logged on the firm and was not delivered.";
    }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host == null ? "" : host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port <= 0 ? 587 : port; }
    public String getUser() { return user; }
    public void setUser(String user) { this.user = user == null ? "" : user; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password == null ? "" : password; }
    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from == null ? "" : from; }
}

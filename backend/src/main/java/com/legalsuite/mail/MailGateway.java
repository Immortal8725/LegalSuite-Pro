package com.legalsuite.mail;

/** Outbound email. When SMTP is unset the implementation reports that it is not configured. */
public interface MailGateway {
    boolean configured();

    /** Staff-facing sentence used when {@link #configured()} is false. */
    String unconfiguredReason();

    void send(String from, String to, String subject, String body);

    /** A file attached to an outbound email. */
    record Attachment(String filename, String mimeType, byte[] bytes) {}

    /** Sends with attachments. Gateways that cannot attach send the body only. */
    default void send(String from, String to, String subject, String body, java.util.List<Attachment> attachments) {
        send(from, to, subject, body);
    }
}

package com.legalsuite.mail;

/** Outbound email. When SMTP is unset the implementation reports that it is not configured. */
public interface MailGateway {
    boolean configured();

    /** Staff-facing sentence used when {@link #configured()} is false. */
    String unconfiguredReason();

    void send(String from, String to, String subject, String body);
}

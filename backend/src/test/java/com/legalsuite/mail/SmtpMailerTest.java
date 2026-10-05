package com.legalsuite.mail;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SmtpMailerTest {
    @Test
    void blankHostStaysInDryRun() {
        SmtpMailer mailer = new SmtpMailer(new SmtpProperties());
        assertFalse(mailer.configured());
        assertTrue(mailer.unconfiguredReason().contains("SMTP_HOST"));
    }

    @Test
    void hostWithoutFromAsksForTheFromAddress() {
        SmtpProperties props = new SmtpProperties();
        props.setHost("smtp.example.com");
        SmtpMailer mailer = new SmtpMailer(props);
        assertFalse(mailer.configured());
        assertTrue(mailer.unconfiguredReason().contains("SMTP_FROM"));
    }

    @Test
    void hostAndFromCountAsConfigured() {
        SmtpProperties props = new SmtpProperties();
        props.setHost("smtp.example.com");
        props.setFrom("matters@ndlovulaw.co.za");
        assertTrue(new SmtpMailer(props).configured());
    }
}

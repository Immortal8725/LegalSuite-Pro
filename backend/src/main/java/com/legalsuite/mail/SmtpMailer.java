package com.legalsuite.mail;

import com.legalsuite.common.ApiException;
import jakarta.mail.Authenticator;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SmtpMailer implements MailGateway {
    private static final Logger log = LoggerFactory.getLogger(SmtpMailer.class);
    private final SmtpProperties props;

    public SmtpMailer(SmtpProperties props) {
        this.props = props;
    }

    @Override
    public boolean configured() {
        return props.configured();
    }

    @Override
    public String unconfiguredReason() {
        return props.unconfiguredReason();
    }

    @Override
    public void send(String from, String to, String subject, String body) {
        if (!configured()) {
            throw ApiException.badRequest(unconfiguredReason());
        }
        try {
            Session session = Session.getInstance(sessionProps(), authenticator());
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.setRecipients(jakarta.mail.Message.RecipientType.TO, InternetAddress.parse(to, false));
            message.setSubject(subject, "UTF-8");
            message.setText(body == null ? "" : body, "UTF-8");
            Transport.send(message);
            log.info("Email sent to={} subjectChars={}", to, subject == null ? 0 : subject.length());
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw ApiException.badRequest("Email was not delivered. Check SMTP_HOST, SMTP_PORT, SMTP_USER, and SMTP_FROM.");
        }
    }

    private Properties sessionProps() {
        Properties mail = new Properties();
        mail.put("mail.smtp.host", props.getHost().trim());
        mail.put("mail.smtp.port", String.valueOf(props.getPort()));
        mail.put("mail.smtp.auth", props.getUser().isBlank() ? "false" : "true");
        mail.put("mail.smtp.connectiontimeout", "10000");
        mail.put("mail.smtp.timeout", "20000");
        mail.put("mail.smtp.writetimeout", "20000");
        if (props.getPort() == 465) {
            mail.put("mail.smtp.ssl.enable", "true");
        } else if (props.getPort() != 25) {
            mail.put("mail.smtp.starttls.enable", "true");
            mail.put("mail.smtp.starttls.required", "true");
        }
        return mail;
    }

    private Authenticator authenticator() {
        if (props.getUser().isBlank()) return null;
        return new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(props.getUser(), props.getPassword());
            }
        };
    }
}

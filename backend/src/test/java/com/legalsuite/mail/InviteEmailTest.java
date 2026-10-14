package com.legalsuite.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Invite by email through the real SMTP mailer, talking to a minimal SMTP catcher on a local port. */
@SpringBootTest
@AutoConfigureMockMvc
class InviteEmailTest {
    @Autowired private MockMvc mvc;
    @Autowired private SmtpProperties smtp;
    private final ObjectMapper json = new ObjectMapper();
    private final List<String> inbox = new CopyOnWriteArrayList<>();
    private ServerSocket server;
    private Thread worker;
    private String savedHost;
    private int savedPort;
    private String savedFrom;
    private boolean savedTls;

    @BeforeEach
    void startCatcher() throws Exception {
        server = new ServerSocket(0);
        worker = new Thread(this::serve, "smtp-catcher");
        worker.setDaemon(true);
        worker.start();
        savedHost = smtp.getHost();
        savedPort = smtp.getPort();
        savedFrom = smtp.getFrom();
        savedTls = smtp.isStarttls();
        smtp.setHost("127.0.0.1");
        smtp.setPort(server.getLocalPort());
        smtp.setFrom("invites@legalsuite.test");
        smtp.setStarttls(false);
    }

    @AfterEach
    void stopCatcher() throws Exception {
        smtp.setHost(savedHost);
        smtp.setPort(savedPort);
        smtp.setFrom(savedFrom);
        smtp.setStarttls(savedTls);
        server.close();
    }

    @Test
    void invitedUserGetsAOneTimeLinkAndSetsTheirOwnPassword() throws Exception {
        String owner = token("smith-associates", "john@smithlaw.com", "password");
        JsonNode invited = data(mvc.perform(post("/api/v1/users/invite").header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Lerato\",\"lastName\":\"Mokoena\",\"email\":\"lerato.invite@example.com\",\"role\":\"associate\"}"))
                .andExpect(status().isOk()).andReturn());
        assertTrue(invited.get("inviteEmailSent").asBoolean());
        // When the email goes out, the admin is not shown a password or link.
        assertFalse(invited.has("temporaryPassword"));
        assertFalse(invited.has("inviteLink"));

        String mail = waitForMail();
        assertTrue(mail.contains("lerato.invite@example.com"));
        Matcher m = Pattern.compile("/set-password\\?token=([A-Za-z0-9_-]+)").matcher(mail);
        assertTrue(m.find(), "email should carry the set-password link");
        String link = m.group(1);

        JsonNode lookup = data(mvc.perform(post("/api/v1/auth/invite/lookup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + link + "\"}"))
                .andExpect(status().isOk()).andReturn());
        assertEquals("lerato.invite@example.com", lookup.get("email").asText());

        mvc.perform(post("/api/v1/auth/invite/accept").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + link + "\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest());
        JsonNode session = data(mvc.perform(post("/api/v1/auth/invite/accept").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + link + "\",\"newPassword\":\"Lerato-chose-this-1\"}"))
                .andExpect(status().isOk()).andReturn());
        String access = session.get("accessToken").asText();
        mvc.perform(get("/api/v1/clients").header("Authorization", "Bearer " + access)).andExpect(status().isOk());
        assertFalse(session.get("user").get("mustChangePassword").asBoolean());

        // The link works once.
        mvc.perform(post("/api/v1/auth/invite/accept").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + link + "\",\"newPassword\":\"Another-password-2\"}"))
                .andExpect(status().isBadRequest());
        // The chosen password signs in.
        token("smith-associates", "lerato.invite@example.com", "Lerato-chose-this-1");
        // Resending to someone who has set a password is refused.
        mvc.perform(post("/api/v1/users/" + invited.get("id").asText() + "/resend-invite").header("Authorization", "Bearer " + owner))
                .andExpect(status().isBadRequest());
    }

    @Test
    void failedDeliveryFallsBackToTemporaryPasswordAndResendReplacesTheLink() throws Exception {
        String owner = token("smith-associates", "john@smithlaw.com", "password");
        server.close(); // nothing listening: delivery fails
        JsonNode invited = data(mvc.perform(post("/api/v1/users/invite").header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Sizwe\",\"lastName\":\"Dube\",\"email\":\"sizwe.invite@example.com\",\"role\":\"paralegal\"}"))
                .andExpect(status().isOk()).andReturn());
        assertFalse(invited.get("inviteEmailSent").asBoolean());
        assertTrue(invited.get("temporaryPassword").asText().length() >= 12);
        String firstLink = invited.get("inviteLink").asText().replaceAll(".*token=", "");

        JsonNode again = data(mvc.perform(post("/api/v1/users/" + invited.get("id").asText() + "/resend-invite")
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk()).andReturn());
        String secondLink = again.get("inviteLink").asText().replaceAll(".*token=", "");
        // The old link no longer works; the new one does.
        mvc.perform(post("/api/v1/auth/invite/lookup").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + firstLink + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/invite/lookup").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + secondLink + "\"}"))
                .andExpect(status().isOk());
        // Garbage tokens are a plain 400, not a 500.
        mvc.perform(post("/api/v1/auth/invite/lookup").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"nope\"}"))
                .andExpect(status().isBadRequest());
    }

    private String waitForMail() throws InterruptedException {
        for (int i = 0; i < 50 && inbox.isEmpty(); i++) Thread.sleep(100);
        assertFalse(inbox.isEmpty(), "no email reached the SMTP catcher");
        return inbox.get(0);
    }

    private void serve() {
        while (!server.isClosed()) {
            try (Socket s = server.accept()) {
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                OutputStream out = s.getOutputStream();
                say(out, "220 catcher ESMTP");
                StringBuilder msg = new StringBuilder();
                boolean data = false;
                String line;
                while ((line = in.readLine()) != null) {
                    if (data) {
                        if (line.equals(".")) {
                            data = false;
                            inbox.add(msg.toString());
                            say(out, "250 queued");
                        } else {
                            msg.append(line).append('\n');
                        }
                        continue;
                    }
                    String cmd = line.toUpperCase();
                    if (cmd.startsWith("EHLO") || cmd.startsWith("HELO")) say(out, "250 catcher");
                    else if (cmd.startsWith("DATA")) { data = true; say(out, "354 go"); }
                    else if (cmd.startsWith("QUIT")) { say(out, "221 bye"); break; }
                    else { msg.append(line).append('\n'); say(out, "250 ok"); }
                }
            } catch (Exception ignored) {
                // socket closed between tests
            }
        }
    }

    private static void say(OutputStream out, String text) throws java.io.IOException {
        out.write((text + "\r\n").getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    private String token(String slug, String email, String password) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firmSlug\":\"" + slug + "\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return data(r).get("accessToken").asText();
    }

    private JsonNode data(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString()).get("data");
    }
}

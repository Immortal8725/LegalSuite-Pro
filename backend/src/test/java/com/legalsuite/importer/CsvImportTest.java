package com.legalsuite.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CsvImportTest {
    @Autowired private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void csvParserHandlesQuotesAndSemicolons() {
        List<List<String>> rows = Csv.parse("a;b\r\n\"x;1\";\"say \"\"hi\"\"\"\n");
        assertEquals(List.of("x;1", "say \"hi\""), rows.get(1));
        assertEquals("'=SUM(A1)", Csv.escape("=SUM(A1)"));
    }

    @Test
    void templateDownloads() throws Exception {
        String t = mvc.perform(get("/api/v1/import/templates/matters").header("Authorization", "Bearer " + token("thabo@ndlovulaw.co.za")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(t.startsWith("matter_number,title,client_email"));
    }

    @Test
    void dryRunThenCommitClientsMattersTrustAndTime() throws Exception {
        String tok = token("thabo@ndlovulaw.co.za");
        String stamp = String.valueOf(System.nanoTime());
        String clients = "first_name,last_name,email\nImp,One,imp1-" + stamp + "@example.co.za\nImp,Two,bad-email\n";
        JsonNode dry = run(tok, "clients", clients, true, null);
        assertEquals(1, dry.get("invalid").asInt());
        JsonNode refused = run(tok, "clients", clients, false, null);
        assertEquals(0, refused.get("imported").asInt());
        JsonNode ok = run(tok, "clients", "first_name,last_name,email\nImp,One,imp1-" + stamp + "@example.co.za\n", false, null);
        assertEquals(1, ok.get("imported").asInt());

        String num = "IMP" + stamp.substring(stamp.length() - 6);
        JsonNode m = run(tok, "matters", "matter_number,title,client_email,billing_rate\n" + num + ",Imported matter,imp1-" + stamp + "@example.co.za,2000\n", false, null);
        assertEquals(1, m.get("imported").asInt(), m.toString());
        JsonNode dup = run(tok, "matters", "matter_number,title,client_email\n" + num + ",Again,imp1-" + stamp + "@example.co.za\n", true, null);
        assertEquals(1, dup.get("invalid").asInt());

        JsonNode t = run(tok, "time", "date,matter_number,minutes,hourly_rate,description\n01/10/2026," + num + ",90,2000,Consult\n", false, null);
        assertEquals(1, t.get("imported").asInt(), t.toString());

        String acct = data(mvc.perform(get("/api/v1/trust/accounts").header("Authorization", "Bearer " + tok))).get(0).get("id").asText();
        JsonNode tr = run(tok, "trust_opening", "client_email,matter_number,amount,date\nimp1-" + stamp + "@example.co.za," + num + ",1500.00,2026-09-30\n", false, acct);
        assertEquals(1, tr.get("imported").asInt(), tr.toString());
    }

    @Test
    void staffCannotCommit() throws Exception {
        String tok = token("sipho@ndlovulaw.co.za");
        mvc.perform(post("/api/v1/import/contacts").header("Authorization", "Bearer " + tok).contentType(MediaType.APPLICATION_JSON)
                .content("{\"csv\":\"first_name\\nA\\n\",\"dryRun\":false}")).andExpect(status().isForbidden());
    }

    private JsonNode run(String tok, String kind, String csv, boolean dry, String acct) throws Exception {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("csv", csv);
        b.put("dryRun", dry);
        if (acct != null) b.put("trustAccountId", acct);
        return data(mvc.perform(post("/api/v1/import/" + kind).header("Authorization", "Bearer " + tok)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(b))).andExpect(status().isOk()));
    }

    private String token(String email) throws Exception {
        return data(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"firmSlug\":\"ndlovu-partners\",\"email\":\"" + email + "\",\"password\":\"password\"}"))
                .andExpect(status().isOk())).get("accessToken").asText();
    }

    private JsonNode data(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString()).get("data");
    }
}

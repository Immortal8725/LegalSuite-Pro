package com.legalsuite.clientchange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legalsuite.domain.Client;
import com.legalsuite.domain.LegalCase;
import com.legalsuite.repo.ClientRepository;
import com.legalsuite.repo.LegalCaseRepository;
import com.legalsuite.repo.TenantRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** The client on an existing matter cannot be swapped through the general update, and saves do not fake a conflict check. */
@SpringBootTest
@AutoConfigureMockMvc
class CaseClientLockTest {
    @Autowired private MockMvc mvc;
    @Autowired private TenantRepository tenants;
    @Autowired private ClientRepository clients;
    @Autowired private LegalCaseRepository cases;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void putCannotSwapClientAndDoesNotMarkConflictChecked() throws Exception {
        UUID firm = tenants.findBySlug("ndlovu-partners").orElseThrow().getId();
        var mine = clients.findAll().stream().filter(c -> firm.equals(c.getTenantId())).toList();
        Client a = mine.get(0);
        Client b = mine.get(1);
        LegalCase m = new LegalCase();
        m.setTenantId(firm);
        m.setClientId(a.getId());
        m.setCaseNumber("LOCK-1");
        m.setTitle("Lock test");
        m.setStatus("open");
        m.setEngagementStatus("signed");
        m.setAppearanceAuthorized(true);
        m = cases.save(m);
        String thabo = token();

        mvc.perform(put("/api/v1/cases/" + m.getId()).header("Authorization", "Bearer " + thabo)
                .contentType(MediaType.APPLICATION_JSON).content("{\"clientId\":\"" + b.getId() + "\"}"))
                .andExpect(status().isBadRequest());
        assertEquals(a.getId(), cases.findById(m.getId()).orElseThrow().getClientId());

        mvc.perform(put("/api/v1/cases/" + m.getId()).header("Authorization", "Bearer " + thabo)
                .contentType(MediaType.APPLICATION_JSON).content("{\"clientId\":\"" + a.getId() + "\",\"title\":\"Lock test 2\"}"))
                .andExpect(status().isOk());
        LegalCase after = cases.findById(m.getId()).orElseThrow();
        assertEquals("Lock test 2", after.getTitle());
        assertFalse(after.isConflictChecked());
        assertTrue(after.getClientId().equals(a.getId()));
    }

    private String token() throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"firmSlug\":\"ndlovu-partners\",\"email\":\"thabo@ndlovulaw.co.za\",\"password\":\"password\"}")).andExpect(status().isOk()).andReturn();
        return json.readTree(r.getResponse().getContentAsString()).get("data").get("accessToken").asText();
    }
}

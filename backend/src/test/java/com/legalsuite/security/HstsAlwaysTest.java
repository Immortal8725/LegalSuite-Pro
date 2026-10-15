package com.legalsuite.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** LEGALSUITE_HSTS_ALWAYS=true sends HSTS even when the app only sees plain HTTP from the proxy. */
@SpringBootTest(properties = {"legalsuite.hsts.always=true", "legalsuite.hsts.max-age-seconds=600"})
@AutoConfigureMockMvc
class HstsAlwaysTest {
    @Autowired
    MockMvc mvc;

    @Test
    void hstsOnPlainHttpWhenForced() throws Exception {
        mvc.perform(get("/api/v1/health")).andExpect(header().string("Strict-Transport-Security", "max-age=600 ; includeSubDomains"));
    }
}

package com.legalsuite.payfast;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PayFastItnEndpointTest {
    @Autowired
    MockMvc mvc;

    @Test
    void itnIsPublicAndRejectsABadSignature() throws Exception {
        mvc.perform(post("/api/v1/product-billing/payfast/itn")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("payment_status=COMPLETE&amount_gross=1199.00&signature=deadbeef"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("signature"));
    }
}

package com.builddash.backend.api.controller;

import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SearchLimitIT extends AbstractIntegrationTest {

    @Test
    void search_limitAbove100_rejectedAs400() throws Exception {
        // Validation fires before the search service — no ES index needed
        mockMvc.perform(get("/search").param("q", "cement").param("limit", "2147483647"))
                .andExpect(status().isBadRequest());
    }
}

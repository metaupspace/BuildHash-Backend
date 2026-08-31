package com.builddash.backend.api.controller;

import com.builddash.backend.domain.port.TokenIssuer;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authorization contract for the search surface after 8.1-B: the permitAll rule is
 * GET-scoped, so POST /search/image falls through to the blanket POST hasRole("USER")
 * rule. Anonymous gets 401, guest 403, USER 200 with the stub's empty-match body; the
 * three public GET routes stay public.
 */
class SearchImageAuthIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenIssuer tokenIssuer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String userToken;
    private String guestToken;

    @BeforeEach
    void setUp() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, created_at, updated_at) VALUES (?, now(), now())", userId);
        userToken = "Bearer " + tokenIssuer.issueAccessToken(userId, UUID.randomUUID(), List.of("USER")).token();

        UUID guestId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, created_at, updated_at) VALUES (?, now(), now())", guestId);
        guestToken = "Bearer " + tokenIssuer.issueGuestToken(guestId).token();
    }

    private MockMultipartFile imagePart() {
        return new MockMultipartFile("image", "photo.jpg", "image/jpeg", new byte[]{1, 2, 3});
    }

    @Test
    void anonymousPostImageSearch_unauthorized401() throws Exception {
        mockMvc.perform(multipart("/search/image").file(imagePart()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void guestPostImageSearch_forbidden403() throws Exception {
        mockMvc.perform(multipart("/search/image").file(imagePart())
                        .header(HttpHeaders.AUTHORIZATION, guestToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void userPostImageSearch_ok200_withStubEmptyMatchResponse() throws Exception {
        mockMvc.perform(multipart("/search/image").file(imagePart())
                        .header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isOk())
                .andExpect(contentTypeIsJson())
                .andExpect(jsonPath("$.productIds").value(empty()));
    }

    @Test
    void anonymousGetSearch_remainsPublic() throws Exception {
        // No Elasticsearch in the shared IT context, so the live query path 500s — the
        // authorization proof uses the validation shortcut instead (SearchLimitIT
        // precedent): a 400 means the request passed the security chain and reached
        // parameter validation. 401/403 would mean the GET-scoped rule regressed.
        mockMvc.perform(get("/search").param("q", "cement").param("limit", "2147483647"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anonymousGetSuggest_remainsPublic() throws Exception {
        // Suggest hits Elasticsearch (absent in the IT context) and 500s — assert the
        // authorization boundary only: not 401, not 403.
        mockMvc.perform(get("/search/suggest").param("q", "cem"))
                .andExpect(status().is(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.anyOf(
                                org.hamcrest.Matchers.is(401),
                                org.hamcrest.Matchers.is(403)))));
    }

    @Test
    void anonymousGetTrending_remainsPublic() throws Exception {
        // Trending is Redis/JPA-backed and fully functional in the IT context.
        mockMvc.perform(get("/search/trending"))
                .andExpect(status().isOk());
    }

    private static org.springframework.test.web.servlet.ResultMatcher contentTypeIsJson() {
        return org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON);
    }
}

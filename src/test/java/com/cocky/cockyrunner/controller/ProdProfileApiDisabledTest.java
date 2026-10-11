package com.cocky.cockyrunner.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** In prod the local-dev /api/v1/** controllers are not registered at all; /internal/** stays token-protected. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class ProdProfileApiDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiV1_isNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/problems")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/problems/a-plus-b")).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/executions").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/problems/a-plus-b/submissions").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void internal_stillRequiresToken() throws Exception {
        mockMvc.perform(post("/internal/v1/run").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }
}

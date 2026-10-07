package com.cocky.cockyrunner.api;

import com.cocky.cockyrunner.config.CorsProperties;
import com.cocky.cockyrunner.exception.InvalidExecutionRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Token filter (token "test-token" comes from src/test/resources/application.properties) and error mapping. */
@WebMvcTest(InternalApiController.class)
@Import({RunnerApiConfig.class, RunnerApiExceptionHandler.class})
@EnableConfigurationProperties(CorsProperties.class)
class InternalApiControllerTest {

    private static final String RUN_BODY =
            "{\"language\":\"python\",\"sourceCode\":\"print(1)\",\"stdin\":\"\",\"timeLimitMs\":1000,\"memoryLimitKb\":262144}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExternalExecutionService service;

    @Test
    void missingToken_returns401_andNeverReachesTheService() throws Exception {
        mockMvc.perform(post("/internal/v1/run").contentType(MediaType.APPLICATION_JSON).content(RUN_BODY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/internal/v1/judge").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());

        verify(service, never()).run(any());
        verify(service, never()).judge(any());
    }

    @Test
    void wrongToken_returns401() throws Exception {
        mockMvc.perform(post("/internal/v1/run").header("X-Runner-Token", "nope")
                        .contentType(MediaType.APPLICATION_JSON).content(RUN_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void correctToken_passes_andReturnsContractFields() throws Exception {
        when(service.run(any())).thenReturn(new RunApiResponse(RunStatus.OK, "1\n", "", null, 12));

        mockMvc.perform(post("/internal/v1/run").header("X-Runner-Token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON).content(RUN_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.stdout").value("1\n"))
                .andExpect(jsonPath("$.compileOutput").value((Object) null))
                .andExpect(jsonPath("$.timeMs").value(12));
    }

    @Test
    void judgeResponseShape() throws Exception {
        when(service.judge(any())).thenReturn(new JudgeApiResponse(ApiVerdict.WA, 1, 2, 30, null, null));

        mockMvc.perform(post("/internal/v1/judge").header("X-Runner-Token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("WA"))
                .andExpect(jsonPath("$.passedCount").value(1))
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.maxTimeMs").value(30))
                .andExpect(jsonPath("$.maxMemoryKb").value((Object) null))
                .andExpect(jsonPath("$.compileOutput").value((Object) null));
    }

    @Test
    void errorMapping_400_429_500() throws Exception {
        doThrow(new InvalidExecutionRequestException("bad")).when(service).run(any());
        mockMvc.perform(post("/internal/v1/run").header("X-Runner-Token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON).content(RUN_BODY))
                .andExpect(status().isBadRequest());

        doThrow(new RunnerBusyException("busy")).when(service).run(any());
        mockMvc.perform(post("/internal/v1/run").header("X-Runner-Token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON).content(RUN_BODY))
                .andExpect(status().isTooManyRequests());

        doThrow(new RunnerInternalException("docker down")).when(service).run(any());
        mockMvc.perform(post("/internal/v1/run").header("X-Runner-Token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON).content(RUN_BODY))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void frontendApiPathsAreNotTokenProtected() throws Exception {
        // /api/** has no controller in this slice, so 404 (not 401) proves the filter skipped it
        mockMvc.perform(post("/api/v1/executions").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }
}

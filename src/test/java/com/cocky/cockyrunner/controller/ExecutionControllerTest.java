package com.cocky.cockyrunner.controller;

import com.cocky.cockyrunner.config.CorsProperties;
import com.cocky.cockyrunner.diagnostic.Diagnostic;
import com.cocky.cockyrunner.diagnostic.Severity;
import com.cocky.cockyrunner.domain.ExecutionStatus;
import com.cocky.cockyrunner.dto.ExecutionResponse;
import com.cocky.cockyrunner.service.ExecutionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExecutionController.class)
@EnableConfigurationProperties(CorsProperties.class)
class ExecutionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExecutionService executionService;

    @Test
    void success_returns200() throws Exception {
        when(executionService.execute(any()))
                .thenReturn(ExecutionResponse.withoutTiming(ExecutionStatus.SUCCESS, "1\n", "", 0, 10));

        mockMvc.perform(post("/api/v1/executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"python\",\"code\":\"print(1)\",\"stdin\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void oversizedCode_returns400WithoutCallingExecutionService() throws Exception {
        String oversizedCode = "\"" + "a".repeat(70_000) + "\"";

        mockMvc.perform(post("/api/v1/executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"python\",\"code\":" + oversizedCode + ",\"stdin\":\"\"}"))
                .andExpect(status().isBadRequest());

        verify(executionService, never()).execute(any());
    }

    @Test
    void response_includesDiagnostics() throws Exception {
        when(executionService.execute(any())).thenReturn(new ExecutionResponse(
                ExecutionStatus.COMPILE_ERROR, "", "main.c:3:5: error: boom", -1, 0, null, null,
                List.of(new Diagnostic(3, 5, Severity.ERROR, "boom"))));

        mockMvc.perform(post("/api/v1/executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"c\",\"code\":\"x\",\"stdin\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stderr").value("main.c:3:5: error: boom"))
                .andExpect(jsonPath("$.diagnostics[0].line").value(3))
                .andExpect(jsonPath("$.diagnostics[0].column").value(5))
                .andExpect(jsonPath("$.diagnostics[0].severity").value("ERROR"))
                .andExpect(jsonPath("$.diagnostics[0].message").value("boom"));
    }

    @Test
    void response_withoutDiagnostics_hasEmptyArray() throws Exception {
        when(executionService.execute(any()))
                .thenReturn(ExecutionResponse.withoutTiming(ExecutionStatus.SUCCESS, "1\n", "", 0, 10));

        mockMvc.perform(post("/api/v1/executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"python\",\"code\":\"print(1)\",\"stdin\":\"\"}"))
                .andExpect(jsonPath("$.diagnostics").isArray())
                .andExpect(jsonPath("$.diagnostics").isEmpty());
    }
}

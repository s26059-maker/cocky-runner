package com.cocky.cockyrunner.controller;

import com.cocky.cockyrunner.config.CorsProperties;
import com.cocky.cockyrunner.domain.Language;
import com.cocky.cockyrunner.domain.SampleRunResult;
import com.cocky.cockyrunner.domain.SampleRunResult.SampleCaseResult;
import com.cocky.cockyrunner.domain.Verdict;
import com.cocky.cockyrunner.exception.ProblemNotFoundException;
import com.cocky.cockyrunner.service.JudgeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SampleRunController.class)
@EnableConfigurationProperties(CorsProperties.class)
class SampleRunControllerTest {

    private static final String URL = "/api/v1/problems/a-plus-b/samples/run";
    private static final String BODY = "{\"language\":\"python\",\"code\":\"print(1)\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JudgeService judgeService;

    @Test
    void allPass_returnsEveryCase() throws Exception {
        when(judgeService.judgeSamples(eq("a-plus-b"), eq(Language.PYTHON), any()))
                .thenReturn(new SampleRunResult(null, List.of(
                        new SampleCaseResult(1, "1 2", "3", "3", Verdict.AC, null, 20L),
                        new SampleCaseResult(2, "2 3", "5", "5", Verdict.AC, null, null))));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compileError").value(nullValue()))
                .andExpect(jsonPath("$.cases", hasSize(2)))
                .andExpect(jsonPath("$.cases[0].index").value(1))
                .andExpect(jsonPath("$.cases[0].input").value("1 2"))
                .andExpect(jsonPath("$.cases[0].expectedOutput").value("3"))
                .andExpect(jsonPath("$.cases[0].actualOutput").value("3"))
                .andExpect(jsonPath("$.cases[0].verdict").value("AC"))
                .andExpect(jsonPath("$.cases[0].userWallMs").value(20))
                .andExpect(jsonPath("$.cases[1].userWallMs").value(nullValue()));
    }

    @Test
    void partialWrongAnswer_returnsMixedVerdicts() throws Exception {
        when(judgeService.judgeSamples(eq("a-plus-b"), eq(Language.PYTHON), any()))
                .thenReturn(new SampleRunResult(null, List.of(
                        new SampleCaseResult(1, "1 2", "3", "3", Verdict.AC, null, 10L),
                        new SampleCaseResult(2, "2 3", "5", "6", Verdict.WA, null, 11L),
                        new SampleCaseResult(3, "x", "1", "", Verdict.RE, "Traceback...", 12L))));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cases[0].verdict").value("AC"))
                .andExpect(jsonPath("$.cases[1].verdict").value("WA"))
                .andExpect(jsonPath("$.cases[1].actualOutput").value("6"))
                .andExpect(jsonPath("$.cases[2].verdict").value("RE"))
                .andExpect(jsonPath("$.cases[2].stderr").value("Traceback..."));
    }

    @Test
    void compileError_returnsMessageWithEmptyCases() throws Exception {
        when(judgeService.judgeSamples(eq("a-plus-b"), eq(Language.PYTHON), any()))
                .thenReturn(new SampleRunResult("main.c:1:1: error", List.of()));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compileError").value("main.c:1:1: error"))
                .andExpect(jsonPath("$.cases", hasSize(0)));
    }

    @Test
    void noSamples_returnsEmptyCasesNotAnError() throws Exception {
        when(judgeService.judgeSamples(eq("a-plus-b"), eq(Language.PYTHON), any()))
                .thenReturn(new SampleRunResult(null, List.of()));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compileError").value(nullValue()))
                .andExpect(jsonPath("$.cases", hasSize(0)));
    }

    @Test
    void blankCode_returns400WithoutCallingJudgeService() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"python\",\"code\":\"\"}"))
                .andExpect(status().isBadRequest());

        verify(judgeService, never()).judgeSamples(any(), any(), any());
    }

    @Test
    void unsupportedLanguage_returns400WithoutCallingJudgeService() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"ruby\",\"code\":\"print(1)\"}"))
                .andExpect(status().isBadRequest());

        verify(judgeService, never()).judgeSamples(any(), any(), any());
    }

    @Test
    void unknownProblemId_returns404() throws Exception {
        when(judgeService.judgeSamples(eq("a-plus-b"), eq(Language.PYTHON), any()))
                .thenThrow(new ProblemNotFoundException("problem not found: a-plus-b"));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound());
    }
}

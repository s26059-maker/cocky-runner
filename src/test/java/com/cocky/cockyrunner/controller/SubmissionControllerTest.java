package com.cocky.cockyrunner.controller;

import com.cocky.cockyrunner.domain.JudgeResult;
import com.cocky.cockyrunner.domain.Language;
import com.cocky.cockyrunner.domain.Verdict;
import com.cocky.cockyrunner.exception.ProblemNotFoundException;
import com.cocky.cockyrunner.service.JudgeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SubmissionController.class)
class SubmissionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JudgeService judgeService;

    @Test
    void acVerdict_returns200() throws Exception {
        when(judgeService.judge(eq("a-plus-b"), eq(Language.PYTHON), any()))
                .thenReturn(new JudgeResult(Verdict.AC, 3, 3, null, 412));

        mockMvc.perform(post("/api/v1/problems/a-plus-b/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"python\",\"code\":\"print(1)\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("AC"))
                .andExpect(jsonPath("$.passedCount").value(3))
                .andExpect(jsonPath("$.totalCount").value(3))
                .andExpect(jsonPath("$.failedCaseNumber").value(nullValue()))
                .andExpect(jsonPath("$.maxExecutionTimeMs").value(412));
    }

    @Test
    void waVerdict_returns200WithFailedCaseNumber() throws Exception {
        when(judgeService.judge(eq("a-plus-b"), eq(Language.PYTHON), any()))
                .thenReturn(new JudgeResult(Verdict.WA, 1, 3, 2, 120));

        mockMvc.perform(post("/api/v1/problems/a-plus-b/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"python\",\"code\":\"print(1)\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("WA"))
                .andExpect(jsonPath("$.failedCaseNumber").value(2));
    }

    @Test
    void errorVerdict_returns500() throws Exception {
        when(judgeService.judge(eq("a-plus-b"), eq(Language.PYTHON), any()))
                .thenReturn(new JudgeResult(Verdict.ERROR, 0, 3, 1, 0));

        mockMvc.perform(post("/api/v1/problems/a-plus-b/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"python\",\"code\":\"print(1)\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.verdict").value("ERROR"));
    }

    @Test
    void blankCode_returns400WithoutCallingJudgeService() throws Exception {
        mockMvc.perform(post("/api/v1/problems/a-plus-b/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"python\",\"code\":\"\"}"))
                .andExpect(status().isBadRequest());

        verify(judgeService, never()).judge(any(), any(), any());
    }

    @Test
    void unsupportedLanguage_returns400WithoutCallingJudgeService() throws Exception {
        mockMvc.perform(post("/api/v1/problems/a-plus-b/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"ruby\",\"code\":\"print(1)\"}"))
                .andExpect(status().isBadRequest());

        verify(judgeService, never()).judge(any(), any(), any());
    }

    @Test
    void unknownProblemId_returns404() throws Exception {
        when(judgeService.judge(eq("no-such-problem"), eq(Language.PYTHON), any()))
                .thenThrow(new ProblemNotFoundException("problem not found: no-such-problem"));

        mockMvc.perform(post("/api/v1/problems/no-such-problem/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"python\",\"code\":\"print(1)\"}"))
                .andExpect(status().isNotFound());
    }
}

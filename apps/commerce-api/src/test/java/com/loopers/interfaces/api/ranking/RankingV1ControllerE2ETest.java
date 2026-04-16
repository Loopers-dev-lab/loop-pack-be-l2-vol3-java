package com.loopers.interfaces.api.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
@DisplayName("랭킹 API E2E 테스트")
class RankingV1ControllerE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/v1/rankings — ZSET이 비어있으면 빈 결과 반환")
    void list_EmptyZset_ReturnsEmptyContent() throws Exception {
        mockMvc.perform(get("/api/v1/rankings")
                        .param("date", "20260407")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content").isEmpty());
    }

    @Test
    @DisplayName("GET /api/v1/rankings — date 미입력 시 오늘 날짜로 조회")
    void list_NoDate_DefaultsToToday() throws Exception {
        mockMvc.perform(get("/api/v1/rankings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.result").value("SUCCESS"));
    }

    @Test
    @DisplayName("GET /api/v1/rankings — size=0이면 400 반환")
    void list_SizeZero_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/rankings").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.meta.result").value("FAIL"));
    }

    @Test
    @DisplayName("GET /api/v1/rankings — size가 음수면 400 반환")
    void list_SizeNegative_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/rankings").param("size", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.meta.result").value("FAIL"));
    }

    @Test
    @DisplayName("GET /api/v1/rankings — page가 음수면 400 반환")
    void list_PageNegative_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/rankings").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.meta.result").value("FAIL"));
    }

    @Test
    @DisplayName("GET /api/v1/rankings/hourly — size=0이면 400 반환")
    void hourly_SizeZero_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/rankings/hourly").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.meta.result").value("FAIL"));
    }

    @Test
    @DisplayName("GET /api/v1/rankings/hourly — size가 음수면 400 반환")
    void hourly_SizeNegative_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/rankings/hourly").param("size", "-2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.meta.result").value("FAIL"));
    }

    @Test
    @DisplayName("GET /api/v1/rankings — size가 상한(100)을 초과하면 400 반환")
    void list_SizeExceedsMax_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/rankings").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.meta.result").value("FAIL"));
    }

    @Test
    @DisplayName("GET /api/v1/rankings/hourly — size가 상한(100)을 초과하면 400 반환")
    void hourly_SizeExceedsMax_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/rankings/hourly").param("size", "1000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.meta.result").value("FAIL"));
    }
}

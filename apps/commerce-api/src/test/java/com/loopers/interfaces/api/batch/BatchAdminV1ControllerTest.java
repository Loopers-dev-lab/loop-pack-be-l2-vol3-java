package com.loopers.interfaces.api.batch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("BatchAdminV1Controller 테스트")
class BatchAdminV1ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        cleanUpTestData();
    }

    private void cleanUpTestData() {
        jdbcTemplate.execute("DELETE FROM mv_product_rank_weekly");
        jdbcTemplate.execute("DELETE FROM mv_product_rank_monthly");
        jdbcTemplate.execute("DELETE FROM product_metrics_daily");
    }

    @Nested
    @DisplayName("POST /api-admin/v1/batch/weekly-ranking")
    class WeeklyRankingJob {

        @Test
        @DisplayName("주간 랭킹 Job을 실행하고 성공 응답을 반환한다")
        void runWeeklyRankingJob_Success() throws Exception {
            // Arrange
            insertDailyMetrics(100L, LocalDate.of(2025, 1, 13), 10, 5, 2, BigDecimal.valueOf(3.0));

            // Act & Assert
            mockMvc.perform(post("/api-admin/v1/batch/weekly-ranking")
                    .param("targetDate", "20250115"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.result", is("SUCCESS")))
                .andExpect(jsonPath("$.data.jobName", is("weeklyRankingJob")))
                .andExpect(jsonPath("$.data.status", is("COMPLETED")))
                .andExpect(jsonPath("$.data.executionId", notNullValue()))
                .andExpect(jsonPath("$.data.startTime", notNullValue()))
                .andExpect(jsonPath("$.data.endTime", notNullValue()));
        }

        @Test
        @DisplayName("잘못된 targetDate 형식이면 400 에러를 반환한다")
        void runWeeklyRankingJob_InvalidDateFormat() throws Exception {
            mockMvc.perform(post("/api-admin/v1/batch/weekly-ranking")
                    .param("targetDate", "2025-01-15"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.meta.result", is("FAIL")))
                .andExpect(jsonPath("$.meta.errorCode", is("BATCH_INVALID_DATE_FORMAT")));
        }

        @Test
        @DisplayName("targetDate 파라미터가 없으면 400 에러를 반환한다")
        void runWeeklyRankingJob_MissingParameter() throws Exception {
            mockMvc.perform(post("/api-admin/v1/batch/weekly-ranking"))
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api-admin/v1/batch/monthly-ranking")
    class MonthlyRankingJob {

        @Test
        @DisplayName("월간 랭킹 Job을 실행하고 성공 응답을 반환한다")
        void runMonthlyRankingJob_Success() throws Exception {
            // Arrange
            insertDailyMetrics(100L, LocalDate.of(2025, 1, 15), 10, 5, 2, BigDecimal.valueOf(3.0));

            // Act & Assert
            mockMvc.perform(post("/api-admin/v1/batch/monthly-ranking")
                    .param("targetDate", "20250115"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.result", is("SUCCESS")))
                .andExpect(jsonPath("$.data.jobName", is("monthlyRankingJob")))
                .andExpect(jsonPath("$.data.status", is("COMPLETED")))
                .andExpect(jsonPath("$.data.executionId", notNullValue()))
                .andExpect(jsonPath("$.data.startTime", notNullValue()))
                .andExpect(jsonPath("$.data.endTime", notNullValue()));
        }

        @Test
        @DisplayName("잘못된 targetDate 형식이면 400 에러를 반환한다")
        void runMonthlyRankingJob_InvalidDateFormat() throws Exception {
            mockMvc.perform(post("/api-admin/v1/batch/monthly-ranking")
                    .param("targetDate", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.meta.result", is("FAIL")))
                .andExpect(jsonPath("$.meta.errorCode", is("BATCH_INVALID_DATE_FORMAT")));
        }
    }

    private void insertDailyMetrics(Long productId, LocalDate metricDate, int viewCount, int likeCount, int orderCount, BigDecimal score) {
        jdbcTemplate.update(
            """
            INSERT INTO product_metrics_daily (product_id, metric_date, view_count, like_count, order_count, score, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())
            """,
            productId, metricDate, viewCount, likeCount, orderCount, score
        );
    }
}
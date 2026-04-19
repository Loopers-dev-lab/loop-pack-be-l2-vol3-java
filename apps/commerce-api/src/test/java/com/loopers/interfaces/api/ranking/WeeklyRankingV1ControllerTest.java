package com.loopers.interfaces.api.ranking;

import com.loopers.application.product.ProductInfo;
import com.loopers.application.ranking.RankingItemInfo;
import com.loopers.application.ranking.RankingPageResult;
import com.loopers.application.ranking.WeeklyRankingFacade;
import com.loopers.config.WebMvcConfig;
import com.loopers.domain.auth.LdapAuthService;
import com.loopers.domain.member.MemberService;
import com.loopers.domain.product.ProductStatus;
import com.loopers.interfaces.api.auth.AdminAuthInterceptor;
import com.loopers.interfaces.api.auth.LoginAdminArgumentResolver;
import com.loopers.interfaces.api.auth.LoginMemberArgumentResolver;
import com.loopers.interfaces.api.auth.MemberAuthInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WeeklyRankingV1Controller.class)
@Import({WebMvcConfig.class, MemberAuthInterceptor.class, LoginMemberArgumentResolver.class,
        AdminAuthInterceptor.class, LoginAdminArgumentResolver.class})
@DisplayName("WeeklyRankingV1Controller 단위 테스트")
class WeeklyRankingV1ControllerTest {

    private static final String ENDPOINT = "/api/v1/rankings/weekly";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WeeklyRankingFacade weeklyRankingFacade;

    @MockBean
    private MemberService memberService;

    @MockBean
    private LdapAuthService ldapAuthService;

    private RankingItemInfo stubItem(long rank) {
        ProductInfo product = new ProductInfo(
                rank, 1L, "브랜드", "상품" + rank, 10000, 9000, 2500, 0,
                ProductStatus.ON_SALE, "Y", ZonedDateTime.now());
        return new RankingItemInfo(rank, 10.0 / rank, product);
    }

    @Nested
    @DisplayName("GET /api/v1/rankings/weekly")
    class GetWeeklyRanking {

        @Test
        @DisplayName("200 — Facade 결과가 JSON으로 올바르게 직렬화된다.")
        void happyPath() throws Exception {
            // given
            LocalDate baseDate = LocalDate.of(2026, 4, 11);
            List<RankingItemInfo> items = List.of(stubItem(1), stubItem(2));
            when(weeklyRankingFacade.getWeeklyRanking(eq(baseDate), eq(1), eq(20)))
                    .thenReturn(new RankingPageResult(baseDate, 2L, items));

            // when & then
            mockMvc.perform(get(ENDPOINT)
                            .param("date", "20260411")
                            .param("page", "1")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.date").value("20260411"))
                    .andExpect(jsonPath("$.data.page").value(1))
                    .andExpect(jsonPath("$.data.size").value(20))
                    .andExpect(jsonPath("$.data.totalElements").value(2))
                    .andExpect(jsonPath("$.data.items").isArray())
                    .andExpect(jsonPath("$.data.items[0].rank").value(1))
                    .andExpect(jsonPath("$.data.items[1].rank").value(2));
        }

        @Test
        @DisplayName("date 파라미터 생략 시 Facade 에 null 이 전달되어 날짜 기본값 처리가 위임된다.")
        void nullDatePassedToFacade() throws Exception {
            // given
            LocalDate yesterday = LocalDate.of(2026, 4, 11);
            when(weeklyRankingFacade.getWeeklyRanking(isNull(), eq(1), eq(20)))
                    .thenReturn(new RankingPageResult(yesterday, 0L, List.of()));

            // when & then
            mockMvc.perform(get(ENDPOINT))
                    .andExpect(status().isOk());

            verify(weeklyRankingFacade).getWeeklyRanking(null, 1, 20);
        }

        @Test
        @DisplayName("page=0 은 1로 보정되어 Facade 에 전달된다.")
        void pageZeroClampedToOne() throws Exception {
            // given
            LocalDate baseDate = LocalDate.of(2026, 4, 11);
            when(weeklyRankingFacade.getWeeklyRanking(any(), eq(1), anyInt()))
                    .thenReturn(new RankingPageResult(baseDate, 0L, List.of()));

            // when & then
            mockMvc.perform(get(ENDPOINT).param("date", "20260411").param("page", "0"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.page").value(1));

            verify(weeklyRankingFacade).getWeeklyRanking(any(), eq(1), anyInt());
        }

        @Test
        @DisplayName("size=200 은 100으로 보정되어 Facade 에 전달된다.")
        void sizeClampedToMax() throws Exception {
            // given
            LocalDate baseDate = LocalDate.of(2026, 4, 11);
            when(weeklyRankingFacade.getWeeklyRanking(any(), anyInt(), eq(100)))
                    .thenReturn(new RankingPageResult(baseDate, 0L, List.of()));

            // when & then
            mockMvc.perform(get(ENDPOINT).param("date", "20260411").param("size", "200"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.size").value(100));

            verify(weeklyRankingFacade).getWeeklyRanking(any(), anyInt(), eq(100));
        }

        @Test
        @DisplayName("size=0 은 기본값 20으로 보정되어 Facade 에 전달된다.")
        void sizeZeroClampedToDefault() throws Exception {
            // given
            LocalDate baseDate = LocalDate.of(2026, 4, 11);
            when(weeklyRankingFacade.getWeeklyRanking(any(), anyInt(), eq(20)))
                    .thenReturn(new RankingPageResult(baseDate, 0L, List.of()));

            // when & then
            mockMvc.perform(get(ENDPOINT).param("date", "20260411").param("size", "0"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.size").value(20));

            verify(weeklyRankingFacade).getWeeklyRanking(any(), anyInt(), eq(20));
        }

        @Test
        @DisplayName("잘못된 date 포맷은 400 BAD_REQUEST 를 반환한다.")
        void badDateFormat() throws Exception {
            // when & then
            mockMvc.perform(get(ENDPOINT).param("date", "2026-04-11"))
                    .andExpect(status().isBadRequest());
        }
    }
}

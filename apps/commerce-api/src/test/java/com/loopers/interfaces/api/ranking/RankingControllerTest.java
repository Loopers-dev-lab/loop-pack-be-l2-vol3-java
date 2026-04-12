package com.loopers.interfaces.api.ranking;

import com.loopers.application.behavior.BehaviorEventPublisher;
import com.loopers.application.member.MemberAuthenticationService;
import com.loopers.application.order.queue.OrderAdmissionApplicationService;
import com.loopers.application.order.queue.OrderQueueProperties;
import com.loopers.application.product.ProductApplicationService;
import com.loopers.application.product.ProductDetailQueryFacade;
import com.loopers.application.product.ProductQueryFacade;
import com.loopers.application.product.PublicProductListQueryApplicationService;
import com.loopers.application.product.view.ProductDetailView;
import com.loopers.application.product.view.ProductView;
import com.loopers.application.ranking.RankingProductView;
import com.loopers.application.ranking.RankingQueryFacade;
import com.loopers.application.ranking.TopRankingProductView;
import com.loopers.interfaces.api.product.ProductController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {RankingController.class, ProductController.class})
@Import(com.loopers.interfaces.api.ApiControllerAdvice.class)
class RankingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RankingQueryFacade rankingQueryFacade;

    @MockBean
    private ProductApplicationService productApplicationService;

    @MockBean
    private ProductQueryFacade productQueryFacade;

    @MockBean
    private ProductDetailQueryFacade productDetailQueryFacade;

    @MockBean
    private PublicProductListQueryApplicationService publicProductListQueryApplicationService;

    @MockBean
    private BehaviorEventPublisher behaviorEventPublisher;

    @MockBean
    private MemberAuthenticationService memberAuthenticationService;

    @MockBean
    private OrderAdmissionApplicationService orderAdmissionApplicationService;

    @MockBean
    private OrderQueueProperties orderQueueProperties;

    @Nested
    @DisplayName("GET /api/v1/rankings")
    class GetRankings {

        @Test
        @DisplayName("일간 랭킹 페이지와 상품 정보를 반환한다")
        void getDailyRankingsSuccess() throws Exception {
            UUID firstProductId = UUID.randomUUID();
            UUID secondProductId = UUID.randomUUID();
            UUID brandId = UUID.randomUUID();
            LocalDate metricDate = LocalDate.of(2025, 9, 7);
            given(rankingQueryFacade.getDailyPage(metricDate, 1, 2)).willReturn(List.of(
                    new TopRankingProductView(firstProductId, "사료A", 10000, brandId, "퍼피박스", 5, 1L, 3.5d),
                    new TopRankingProductView(secondProductId, "사료B", 9000, brandId, "퍼피박스", 3, 2L, 1.2d)
            ));

            mockMvc.perform(get("/api/v1/rankings")
                            .param("date", "20250907")
                            .param("size", "2")
                            .param("page", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.window").value("DAILY"))
                    .andExpect(jsonPath("$.data.date").value("20250907"))
                    .andExpect(jsonPath("$.data.page").value(1))
                    .andExpect(jsonPath("$.data.size").value(2))
                    .andExpect(jsonPath("$.data.items[0].productId").value(firstProductId.toString()))
                    .andExpect(jsonPath("$.data.items[0].name").value("사료A"))
                    .andExpect(jsonPath("$.data.items[0].price").value(10000))
                    .andExpect(jsonPath("$.data.items[0].likeCount").value(5))
                    .andExpect(jsonPath("$.data.items[0].rank").value(1))
                    .andExpect(jsonPath("$.data.items[0].score").value(3.5d))
                    .andExpect(jsonPath("$.data.items[0].brand.id").value(brandId.toString()))
                    .andExpect(jsonPath("$.data.items[0].brand.name").value("퍼피박스"))
                    .andExpect(jsonPath("$.data.items[1].productId").value(secondProductId.toString()))
                    .andExpect(jsonPath("$.data.items[1].likeCount").value(3))
                    .andExpect(jsonPath("$.data.items[1].rank").value(2));
        }

        @Test
        @DisplayName("시간별 랭킹 페이지를 반환한다")
        void getHourlyRankingsSuccess() throws Exception {
            UUID productId = UUID.randomUUID();
            UUID brandId = UUID.randomUUID();
            LocalDateTime metricHour = LocalDateTime.of(2025, 9, 7, 12, 0);
            given(rankingQueryFacade.getHourlyPage(metricHour, 1, 1)).willReturn(List.of(
                    new TopRankingProductView(productId, "사료A", 10000, brandId, "퍼피박스", 5, 1L, 2.1d)
            ));

            mockMvc.perform(get("/api/v1/rankings")
                            .param("window", "HOURLY")
                            .param("hour", "2025090712")
                            .param("size", "1")
                            .param("page", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.window").value("HOURLY"))
                    .andExpect(jsonPath("$.data.date").value("2025090712"))
                    .andExpect(jsonPath("$.data.items[0].productId").value(productId.toString()))
                    .andExpect(jsonPath("$.data.items[0].rank").value(1));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/products/{productId}/rank")
    class GetProductRank {

        @Test
        @DisplayName("오늘 랭킹에 있는 상품이면 순위와 점수를 반환한다")
        void getProductRankSuccess() throws Exception {
            UUID productId = UUID.randomUUID();
            given(rankingQueryFacade.getProductRank(productId)).willReturn(new RankingProductView(productId, 1L, 2.4d));

            mockMvc.perform(get("/api/v1/products/{productId}/rank", productId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.productId").value(productId.toString()))
                    .andExpect(jsonPath("$.data.rank").value(1))
                    .andExpect(jsonPath("$.data.score").value(2.4d));
        }

        @Test
        @DisplayName("오늘 랭킹에 없는 상품이면 rank와 score를 null로 반환한다")
        void getProductRankWhenNotRankedReturnsNulls() throws Exception {
            UUID productId = UUID.randomUUID();
            given(rankingQueryFacade.getProductRank(productId)).willReturn(new RankingProductView(productId, null, null));

            mockMvc.perform(get("/api/v1/products/{productId}/rank", productId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.productId").value(productId.toString()))
                    .andExpect(jsonPath("$.data.rank").isEmpty())
                    .andExpect(jsonPath("$.data.score").isEmpty());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/products/{productId}")
    class GetProductDetail {

        @Test
        @DisplayName("상품 상세 조회 시 상품 순위를 함께 반환한다")
        void getProductDetailWithRank() throws Exception {
            UUID productId = UUID.randomUUID();
            UUID brandId = UUID.randomUUID();
            ProductView productView = new ProductView(productId, "사료A", 10000, 10, "설명", UUID.randomUUID(), brandId, "퍼피박스", 5, null);
            given(productDetailQueryFacade.get(productId)).willReturn(ProductDetailView.from(productView, 3L));

            mockMvc.perform(get("/api/v1/products/{productId}", productId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.id").value(productId.toString()))
                    .andExpect(jsonPath("$.data.rank").value(3));
        }

        @Test
        @DisplayName("랭킹에 없는 상품이면 rank를 null로 반환한다")
        void getProductDetailWhenNotRankedReturnsNullRank() throws Exception {
            UUID productId = UUID.randomUUID();
            UUID brandId = UUID.randomUUID();
            ProductView productView = new ProductView(productId, "사료A", 10000, 10, "설명", UUID.randomUUID(), brandId, "퍼피박스", 5, null);
            given(productDetailQueryFacade.get(productId)).willReturn(ProductDetailView.from(productView, null));

            mockMvc.perform(get("/api/v1/products/{productId}", productId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.id").value(productId.toString()))
                    .andExpect(jsonPath("$.data.rank").isEmpty());
        }
    }
}

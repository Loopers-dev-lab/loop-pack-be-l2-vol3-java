package com.loopers.interfaces.api.ranking;

import com.loopers.application.behavior.BehaviorEventPublisher;
import com.loopers.application.member.MemberAuthenticationService;
import com.loopers.application.order.queue.OrderAdmissionApplicationService;
import com.loopers.application.order.queue.OrderQueueProperties;
import com.loopers.application.product.ProductApplicationService;
import com.loopers.application.product.ProductQueryFacade;
import com.loopers.application.product.PublicProductListQueryApplicationService;
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
    @DisplayName("GET /api/v1/rankings/top")
    class GetTopRankings {

        @Test
        @DisplayName("오늘 랭킹 상위 상품 목록을 반환한다")
        void getTopRankingsSuccess() throws Exception {
            UUID firstProductId = UUID.randomUUID();
            UUID secondProductId = UUID.randomUUID();
            UUID brandId = UUID.randomUUID();
            given(rankingQueryFacade.getTop(2)).willReturn(List.of(
                    new TopRankingProductView(firstProductId, "사료A", 10000, brandId, "퍼피박스", 5, 1L, 3.5d),
                    new TopRankingProductView(secondProductId, "사료B", 9000, brandId, "퍼피박스", 3, 2L, 1.2d)
            ));

            mockMvc.perform(get("/api/v1/rankings/top").param("limit", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.items[0].productId").value(firstProductId.toString()))
                    .andExpect(jsonPath("$.data.items[0].rank").value(1))
                    .andExpect(jsonPath("$.data.items[0].score").value(3.5d))
                    .andExpect(jsonPath("$.data.items[0].brand.id").value(brandId.toString()))
                    .andExpect(jsonPath("$.data.items[0].brand.name").value("퍼피박스"))
                    .andExpect(jsonPath("$.data.items[1].productId").value(secondProductId.toString()))
                    .andExpect(jsonPath("$.data.items[1].rank").value(2));
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
}

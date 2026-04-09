package com.loopers.application.product;

import com.loopers.application.product.view.ProductDetailView;
import com.loopers.application.product.view.ProductView;
import com.loopers.application.ranking.RankingApplicationService;
import com.loopers.application.ranking.RankingProductView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ProductDetailQueryFacadeTest {

    @Mock
    private ProductQueryFacade productQueryFacade;

    @Mock
    private RankingApplicationService rankingApplicationService;

    @InjectMocks
    private ProductDetailQueryFacade productDetailQueryFacade;

    @Test
    @DisplayName("상품 상세 조회 시 랭킹 정보를 함께 조합한다")
    void getWithRank() {
        UUID productId = UUID.randomUUID();
        ProductView productView = new ProductView(
                productId,
                "사료A",
                10000,
                20,
                "설명",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "퍼피박스",
                5,
                null
        );
        given(productQueryFacade.get(productId)).willReturn(productView);
        given(rankingApplicationService.getProductRank(productId)).willReturn(new RankingProductView(productId, 3L, 1.7d));

        ProductDetailView result = productDetailQueryFacade.get(productId);

        assertThat(result.product()).isEqualTo(productView);
        assertThat(result.rank()).isEqualTo(3L);
    }

    @Test
    @DisplayName("랭킹 정보가 없으면 rank는 null이다")
    void getWithoutRank() {
        UUID productId = UUID.randomUUID();
        ProductView productView = new ProductView(
                productId,
                "사료A",
                10000,
                20,
                "설명",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "퍼피박스",
                5,
                null
        );
        given(productQueryFacade.get(productId)).willReturn(productView);
        given(rankingApplicationService.getProductRank(productId)).willReturn(new RankingProductView(productId, null, null));

        ProductDetailView result = productDetailQueryFacade.get(productId);

        assertThat(result.product()).isEqualTo(productView);
        assertThat(result.rank()).isNull();
    }
}

package com.loopers.application.ranking;

import com.loopers.application.product.ProductAssembler;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.vo.Price;
import com.loopers.domain.product.vo.Stock;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.support.page.PageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RankingFacadeTest {

    RankingRepository rankingRepository = mock(RankingRepository.class);
    ProductRepository productRepository = mock(ProductRepository.class);
    BrandRepository brandRepository = mock(BrandRepository.class);
    ProductAssembler productAssembler = new ProductAssembler();
    RankingFacade rankingFacade = new RankingFacade(rankingRepository, productRepository, brandRepository, productAssembler);

    @DisplayName("getPage() 를 호출할 때, ")
    @Nested
    class GetPage {

        @DisplayName("ZSET 점수 높은 순서로 반환된 productId 순서와 RankingInfo 목록의 순서가 일치한다.")
        @Test
        void returnsRankingInfosInRankingOrder() {
            // arrange
            LocalDate date = LocalDate.of(2026, 4, 9);
            int page = 1, size = 2;

            Long brandId = 1L;
            Brand brand = mock(Brand.class);
            when(brand.getId()).thenReturn(brandId);
            when(brand.name()).thenReturn("나이키");

            Product product30 = mockProduct(30L, "상품 30", "설명 30", brandId, 10, 3000, 5L);
            Product product10 = mockProduct(10L, "상품 10", "설명 10", brandId, 5, 1000, 2L);

            // ZREVRANGE 결과: 30이 rank 1, 10이 rank 2
            List<Long> productIds = List.of(30L, 10L);
            when(rankingRepository.findProductIdsByRank(date, 0L, 2L)).thenReturn(productIds);
            when(rankingRepository.countByDate(date)).thenReturn(2L);
            // DB 는 순서 보장 안 함 - 역순으로 반환
            when(productRepository.findAllByIdIn(productIds)).thenReturn(List.of(product10, product30));
            when(brandRepository.findAllByIdIn(List.of(brandId))).thenReturn(List.of(brand));

            // act
            PageResponse<RankingInfo> result = rankingFacade.getPage(date, page, size);

            // assert
            assertThat(result.content()).hasSize(2);
            assertThat(result.content().get(0).rank()).isEqualTo(1L);
            assertThat(result.content().get(0).productId()).isEqualTo(30L);
            assertThat(result.content().get(1).rank()).isEqualTo(2L);
            assertThat(result.content().get(1).productId()).isEqualTo(10L);
        }

        @DisplayName("ZSET 가 비어있으면 빈 content 와 totalPages=0 을 반환한다.")
        @Test
        void returnsEmptyPage_whenZSetIsEmpty() {
            // arrange
            LocalDate date = LocalDate.of(2026, 4, 9);
            int page = 1, size = 20;

            when(rankingRepository.findProductIdsByRank(date, 0L, 20L)).thenReturn(List.of());

            // act
            PageResponse<RankingInfo> result = rankingFacade.getPage(date, page, size);

            // assert
            assertThat(result.content()).isEmpty();
            assertThat(result.totalPages()).isEqualTo(0);
        }

        @DisplayName("totalPages 는 count/size 기반 ceil 로 계산된다.")
        @Test
        void calculatesTotalPagesCorrectly() {
            // arrange
            LocalDate date = LocalDate.of(2026, 4, 9);
            int page = 1, size = 2;

            Long brandId = 1L;
            Brand brand = mock(Brand.class);
            when(brand.getId()).thenReturn(brandId);
            when(brand.name()).thenReturn("나이키");

            Product product1 = mockProduct(1L, "상품 1", null, brandId, 10, 1000, 0L);
            Product product2 = mockProduct(2L, "상품 2", null, brandId, 10, 2000, 0L);

            when(rankingRepository.findProductIdsByRank(date, 0L, 2L)).thenReturn(List.of(1L, 2L));
            when(rankingRepository.countByDate(date)).thenReturn(3L); // 총 3개, size=2 → totalPages=2
            when(productRepository.findAllByIdIn(List.of(1L, 2L))).thenReturn(List.of(product1, product2));
            when(brandRepository.findAllByIdIn(List.of(brandId))).thenReturn(List.of(brand));

            // act
            PageResponse<RankingInfo> result = rankingFacade.getPage(date, page, size);

            // assert
            assertThat(result.totalPages()).isEqualTo(2);
        }

        private Product mockProduct(Long id, String name, String description, Long brandId,
                                    int stockValue, int priceValue, Long likeCount) {
            Product product = mock(Product.class);
            when(product.getId()).thenReturn(id);
            when(product.name()).thenReturn(name);
            when(product.description()).thenReturn(description);
            when(product.brandId()).thenReturn(brandId);
            when(product.stock()).thenReturn(Stock.from(stockValue));
            when(product.price()).thenReturn(Price.from(priceValue));
            when(product.likeCount()).thenReturn(likeCount);
            return product;
        }
    }
}

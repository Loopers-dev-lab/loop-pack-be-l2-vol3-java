package com.loopers.application.ranking;

import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.product.ProductStatus;
import com.loopers.domain.ranking.RankingEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RankingAssembler 단위 테스트")
class RankingAssemblerTest {

    private static final LocalDate BASE_DATE = LocalDate.of(2026, 4, 11);

    private ProductFacade productFacade;
    private RankingAssembler assembler;

    @BeforeEach
    void setUp() {
        productFacade = mock(ProductFacade.class);
        assembler = new RankingAssembler(productFacade);
    }

    private ProductInfo stubProduct(Long id) {
        return new ProductInfo(id, 1L, "브랜드", "상품" + id, 10000, 9000, 2500, 0,
                ProductStatus.ON_SALE, "Y", ZonedDateTime.now());
    }

    @Nested
    @DisplayName("assemble")
    class Assemble {

        @Test
        @DisplayName("RankingEntry 목록과 상품 정보를 결합하여 RankingPageResult 를 반환한다")
        void happyPath() {
            // given
            List<RankingEntry> entries = List.of(
                    new RankingEntry(1L, 1L, 5.0),
                    new RankingEntry(2L, 2L, 3.0)
            );
            when(productFacade.findVisibleByIds(List.of(1L, 2L))).thenReturn(Map.of(
                    1L, stubProduct(1L),
                    2L, stubProduct(2L)
            ));

            // when
            RankingPageResult result = assembler.assemble(BASE_DATE, 2L, entries);

            // then
            assertThat(result.effectiveDate()).isEqualTo(BASE_DATE);
            assertThat(result.total()).isEqualTo(2L);
            assertThat(result.items()).hasSize(2);
            assertThat(result.items().get(0).rank()).isEqualTo(1L);
            assertThat(result.items().get(0).product().id()).isEqualTo(1L);
            assertThat(result.items().get(1).rank()).isEqualTo(2L);
            assertThat(result.items().get(1).product().id()).isEqualTo(2L);
        }

        @Test
        @DisplayName("삭제/숨김 상품은 응답에서 제외되고 items 수가 줄어든다")
        void visibilityFilter() {
            // given — 3개 엔트리, 2번 상품만 visible
            List<RankingEntry> entries = List.of(
                    new RankingEntry(1L, 1L, 5.0),
                    new RankingEntry(2L, 2L, 4.0),
                    new RankingEntry(3L, 3L, 3.0)
            );
            when(productFacade.findVisibleByIds(List.of(1L, 2L, 3L)))
                    .thenReturn(Map.of(2L, stubProduct(2L)));

            // when
            RankingPageResult result = assembler.assemble(BASE_DATE, 3L, entries);

            // then — 2번만 남음, 원 rank 유지
            assertThat(result.items()).hasSize(1);
            assertThat(result.items().get(0).product().id()).isEqualTo(2L);
            assertThat(result.items().get(0).rank()).isEqualTo(2L);
        }

        @Test
        @DisplayName("entries 가 비어 있으면 productFacade 를 호출하지 않고 빈 목록을 반환한다")
        void emptyEntries() {
            // when
            RankingPageResult result = assembler.assemble(BASE_DATE, 0L, List.of());

            // then
            assertThat(result.effectiveDate()).isEqualTo(BASE_DATE);
            assertThat(result.total()).isEqualTo(0L);
            assertThat(result.items()).isEmpty();
            verify(productFacade, never()).findVisibleByIds(List.of());
        }
    }
}

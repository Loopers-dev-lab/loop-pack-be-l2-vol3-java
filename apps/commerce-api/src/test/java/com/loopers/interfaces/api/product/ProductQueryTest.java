package com.loopers.interfaces.api.product;

import com.loopers.domain.product.query.ProductListCriteria;
import com.loopers.domain.product.query.ProductListQuery;
import com.loopers.domain.product.query.ProductSortOption;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductQueryTest {

    @Test
    @DisplayName("sort 값이 비어 있으면 최신순 정렬을 사용한다")
    void defaultSortIsLatest() {
        ProductListQuery query = new ProductListQuery(null, null, null, null, null, null, null, null);
        ProductListCriteria criteria = ProductListCriteria.fromPublic(query);

        assertThat(criteria.sortOption()).isEqualTo(ProductSortOption.LATEST);
    }

    @Test
    @DisplayName("허용된 sort 값은 매핑된다")
    void mapSupportedSortValues() {
        ProductListCriteria priceCriteria = ProductListCriteria.fromPublic(new ProductListQuery(null, null, null, null, null, "price", null, null));
        ProductListCriteria likesCriteria = ProductListCriteria.fromPublic(new ProductListQuery(null, null, null, null, null, "likes", null, null));
        ProductListCriteria nameCriteria = ProductListCriteria.fromPublic(new ProductListQuery(null, null, null, null, null, "name", null, null));

        assertThat(priceCriteria.sortOption()).isEqualTo(ProductSortOption.PRICE);
        assertThat(likesCriteria.sortOption()).isEqualTo(ProductSortOption.LIKES);
        assertThat(nameCriteria.sortOption()).isEqualTo(ProductSortOption.NAME);
    }

    @Test
    @DisplayName("구형 sort 값도 호환 매핑한다")
    void mapLegacySortValues() {
        ProductListCriteria priceCriteria = ProductListCriteria.fromPublic(new ProductListQuery(null, null, null, null, null, "price_asc", null, null));
        ProductListCriteria likesCriteria = ProductListCriteria.fromPublic(new ProductListQuery(null, null, null, null, null, "likes_desc", null, null));

        assertThat(priceCriteria.sortOption()).isEqualTo(ProductSortOption.PRICE);
        assertThat(likesCriteria.sortOption()).isEqualTo(ProductSortOption.LIKES);
    }

    @Test
    @DisplayName("지원하지 않는 sort 값은 400 에러를 발생시킨다")
    void invalidSortValueFailsWithBadRequest() {
        assertThatThrownBy(() -> ProductListCriteria.fromPublic(new ProductListQuery(null, null, null, null, null, "invalid", null, null)))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
    }

    @Test
    @DisplayName("리스트 쿼리는 기본 페이지 값으로 Criteria를 만든다")
    void listQueryCreatesDefaultCriteria() {
        ProductListCriteria criteria = ProductListCriteria.fromPublic(new ProductListQuery(null, null, null, null, null, null, null, null));

        assertThat(criteria.page()).isEqualTo(ProductListCriteria.DEFAULT_PAGE);
        assertThat(criteria.size()).isEqualTo(ProductListCriteria.DEFAULT_SIZE);
        assertThat(criteria.sortOption()).isEqualTo(ProductSortOption.LATEST);
    }

    @Test
    @DisplayName("페이지 값이 음수면 400 에러를 발생시킨다")
    void invalidPageValueFailsWithBadRequest() {
        assertThatThrownBy(() -> ProductListCriteria.fromPublic(new ProductListQuery(null, null, null, null, null, null, -1, 20)))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
    }

    @Test
    @DisplayName("크기가 1 미만이면 400 에러를 발생시킨다")
    void invalidSizeValueFailsWithBadRequest() {
        assertThatThrownBy(() -> ProductListCriteria.fromPublic(new ProductListQuery(null, null, null, null, null, null, 0, 0)))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
    }

    @Test
    @DisplayName("minPrice가 maxPrice보다 크면 400 에러를 발생시킨다")
    void invalidPriceRangeFailsWithBadRequest() {
        assertThatThrownBy(() -> ProductListCriteria.fromPublic(new ProductListQuery(null, null, 10_001, 10_000, null, null, null, null)))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
    }

    @Test
    @DisplayName("유저 목록 조회에서 삭제 조건을 주면 400 에러를 발생시킨다")
    void deletedFilterFailsWithBadRequestForPublicList() {
        assertThatThrownBy(() -> ProductListCriteria.fromPublic(new ProductListQuery(null, null, null, null, true, null, null, null)))
                .isInstanceOf(CoreException.class)
                .hasMessage("삭제 상품 조회 조건은 관리자만 사용할 수 있습니다.")
                .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
    }
}

package com.loopers.interfaces.api.product;

import com.loopers.domain.product.ProductService;
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

    private final ProductService productService = new ProductService();

    @Test
    @DisplayName("sort 값이 비어 있으면 최신순 정렬을 사용한다")
    void defaultSortIsLatest() {
        ProductListQuery query = new ProductListQuery(null, null, null, null);
        ProductListCriteria criteria = productService.toCriteria(query);

        assertThat(criteria.sortOption()).isEqualTo(ProductSortOption.LATEST);
    }

    @Test
    @DisplayName("허용된 sort 값은 매핑된다")
    void mapSupportedSortValues() {
        ProductListCriteria priceCriteria = productService.toCriteria(new ProductListQuery(null, "price_asc", null, null));
        ProductListCriteria likesCriteria = productService.toCriteria(new ProductListQuery(null, "likes_desc", null, null));

        assertThat(priceCriteria.sortOption()).isEqualTo(ProductSortOption.PRICE_ASC);
        assertThat(likesCriteria.sortOption()).isEqualTo(ProductSortOption.LIKES_DESC);
    }

    @Test
    @DisplayName("지원하지 않는 sort 값은 400 에러를 발생시킨다")
    void invalidSortValueFailsWithBadRequest() {
        assertThatThrownBy(() -> productService.toCriteria(new ProductListQuery(null, "invalid", null, null)))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
    }

    @Test
    @DisplayName("리스트 쿼리는 기본 페이지 값으로 Criteria를 만든다")
    void listQueryCreatesDefaultCriteria() {
        ProductListCriteria criteria = productService.toCriteria(new ProductListQuery(null, null, null, null));

        assertThat(criteria.page()).isEqualTo(ProductListCriteria.DEFAULT_PAGE);
        assertThat(criteria.size()).isEqualTo(ProductListCriteria.DEFAULT_SIZE);
        assertThat(criteria.sortOption()).isEqualTo(ProductSortOption.LATEST);
    }

    @Test
    @DisplayName("페이지 값이 음수면 400 에러를 발생시킨다")
    void invalidPageValueFailsWithBadRequest() {
        assertThatThrownBy(() -> productService.toCriteria(new ProductListQuery(null, null, -1, 20)))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
    }

    @Test
    @DisplayName("크기가 1 미만이면 400 에러를 발생시킨다")
    void invalidSizeValueFailsWithBadRequest() {
        assertThatThrownBy(() -> productService.toCriteria(new ProductListQuery(null, null, 0, 0)))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
    }
}

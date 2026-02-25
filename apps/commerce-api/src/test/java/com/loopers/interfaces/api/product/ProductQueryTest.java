package com.loopers.interfaces.api.product;

import com.loopers.interfaces.api.common.PaginationQuery;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductQueryTest {

    @Test
    @DisplayName("sort 값이 비어 있으면 최신순 정렬을 사용한다")
    void defaultSortIsLatest() {
        ProductSortType resolved = ProductSortType.from(null);
        assertThat(resolved).isEqualTo(ProductSortType.LATEST);
    }

    @Test
    @DisplayName("허용된 sort 값은 매핑된다")
    void mapSupportedSortValues() {
        assertThat(ProductSortType.from("price_asc")).isEqualTo(ProductSortType.PRICE_ASC);
        assertThat(ProductSortType.from("likes_desc")).isEqualTo(ProductSortType.LIKES_DESC);
    }

    @Test
    @DisplayName("지원하지 않는 sort 값은 400 에러를 발생시킨다")
    void invalidSortValueFailsWithBadRequest() {
        assertThatThrownBy(() -> ProductSortType.from("invalid"))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
    }

    @Test
    @DisplayName("리스트 쿼리는 기본 페이지 값으로 Pageable을 만든다")
    void listQueryCreatesDefaultPageable() {
        ProductListQuery query = new ProductListQuery(null, null, null, null);
        Pageable pageable = query.toPageable();

        assertThat(pageable.getPageNumber()).isEqualTo(PaginationQuery.DEFAULT_PAGE);
        assertThat(pageable.getPageSize()).isEqualTo(PaginationQuery.DEFAULT_SIZE);
        assertThat(pageable).isInstanceOf(PageRequest.class);
        assertThat(pageable.getSort()).isEqualTo(ProductSortType.LATEST.toSort());
    }

    @Test
    @DisplayName("페이지 값이 음수면 400 에러를 발생시킨다")
    void invalidPageValueFailsWithBadRequest() {
        assertThatThrownBy(() -> new PaginationQuery(-1, 20))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
    }

    @Test
    @DisplayName("크기가 1 미만이면 400 에러를 발생시킨다")
    void invalidSizeValueFailsWithBadRequest() {
        assertThatThrownBy(() -> new PaginationQuery(0, 0))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
    }
}

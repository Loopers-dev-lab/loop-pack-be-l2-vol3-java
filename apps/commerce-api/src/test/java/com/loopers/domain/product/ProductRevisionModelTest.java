package com.loopers.domain.product;

import com.loopers.support.enums.ProductRevisionAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ProductRevisionModel 도메인 모델 테스트")
class ProductRevisionModelTest {

    @Test
    @DisplayName("유효한 입력으로 생성 성공")
    void create_WithValidInputs_ShouldSuccess() {
        ProductRevisionModel revision = ProductRevisionModel.create(
                "product-001", 1L, ProductRevisionAction.UPDATE,
                "admin", "가격 변경",
                "{\"price\": 10000}", "{\"price\": 20000}"
        );

        assertThat(revision.getProductId()).isEqualTo("product-001");
        assertThat(revision.getRevisionSeq()).isEqualTo(1L);
        assertThat(revision.getAction()).isEqualTo(ProductRevisionAction.UPDATE);
        assertThat(revision.getChangedBy()).isEqualTo("admin");
        assertThat(revision.getChangeReason()).isEqualTo("가격 변경");
        assertThat(revision.getBeforeSnapshot()).isEqualTo("{\"price\": 10000}");
        assertThat(revision.getAfterSnapshot()).isEqualTo("{\"price\": 20000}");
    }

    @Test
    @DisplayName("CREATE action 시 beforeSnapshot은 null이다")
    void create_WithCreateAction_BeforeSnapshotShouldBeNull() {
        ProductRevisionModel revision = ProductRevisionModel.create(
                "product-001", 0L, ProductRevisionAction.CREATE,
                "admin", "상품 생성",
                null, "{\"name\": \"상품A\"}"
        );

        assertThat(revision.getAction()).isEqualTo(ProductRevisionAction.CREATE);
        assertThat(revision.getBeforeSnapshot()).isNull();
        assertThat(revision.getAfterSnapshot()).isNotNull();
    }
}

package com.loopers.domain.brand;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrandTest {

    @DisplayName("Brand 생성: 이름과 설명 VO로 브랜드를 생성한다")
    @Test
    void create_success() {
        Brand brand = Brand.create(new BrandName("LOOPERS"), new BrandDescription("브랜드 설명"));

        assertThat(brand.getName()).isEqualTo("LOOPERS");
        assertThat(brand.getDescription()).isEqualTo("브랜드 설명");
        assertThat(brand.isDeleted()).isFalse();
    }

    @DisplayName("Brand 수정: 이름과 설명을 변경할 수 있다")
    @Test
    void update_success() {
        Brand brand = Brand.create(new BrandName("OLD"), new BrandDescription("OLD_DESC"));

        brand.update(new BrandName("NEW"), new BrandDescription("NEW_DESC"));

        assertThat(brand.getName()).isEqualTo("NEW");
        assertThat(brand.getDescription()).isEqualTo("NEW_DESC");
    }

    @DisplayName("Brand 생성: name이 null이면 예외가 발생한다")
    @Test
    void create_fail_nullName() {
        assertThatThrownBy(() -> Brand.create(null, new BrandDescription("설명")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("브랜드 정보는 필수입니다");
    }

    @DisplayName("Brand 생성: description이 null이면 예외가 발생한다")
    @Test
    void create_fail_nullDescription() {
        assertThatThrownBy(() -> Brand.create(new BrandName("LOOPERS"), null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("브랜드 정보는 필수입니다");
    }

    @DisplayName("Brand 수정: name이 null이면 예외가 발생한다")
    @Test
    void update_fail_nullName() {
        Brand brand = Brand.create(new BrandName("LOOPERS"), new BrandDescription("설명"));

        assertThatThrownBy(() -> brand.update(null, new BrandDescription("새 설명")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("브랜드 정보는 필수입니다");
    }

    @DisplayName("Brand 수정: description이 null이면 예외가 발생한다")
    @Test
    void update_fail_nullDescription() {
        Brand brand = Brand.create(new BrandName("LOOPERS"), new BrandDescription("설명"));

        assertThatThrownBy(() -> brand.update(new BrandName("NEW"), null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("브랜드 정보는 필수입니다");
    }

    @DisplayName("Brand 삭제/복구: soft delete와 restore가 동작한다")
    @Test
    void delete_restore_success() {
        Brand brand = Brand.create(new BrandName("LOOPERS"), new BrandDescription("DESC"));

        brand.delete();
        assertThat(brand.isDeleted()).isTrue();
        assertThat(brand.getDeletedAt()).isNotNull();

        brand.restore();
        assertThat(brand.isDeleted()).isFalse();
        assertThat(brand.getDeletedAt()).isNull();
    }
}

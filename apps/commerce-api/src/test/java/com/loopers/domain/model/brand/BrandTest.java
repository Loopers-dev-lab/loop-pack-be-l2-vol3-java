package com.loopers.domain.model.brand;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrandTest {

    @Test
    @DisplayName("브랜드 생성 성공")
    void create_success() {
        Brand brand = Brand.create(BrandName.of("Nike"), "스포츠 브랜드");

        assertThat(brand.getId()).isNull();
        assertThat(brand.getName().getValue()).isEqualTo("Nike");
        assertThat(brand.getDescription()).isEqualTo("스포츠 브랜드");
        assertThat(brand.getCreatedAt()).isNotNull();
        assertThat(brand.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("브랜드 수정 시 새 객체 반환")
    void update_returns_new_instance() {
        Brand brand = Brand.create(BrandName.of("Nike"), "스포츠 브랜드");
        Brand updated = brand.update(BrandName.of("Adidas"), "독일 스포츠 브랜드");

        assertThat(updated.getName().getValue()).isEqualTo("Adidas");
        assertThat(updated.getDescription()).isEqualTo("독일 스포츠 브랜드");
        assertThat(brand.getName().getValue()).isEqualTo("Nike");
    }

    @Test
    @DisplayName("브랜드 삭제 시 deletedAt 설정")
    void delete_success() {
        Brand brand = Brand.create(BrandName.of("Nike"), "스포츠 브랜드");
        Brand deleted = brand.delete();

        assertThat(deleted.isDeleted()).isTrue();
        assertThat(deleted.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("이미 삭제된 브랜드 재삭제 시 예외")
    void delete_already_deleted() {
        Brand brand = Brand.create(BrandName.of("Nike"), "스포츠 브랜드");
        Brand deleted = brand.delete();

        assertThatThrownBy(deleted::delete)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("이미 삭제된 브랜드입니다.");
    }

    @Test
    @DisplayName("reconstitute로 DB에서 복원")
    void reconstitute_success() {
        LocalDateTime now = LocalDateTime.now();
        Brand brand = Brand.reconstitute(new BrandData(1L, BrandName.of("Nike"), "스포츠 브랜드", now, now, null));

        assertThat(brand.getId()).isEqualTo(1L);
        assertThat(brand.getName().getValue()).isEqualTo("Nike");
        assertThat(brand.isDeleted()).isFalse();
    }
}

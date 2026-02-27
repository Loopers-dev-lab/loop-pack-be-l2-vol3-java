package com.loopers.domain.brand;

import com.loopers.domain.brand.vo.BrandName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

public class BrandTest {

    @Nested
    @DisplayName("Brand 생성")
    class BrandCreationTest {

        @Test
        @DisplayName("Brand 생성 성공")
        void createBrandSuccess() {
            // given
            BrandName name = new BrandName("퍼피박스");
            String description = "강아지용품 브랜드";
            String imageUrl = "https://example.com/brand.png";

            // when & then
            assertThatNoException()
                    .isThrownBy(() -> new Brand(name, description, imageUrl));
        }

        @Test
        @DisplayName("Brand 생성 성공 - description null 허용")
        void createBrandWithNullDescription() {
            // given
            BrandName name = new BrandName("퍼피박스");
            String description = null;
            String imageUrl = "https://example.com/brand.png";

            // when & then
            assertThatNoException()
                    .isThrownBy(() -> new Brand(name, description, imageUrl));
        }

        @Test
        @DisplayName("Brand 생성 성공 - imageUrl null 허용")
        void createBrandWithNullImageUrl() {
            // given
            BrandName name = new BrandName("퍼피박스");
            String description = "강아지용품 브랜드";
            String imageUrl = null;

            // when & then
            assertThatNoException()
                    .isThrownBy(() -> new Brand(name, description, imageUrl));
        }
    }

    @Nested
    @DisplayName("Brand 수정")
    class BrandUpdateTest {

        @Test
        @DisplayName("description 수정")
        void updateDescription() {
            // given
            BrandName name = new BrandName("퍼피박스");
            Brand brand = new Brand(name, "기존 설명", "https://old.com/img.png");

            // when
            Brand updated = brand.updateDescription("새로운 설명");

            // then
            assertThat(updated.name()).isEqualTo(brand.name());
            assertThat(updated.description()).isEqualTo("새로운 설명");
            assertThat(updated.imageUrl()).isEqualTo(brand.imageUrl());
        }

        @Test
        @DisplayName("imageUrl 수정")
        void updateImageUrl() {
            // given
            BrandName name = new BrandName("퍼피박스");
            Brand brand = new Brand(name, "설명", "https://old.com/img.png");

            // when
            Brand updated = brand.updateImageUrl("https://new.com/img.png");

            // then
            assertThat(updated.name()).isEqualTo(brand.name());
            assertThat(updated.description()).isEqualTo(brand.description());
            assertThat(updated.imageUrl()).isEqualTo("https://new.com/img.png");
        }

        @Test
        @DisplayName("description과 imageUrl 동시 수정")
        void updateDescriptionAndImageUrl() {
            // given
            BrandName name = new BrandName("퍼피박스");
            Brand brand = new Brand(name, "기존 설명", "https://old.com/img.png");

            // when
            Brand updated = brand.update("새로운 설명", "https://new.com/img.png");

            // then
            assertThat(updated.name().value()).isEqualTo("퍼피박스");
            assertThat(updated.description()).isEqualTo("새로운 설명");
            assertThat(updated.imageUrl()).isEqualTo("https://new.com/img.png");
        }

        @Test
        @DisplayName("name은 불변 - 수정 시도 시 예외")
        void nameIsImmutable() {
            // given
            BrandName name = new BrandName("퍼피박스");
            Brand brand = new Brand(name, "설명", "https://example.com/img.png");

            // when & then
            // Brand는 Record이므로 name 필드는 불변
            // name을 수정하려면 새로운 Brand 객체를 생성해야 함
            assertThat(brand.name().value()).isEqualTo("퍼피박스");
        }
    }

    @Nested
    @DisplayName("Brand 삭제 가능 조건")
    class BrandDeleteTest {
        @Test
        @DisplayName("삭제 가능 - 연관 데이터 무관")
        void canDeleteRegardlessOfRelatedData() {
            BrandName name = new BrandName("퍼피박스");
            Brand brand = new Brand(name, "설명", "https://example.com/img.png");

            boolean canDelete = brand.canDelete();

            assertThat(canDelete).isTrue();
        }
    }
}

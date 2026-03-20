package com.loopers.domain.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ProductImageModelTest {

    private static final Long PRODUCT_ID = 1L;
    private static final String IMAGE_URL = "https://cdn.example.com/image.jpg";

    @DisplayName("상품 이미지를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("유효한 값이 주어지면, 정상적으로 생성된다.")
        @Test
        void create_whenValidValues() {
            // act
            ProductImageModel image = ProductImageModel.create(
                    PRODUCT_ID, IMAGE_URL, ImageType.MAIN, 0);

            // assert
            assertAll(
                    () -> assertThat(image.getProductId()).isEqualTo(PRODUCT_ID),
                    () -> assertThat(image.getImageUrl()).isEqualTo(IMAGE_URL),
                    () -> assertThat(image.getImageType()).isEqualTo(ImageType.MAIN),
                    () -> assertThat(image.getSortOrder()).isZero());
        }

        @DisplayName("DETAIL 타입으로 생성할 수 있다.")
        @Test
        void create_withDetailType() {
            // act
            ProductImageModel image = ProductImageModel.create(
                    PRODUCT_ID, IMAGE_URL, ImageType.DETAIL, 3);

            // assert
            assertAll(
                    () -> assertThat(image.getImageType()).isEqualTo(ImageType.DETAIL),
                    () -> assertThat(image.getSortOrder()).isEqualTo(3));
        }

        @DisplayName("imageUrl이 null이면 예외가 발생한다.")
        @Test
        void create_whenImageUrlIsNull() {
            assertThatThrownBy(() -> ProductImageModel.create(
                    PRODUCT_ID, null, ImageType.MAIN, 0))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("이미지 URL은 필수값입니다.");
        }

        @DisplayName("imageUrl이 빈 문자열이면 예외가 발생한다.")
        @Test
        void create_whenImageUrlIsBlank() {
            assertThatThrownBy(() -> ProductImageModel.create(
                    PRODUCT_ID, "  ", ImageType.MAIN, 0))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("이미지 URL은 필수값입니다.");
        }

        @DisplayName("productId가 null이면 예외가 발생한다.")
        @Test
        void create_whenProductIdIsNull() {
            assertThatThrownBy(() -> ProductImageModel.create(
                    null, IMAGE_URL, ImageType.MAIN, 0))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("상품 ID는 필수값입니다.");
        }

        @DisplayName("imageType이 null이면 예외가 발생한다.")
        @Test
        void create_whenImageTypeIsNull() {
            assertThatThrownBy(() -> ProductImageModel.create(
                    PRODUCT_ID, IMAGE_URL, null, 0))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("이미지 타입은 필수값입니다.");
        }

        @DisplayName("sortOrder가 음수이면 예외가 발생한다.")
        @Test
        void create_whenSortOrderIsNegative() {
            assertThatThrownBy(() -> ProductImageModel.create(
                    PRODUCT_ID, IMAGE_URL, ImageType.MAIN, -1))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("정렬 순서는 0 이상이어야 합니다.");
        }
    }
}

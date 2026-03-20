package com.loopers.domain.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ProductModelTest {

    private static final Long BRAND_ID = 1L;

    @DisplayName("상품을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("유효한 값이 주어지면, 정상적으로 생성된다.")
        @Test
        void create_whenValidValues() {
            // act
            ProductModel product = ProductModel.create(BRAND_ID, "에어맥스", 150000, 100);

            // assert
            assertThat(product.getBrandId()).isEqualTo(BRAND_ID);
            assertThat(product.getName()).isEqualTo("에어맥스");
            assertThat(product.getPrice()).isEqualTo(150000);
            assertThat(product.getStock()).isEqualTo(100);
        }

        @DisplayName("브랜드 ID가 null이면 예외가 발생한다.")
        @Test
        void create_whenBrandIdIsNull() {
            assertThatThrownBy(() -> ProductModel.create(null, "에어맥스", 150000, 100))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("브랜드 ID는 필수값입니다.");
        }

        @DisplayName("상품명이 null이면 예외가 발생한다.")
        @Test
        void create_whenNameIsNull() {
            assertThatThrownBy(() -> ProductModel.create(BRAND_ID, null, 150000, 100))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("상품명은 필수값입니다.");
        }

        @DisplayName("상품명이 빈 문자열이면 예외가 발생한다.")
        @Test
        void create_whenNameIsBlank() {
            assertThatThrownBy(() -> ProductModel.create(BRAND_ID, "  ", 150000, 100))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("상품명은 필수값입니다.");
        }

        @DisplayName("상품명이 100자 이상이면 예외가 발생한다.")
        @Test
        void create_whenNameTooLong() {
            String longName = "a".repeat(100);

            assertThatThrownBy(() -> ProductModel.create(BRAND_ID, longName, 150000, 100))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("상품명은 99자 이하여야 합니다.");
        }

        @DisplayName("가격이 음수이면 예외가 발생한다.")
        @Test
        void create_whenPriceIsNegative() {
            assertThatThrownBy(() -> ProductModel.create(BRAND_ID, "에어맥스", -1, 100))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("가격은 0 이상이어야 합니다.");
        }

        @DisplayName("재고가 음수이면 예외가 발생한다.")
        @Test
        void create_whenStockIsNegative() {
            assertThatThrownBy(() -> ProductModel.create(BRAND_ID, "에어맥스", 150000, -1))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("재고는 0 이상이어야 합니다.");
        }
    }

    @DisplayName("상품을 수정할 때, ")
    @Nested
    class Update {

        @DisplayName("유효한 값이 주어지면, 정상적으로 수정된다.")
        @Test
        void update_whenValidValues() {
            // arrange
            ProductModel product = ProductModel.create(BRAND_ID, "에어맥스", 150000, 100);

            // act
            product.update("에어포스", 120000, 50);

            // assert
            assertThat(product.getName()).isEqualTo("에어포스");
            assertThat(product.getPrice()).isEqualTo(120000);
            assertThat(product.getStock()).isEqualTo(50);
        }

        @DisplayName("상품명이 null이면 예외가 발생한다.")
        @Test
        void update_whenNameIsNull() {
            // arrange
            ProductModel product = ProductModel.create(BRAND_ID, "에어맥스", 150000, 100);

            // act & assert
            assertThatThrownBy(() -> product.update(null, 120000, 50))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("상품명은 필수값입니다.");
        }
    }

    @DisplayName("재고를 차감할 때, ")
    @Nested
    class DecreaseStock {

        @DisplayName("충분한 재고가 있으면 정상적으로 차감된다.")
        @Test
        void decreaseStock_whenEnough() {
            // arrange
            ProductModel product = ProductModel.create(BRAND_ID, "에어맥스", 150000, 10);

            // act
            product.decreaseStock(3);

            // assert
            assertThat(product.getStock()).isEqualTo(7);
        }

        @DisplayName("재고가 부족하면 예외가 발생한다.")
        @Test
        void decreaseStock_whenInsufficient() {
            // arrange
            ProductModel product = ProductModel.create(BRAND_ID, "에어맥스", 150000, 5);

            // act & assert
            assertThatThrownBy(() -> product.decreaseStock(6))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("재고가 부족합니다.");
        }

        @DisplayName("차감 수량이 0이면 예외가 발생한다.")
        @Test
        void decreaseStock_whenZeroQuantity() {
            // arrange
            ProductModel product = ProductModel.create(BRAND_ID, "에어맥스", 150000, 10);

            // act & assert
            assertThatThrownBy(() -> product.decreaseStock(0))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("차감 수량은 1 이상이어야 합니다.");
        }

        @DisplayName("재고와 동일한 수량을 차감하면 재고가 0이 된다.")
        @Test
        void decreaseStock_whenExactAmount() {
            // arrange
            ProductModel product = ProductModel.create(BRAND_ID, "에어맥스", 150000, 5);

            // act
            product.decreaseStock(5);

            // assert
            assertThat(product.getStock()).isZero();
        }
    }

    @DisplayName("재고를 복구할 때, ")
    @Nested
    class IncreaseStock {

        @DisplayName("수량만큼 재고가 증가한다")
        @Test
        void increaseStock_success() {
            // arrange
            ProductModel product = ProductModel.create(BRAND_ID, "에어맥스", 150000, 5);

            // act
            product.increaseStock(3);

            // assert
            assertThat(product.getStock()).isEqualTo(8);
        }

        @DisplayName("복구 수량이 0이면 예외가 발생한다.")
        @Test
        void increaseStock_whenZeroQuantity() {
            // arrange
            ProductModel product = ProductModel.create(BRAND_ID, "에어맥스", 150000, 5);

            // act & assert
            assertThatThrownBy(() -> product.increaseStock(0))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("복구 수량은 1 이상이어야 합니다.");
        }
    }

    @DisplayName("좋아요 수를 증감할 때, ")
    @Nested
    class LikeCount {

        @DisplayName("addLikeCount 호출 시 좋아요 수가 1 증가한다.")
        @Test
        void addLikeCount_increments() {
            // arrange
            ProductModel product = ProductModel.create(BRAND_ID, "에어맥스", 150000, 100);

            // act
            product.addLikeCount();

            // assert
            assertThat(product.getLikeCount()).isEqualTo(1);
        }

        @DisplayName("subtractLikeCount 호출 시 좋아요 수가 1 감소한다.")
        @Test
        void subtractLikeCount_decrements() {
            // arrange
            ProductModel product = ProductModel.create(BRAND_ID, "에어맥스", 150000, 100);
            product.addLikeCount();
            product.addLikeCount();

            // act
            product.subtractLikeCount();

            // assert
            assertThat(product.getLikeCount()).isEqualTo(1);
        }

        @DisplayName("좋아요 수가 0일 때 subtractLikeCount 호출해도 0 이하로 내려가지 않는다.")
        @Test
        void subtractLikeCount_whenZero_remainsZero() {
            // arrange
            ProductModel product = ProductModel.create(BRAND_ID, "에어맥스", 150000, 100);

            // act
            product.subtractLikeCount();

            // assert
            assertThat(product.getLikeCount()).isZero();
        }
    }

    @DisplayName("썸네일 URL을 수정할 때, ")
    @Nested
    class UpdateThumbnailUrl {

        @DisplayName("정상적으로 수정된다.")
        @Test
        void updateThumbnailUrl_success() {
            // arrange
            ProductModel product = ProductModel.create(BRAND_ID, "에어맥스", 150000, 100);

            // act
            product.updateThumbnailUrl("https://cdn.example.com/thumb.jpg");

            // assert
            assertThat(product.getThumbnailUrl()).isEqualTo("https://cdn.example.com/thumb.jpg");
        }

        @DisplayName("null로 설정할 수 있다.")
        @Test
        void updateThumbnailUrl_toNull() {
            // arrange
            ProductModel product = ProductModel.create(BRAND_ID, "에어맥스", 150000, 100);
            product.updateThumbnailUrl("https://cdn.example.com/thumb.jpg");

            // act
            product.updateThumbnailUrl(null);

            // assert
            assertThat(product.getThumbnailUrl()).isNull();
        }
    }

    @DisplayName("품절 여부를 확인할 때, ")
    @Nested
    class IsSoldOut {

        @DisplayName("재고가 0이면 품절이다.")
        @Test
        void isSoldOut_whenStockIsZero() {
            // arrange
            ProductModel product = ProductModel.create(BRAND_ID, "에어맥스", 150000, 0);

            // act & assert
            assertThat(product.isSoldOut()).isTrue();
        }

        @DisplayName("재고가 있으면 품절이 아니다.")
        @Test
        void isSoldOut_whenStockExists() {
            // arrange
            ProductModel product = ProductModel.create(BRAND_ID, "에어맥스", 150000, 1);

            // act & assert
            assertThat(product.isSoldOut()).isFalse();
        }
    }

    @DisplayName("상품을 삭제할 때, ")
    @Nested
    class Delete {

        @DisplayName("delete() 호출 시 deletedAt이 설정된다.")
        @Test
        void delete_whenCalled() {
            // arrange
            ProductModel product = ProductModel.create(BRAND_ID, "에어맥스", 150000, 100);

            // act
            product.delete();

            // assert
            assertThat(product.getDeletedAt()).isNotNull();
        }
    }

    @DisplayName("가격을 검증할 때, ")
    @Nested
    class ValidateExpectedPrice {

        @DisplayName("기대 가격과 실제 가격이 같으면 예외가 발생하지 않는다.")
        @Test
        void validateExpectedPrice_whenMatch() {
            // arrange
            ProductModel product = ProductModel.create(BRAND_ID, "에어맥스", 150000, 100);

            // act & assert — 예외 없이 통과
            product.validateExpectedPrice(150000);
        }

        @DisplayName("기대 가격과 실제 가격이 다르면 예외가 발생한다.")
        @Test
        void validateExpectedPrice_whenMismatch() {
            // arrange
            ProductModel product = ProductModel.create(BRAND_ID, "에어맥스", 150000, 100);

            // act & assert
            assertThatThrownBy(() -> product.validateExpectedPrice(120000))
                .isInstanceOf(CoreException.class);
        }
    }

    @DisplayName("경계값을 검증할 때, ")
    @Nested
    class BoundaryValues {

        @DisplayName("가격이 0이면 정상 생성된다.")
        @Test
        void create_whenPriceIsZero() {
            // act
            ProductModel product = ProductModel.create(BRAND_ID, "무료상품", 0, 100);

            // assert
            assertThat(product.getPrice()).isZero();
        }

        @DisplayName("재고가 0이면 정상 생성되고 품절이다.")
        @Test
        void create_whenStockIsZero() {
            // act
            ProductModel product = ProductModel.create(BRAND_ID, "에어맥스", 150000, 0);

            // assert
            assertThat(product.isSoldOut()).isTrue();
        }

        @DisplayName("상품명이 99자이면 정상 생성된다.")
        @Test
        void create_whenNameIs99Chars() {
            // act
            ProductModel product = ProductModel.create(BRAND_ID, "a".repeat(99), 150000, 100);

            // assert
            assertThat(product.getName()).hasSize(99);
        }
    }
}

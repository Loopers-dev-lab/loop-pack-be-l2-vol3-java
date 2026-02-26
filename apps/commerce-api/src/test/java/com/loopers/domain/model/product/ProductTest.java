package com.loopers.domain.model.product;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductTest {

    private Product createProduct() {
        return Product.create(
                1L,
                ProductName.of("에어맥스 90"),
                Price.of(139000),
                null,
                Stock.of(50),
                "나이키 에어맥스 90"
        );
    }

    @Test
    @DisplayName("상품 생성 성공")
    void create_success() {
        Product product = createProduct();

        assertThat(product.getId()).isNull();
        assertThat(product.getBrandId()).isEqualTo(1L);
        assertThat(product.getName().getValue()).isEqualTo("에어맥스 90");
        assertThat(product.getPrice().getValue()).isEqualTo(139000);
        assertThat(product.getStock().getValue()).isEqualTo(50);
        assertThat(product.getLikeCount()).isEqualTo(0);
        assertThat(product.isDeleted()).isFalse();
        assertThat(product.isOnSale()).isFalse();
        assertThat(product.getDiscountRate()).isEqualTo(0);
    }

    @Test
    @DisplayName("세일 상품 생성")
    void create_withSalePrice() {
        Product product = Product.create(
                1L,
                ProductName.of("에어맥스 90"),
                Price.of(139000),
                Price.of(99000),
                Stock.of(50),
                "나이키 에어맥스 90"
        );

        assertThat(product.isOnSale()).isTrue();
        assertThat(product.getSalePrice().getValue()).isEqualTo(99000);
        assertThat(product.getDiscountRate()).isEqualTo(28); // (139000-99000)*100/139000 = 28
    }

    @Test
    @DisplayName("상품 수정 시 brandId 변경 불가 (update에 brandId 파라미터 없음)")
    void update_without_brandId() {
        Product product = createProduct();
        Product updated = product.update(
                ProductName.of("에어맥스 95"),
                Price.of(159000),
                null,
                Stock.of(30),
                "나이키 에어맥스 95"
        );

        assertThat(updated.getBrandId()).isEqualTo(1L);
        assertThat(updated.getName().getValue()).isEqualTo("에어맥스 95");
        assertThat(updated.getPrice().getValue()).isEqualTo(159000);
    }

    @Test
    @DisplayName("상품 삭제 (Soft Delete)")
    void delete_success() {
        Product product = createProduct();
        Product deleted = product.delete();

        assertThat(deleted.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("이미 삭제된 상품 재삭제 시 예외")
    void delete_already_deleted() {
        Product product = createProduct();
        Product deleted = product.delete();

        assertThatThrownBy(deleted::delete)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("이미 삭제된 상품입니다.");
    }

    @Test
    @DisplayName("재고 차감 성공")
    void decreaseStock_success() {
        Product product = createProduct();
        Product decreased = product.decreaseStock(5);

        assertThat(decreased.getStock().getValue()).isEqualTo(45);
    }

    @Test
    @DisplayName("재고 부족 시 차감 예외")
    void decreaseStock_fail_insufficient() {
        Product product = createProduct();

        assertThatThrownBy(() -> product.decreaseStock(51))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("재고가 부족합니다");
    }

    @Test
    @DisplayName("좋아요 수 증가")
    void increaseLikeCount() {
        Product product = createProduct();
        Product liked = product.increaseLikeCount();

        assertThat(liked.getLikeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("좋아요 수 감소")
    void decreaseLikeCount() {
        Product product = createProduct().increaseLikeCount();
        Product unliked = product.decreaseLikeCount();

        assertThat(unliked.getLikeCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("좋아요 0에서 감소 시 예외")
    void decreaseLikeCount_fail_zero() {
        Product product = createProduct();

        assertThatThrownBy(product::decreaseLikeCount)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("0 미만");
    }

    @Test
    @DisplayName("품절 여부 확인")
    void isSoldOut() {
        Product soldOut = Product.create(1L, ProductName.of("품절상품"), Price.of(10000), null,
                Stock.of(0), "설명");
        Product inStock = createProduct();

        assertThat(soldOut.isSoldOut()).isTrue();
        assertThat(inStock.isSoldOut()).isFalse();
    }
}

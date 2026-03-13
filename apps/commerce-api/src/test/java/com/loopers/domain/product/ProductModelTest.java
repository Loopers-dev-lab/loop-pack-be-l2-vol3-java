package com.loopers.domain.product;

import com.loopers.domain.BaseStringIdEntity;
import com.loopers.support.enums.DisplayStatus;
import com.loopers.support.enums.ProductSaleStatus;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ProductModel 도메인 모델 테스트")
class ProductModelTest {

    @Nested
    @DisplayName("생성 검증")
    class CreateTests {

        @Test
        @DisplayName("유효한 입력으로 생성 성공")
        void create_WithValidInputs_ShouldSuccess() {
            ProductModel product = createTestProduct();

            assertThat(product.getProductName()).isEqualTo("테스트 상품");
            assertThat(product.getBrandId()).isEqualTo(1L);
            assertThat(product.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(10000));
        }

        @Test
        @DisplayName("productName이 null이면 CoreException 발생")
        void create_WithNullProductName_ShouldThrow() {
            assertThatThrownBy(() -> ProductModel.create(
                    null, 1L, BigDecimal.valueOf(10000),
                    "설명", "카테고리", "블랙", "M", "옵션", "img.jpg", null
            )).isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("brandId가 null이면 CoreException 발생")
        void create_WithNullBrandId_ShouldThrow() {
            assertThatThrownBy(() -> ProductModel.create(
                    "상품", null, BigDecimal.valueOf(10000),
                    "설명", "카테고리", "블랙", "M", "옵션", "img.jpg", null
            )).isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("price가 음수이면 CoreException 발생")
        void create_WithNegativePrice_ShouldThrow() {
            assertThatThrownBy(() -> ProductModel.create(
                    "상품", 1L, BigDecimal.valueOf(-1),
                    "설명", "카테고리", "블랙", "M", "옵션", "img.jpg", null
            )).isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("price가 0이면 CoreException 발생")
        void create_WithZeroPrice_ShouldThrow() {
            assertThatThrownBy(() -> ProductModel.create(
                    "상품", 1L, BigDecimal.ZERO,
                    "설명", "카테고리", "블랙", "M", "옵션", "img.jpg", null
            )).isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("생성 시 displayStatus 기본값은 ACTIVE이다")
        void create_DefaultDisplayStatus_ShouldBeACTIVE() {
            ProductModel product = createTestProduct();
            assertThat(product.getDisplayStatus()).isEqualTo(DisplayStatus.ACTIVE);
        }

        @Test
        @DisplayName("생성 시 saleStatus 기본값은 ON_SALE이다")
        void create_DefaultSaleStatus_ShouldBeON_SALE() {
            ProductModel product = createTestProduct();
            assertThat(product.getSaleStatus()).isEqualTo(ProductSaleStatus.ON_SALE);
        }

        @Test
        @DisplayName("생성 시 revisionSeq 기본값은 0이다")
        void create_DefaultRevisionSeq_ShouldBe0() {
            ProductModel product = createTestProduct();
            assertThat(product.getRevisionSeq()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("수정 및 상태 변경")
    class UpdateAndStatusTests {

        @Test
        @DisplayName("updateInfo 호출 시 필드 변경 및 revisionSeq 증가")
        void updateInfo_ShouldChangeFieldsAndIncrementRevisionSeq() {
            ProductModel product = createTestProduct();
            product.updateInfo("새상품", BigDecimal.valueOf(20000), "새설명",
                    "새카테고리", "화이트", "L", "새옵션", "new.jpg", null);

            assertThat(product.getProductName()).isEqualTo("새상품");
            assertThat(product.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(20000));
            assertThat(product.getRevisionSeq()).isEqualTo(1L);
        }

        @Test
        @DisplayName("saleStatus를 TEMP_SOLD_OUT으로 변경")
        void changeSaleStatus_ToTempSoldOut_ShouldUpdate() {
            ProductModel product = createTestProduct();
            product.changeSaleStatus(ProductSaleStatus.TEMP_SOLD_OUT);
            assertThat(product.getSaleStatus()).isEqualTo(ProductSaleStatus.TEMP_SOLD_OUT);
        }

        @Test
        @DisplayName("saleStatus를 STOPPED로 변경")
        void changeSaleStatus_ToStopped_ShouldUpdate() {
            ProductModel product = createTestProduct();
            product.changeSaleStatus(ProductSaleStatus.STOPPED);
            assertThat(product.getSaleStatus()).isEqualTo(ProductSaleStatus.STOPPED);
        }

        @Test
        @DisplayName("displayStatus를 HIDDEN으로 변경")
        void changeDisplayStatus_ToHidden_ShouldUpdate() {
            ProductModel product = createTestProduct();
            product.changeDisplayStatus(DisplayStatus.HIDDEN);
            assertThat(product.getDisplayStatus()).isEqualTo(DisplayStatus.HIDDEN);
        }
    }

    @Nested
    @DisplayName("주문 가능 여부 (isOrderable)")
    class OrderableTests {

        @Test
        @DisplayName("ACTIVE + ON_SALE + 미삭제 → true")
        void isOrderable_WhenActiveAndOnSaleAndNotDeleted_ShouldReturnTrue() {
            ProductModel product = createTestProduct();
            assertThat(product.isOrderable()).isTrue();
        }

        @Test
        @DisplayName("HIDDEN → false")
        void isOrderable_WhenHidden_ShouldReturnFalse() {
            ProductModel product = createTestProduct();
            product.changeDisplayStatus(DisplayStatus.HIDDEN);
            assertThat(product.isOrderable()).isFalse();
        }

        @Test
        @DisplayName("TEMP_SOLD_OUT → false")
        void isOrderable_WhenTempSoldOut_ShouldReturnFalse() {
            ProductModel product = createTestProduct();
            product.changeSaleStatus(ProductSaleStatus.TEMP_SOLD_OUT);
            assertThat(product.isOrderable()).isFalse();
        }

        @Test
        @DisplayName("STOPPED → false")
        void isOrderable_WhenStopped_ShouldReturnFalse() {
            ProductModel product = createTestProduct();
            product.changeSaleStatus(ProductSaleStatus.STOPPED);
            assertThat(product.isOrderable()).isFalse();
        }

        @Test
        @DisplayName("삭제됨 → false")
        void isOrderable_WhenDeleted_ShouldReturnFalse() {
            ProductModel product = createTestProduct();
            product.softDelete();
            assertThat(product.isOrderable()).isFalse();
        }
    }

    @Nested
    @DisplayName("BaseStringIdEntity 상속")
    class InheritanceTests {

        @Test
        @DisplayName("BaseStringIdEntity를 상속한다")
        void create_ShouldExtendBaseStringIdEntity() {
            ProductModel product = createTestProduct();
            assertThat(product).isInstanceOf(BaseStringIdEntity.class);
        }
    }

    // === Helper ===

    private ProductModel createTestProduct() {
        return ProductModel.create(
                "테스트 상품", 1L, BigDecimal.valueOf(10000),
                "상품 설명", "카테고리", "블랙", "M", "옵션", "img.jpg", null
        );
    }
}

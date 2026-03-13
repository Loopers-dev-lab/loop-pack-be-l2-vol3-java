package com.loopers.domain.product;

import com.loopers.support.enums.ProductRevisionAction;
import com.loopers.support.enums.ProductSaleStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService 도메인 서비스 테스트")
class ProductServiceTest {

    @Mock
    ProductRepository productRepository;

    @Mock
    ProductRevisionRepository revisionRepository;

    @InjectMocks
    ProductService productService;

    // === 생성 ===

    @Nested
    @DisplayName("상품 생성")
    class CreateTests {

        @Test
        @DisplayName("유효한 입력으로 상품 + 이력이 함께 생성된다")
        void createProduct_WithValidInput_ShouldCreateProductAndRevision() {
            when(productRepository.save(any(ProductModel.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(revisionRepository.save(any(ProductRevisionModel.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            ProductModel result = productService.createProduct(
                    "테스트상품", 1L, BigDecimal.valueOf(10000), "설명");

            assertThat(result.getProductName()).isEqualTo("테스트상품");
            assertThat(result.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(10000));
            verify(productRepository).save(any(ProductModel.class));
            verify(revisionRepository).save(any(ProductRevisionModel.class));
        }

        @Test
        @DisplayName("CREATE 이력이 기록되고 beforeSnapshot이 null이다")
        void createProduct_ShouldCreateRevisionWithCREATEAction() {
            when(productRepository.save(any(ProductModel.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(revisionRepository.save(any(ProductRevisionModel.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            productService.createProduct("상품", 1L, BigDecimal.valueOf(5000), "설명");

            ArgumentCaptor<ProductRevisionModel> captor = ArgumentCaptor.forClass(ProductRevisionModel.class);
            verify(revisionRepository).save(captor.capture());
            ProductRevisionModel revision = captor.getValue();
            assertThat(revision.getAction()).isEqualTo(ProductRevisionAction.CREATE);
            assertThat(revision.getBeforeSnapshot()).isNull();
            assertThat(revision.getAfterSnapshot()).isNotNull();
        }
    }

    // === 조회 ===

    @Nested
    @DisplayName("상품 조회")
    class FindTests {

        @Test
        @DisplayName("존재하는 ID로 조회하면 ProductModel을 반환한다")
        void findById_Existing_ShouldReturn() {
            ProductModel product = createTestProduct();
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));

            ProductModel result = productService.findById(1L);

            assertThat(result.getProductName()).isEqualTo("테스트상품");
        }

        @Test
        @DisplayName("존재하지 않는 ID 조회 시 PRODUCT_NOT_FOUND 예외가 발생한다")
        void findById_NotFound_ShouldThrow() {
            when(productRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.findById(999L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType())
                            .isEqualTo(ErrorType.PRODUCT_NOT_FOUND));
        }

        @Test
        @DisplayName("주문 불가 상품 조회 시 PRODUCT_NOT_ORDERABLE 예외가 발생한다")
        void findOrderableById_WhenNotOrderable_ShouldThrow() {
            ProductModel product = createTestProduct();
            product.changeSaleStatus(ProductSaleStatus.STOPPED);
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));

            assertThatThrownBy(() -> productService.findOrderableById(1L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType())
                            .isEqualTo(ErrorType.PRODUCT_NOT_ORDERABLE));
        }

        @Test
        @DisplayName("고객용 목록 조회가 올바르게 동작한다")
        void findAllForCustomer_ShouldReturnOnlyActiveAndNotDeleted() {
            ProductModel product = createTestProduct();
            when(productRepository.findAllForCustomer(null, null))
                    .thenReturn(List.of(product));

            List<ProductModel> result = productService.findAllForCustomer(null, null);

            assertThat(result).hasSize(1);
            verify(productRepository).findAllForCustomer(null, null);
        }

        @Test
        @DisplayName("keyword 파라미터가 repository에 올바르게 전달된다")
        void findAllForCustomer_WithKeyword_ShouldFilter() {
            when(productRepository.findAllForCustomer("테스트", null))
                    .thenReturn(List.of());

            productService.findAllForCustomer("테스트", null);

            verify(productRepository).findAllForCustomer("테스트", null);
        }

        @Test
        @DisplayName("brandId 필터가 올바르게 동작한다")
        void findAllForCustomer_WithBrandId_ShouldFilter() {
            when(productRepository.findAllForCustomer(null, 1L))
                    .thenReturn(List.of());

            productService.findAllForCustomer(null, 1L);

            verify(productRepository).findAllForCustomer(null, 1L);
        }
    }

    // === 수정 ===

    @Nested
    @DisplayName("상품 수정")
    class UpdateTests {

        @Test
        @DisplayName("수정 후 UPDATE 이력이 기록되고 before/after 스냅샷이 포함된다")
        void updateProduct_ShouldUpdateAndCreateRevision() {
            ProductModel product = createTestProduct();
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(revisionRepository.save(any(ProductRevisionModel.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            ProductModel result = productService.updateProduct(
                    1L, "새상품명", BigDecimal.valueOf(20000), "새설명", "new-image.jpg");

            assertThat(result.getProductName()).isEqualTo("새상품명");
            assertThat(result.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(20000));

            ArgumentCaptor<ProductRevisionModel> captor = ArgumentCaptor.forClass(ProductRevisionModel.class);
            verify(revisionRepository).save(captor.capture());
            ProductRevisionModel revision = captor.getValue();
            assertThat(revision.getAction()).isEqualTo(ProductRevisionAction.UPDATE);
            assertThat(revision.getBeforeSnapshot()).isNotNull();
            assertThat(revision.getAfterSnapshot()).isNotNull();
        }

        @Test
        @DisplayName("수정 시 revisionSeq가 1 증가한다")
        void updateProduct_ShouldIncrementRevisionSeq() {
            ProductModel product = createTestProduct();
            assertThat(product.getRevisionSeq()).isEqualTo(0L);
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(revisionRepository.save(any(ProductRevisionModel.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            productService.updateProduct(
                    1L, "새상품명", BigDecimal.valueOf(20000), "새설명", null);

            assertThat(product.getRevisionSeq()).isEqualTo(1L);
        }

        @Test
        @DisplayName("brandId는 수정 시 변경되지 않는다")
        void updateProduct_BrandId_ShouldNotBeChangeable() {
            ProductModel product = createTestProduct();
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(revisionRepository.save(any(ProductRevisionModel.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            productService.updateProduct(
                    1L, "새상품명", BigDecimal.valueOf(20000), "새설명", null);

            assertThat(product.getBrandId()).isEqualTo(1L);
        }
    }

    // === 삭제 ===

    @Nested
    @DisplayName("상품 삭제")
    class DeleteTests {

        @Test
        @DisplayName("소프트 삭제 후 DELETE 이력이 기록된다")
        void deleteProduct_ShouldSoftDeleteAndCreateRevision() {
            ProductModel product = createTestProduct();
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(revisionRepository.save(any(ProductRevisionModel.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            productService.deleteProduct(1L);

            assertThat(product.isDeleted()).isTrue();
            ArgumentCaptor<ProductRevisionModel> captor = ArgumentCaptor.forClass(ProductRevisionModel.class);
            verify(revisionRepository).save(captor.capture());
            assertThat(captor.getValue().getAction()).isEqualTo(ProductRevisionAction.DELETE);
        }

        @Test
        @DisplayName("이미 삭제된 상품 재삭제 시 에러 없이 통과한다 (멱등)")
        void deleteProduct_AlreadyDeleted_ShouldBeIdempotent() {
            ProductModel product = createTestProduct();
            product.softDelete();
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));

            assertThatCode(() -> productService.deleteProduct(1L))
                    .doesNotThrowAnyException();
            verify(revisionRepository, never()).save(any());
        }
    }

    // === 브랜드 연쇄 삭제 ===

    @Nested
    @DisplayName("브랜드 연쇄 삭제")
    class SoftDeleteByBrandIdTests {

        @Test
        @DisplayName("해당 브랜드의 모든 상품이 소프트 삭제된다")
        void softDeleteByBrandId_ShouldDeleteAllProductsOfBrand() {
            ProductModel p1 = createTestProduct();
            ProductModel p2 = createTestProduct();
            ProductModel p3 = createTestProduct();
            when(productRepository.findAllByBrandId(1L))
                    .thenReturn(List.of(p1, p2, p3));
            when(revisionRepository.save(any(ProductRevisionModel.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            productService.softDeleteByBrandId(1L);

            assertThat(p1.isDeleted()).isTrue();
            assertThat(p2.isDeleted()).isTrue();
            assertThat(p3.isDeleted()).isTrue();
            verify(revisionRepository, times(3)).save(any(ProductRevisionModel.class));
        }

        @Test
        @DisplayName("소속 상품이 없으면 에러 없이 통과한다")
        void softDeleteByBrandId_WhenNoProducts_ShouldBeNoop() {
            when(productRepository.findAllByBrandId(1L)).thenReturn(List.of());

            assertThatCode(() -> productService.softDeleteByBrandId(1L))
                    .doesNotThrowAnyException();
            verify(revisionRepository, never()).save(any());
        }
    }

    // === 판매 상태 / 이력 ===

    @Nested
    @DisplayName("판매 상태 및 이력")
    class SaleStatusAndRevisionTests {

        @Test
        @DisplayName("판매 상태 변경 시 SALE_STATUS_CHANGE 이력이 기록된다")
        void changeSaleStatus_ShouldCreateRevision() {
            ProductModel product = createTestProduct();
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(revisionRepository.save(any(ProductRevisionModel.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            productService.changeSaleStatus(1L, ProductSaleStatus.TEMP_SOLD_OUT);

            assertThat(product.getSaleStatus()).isEqualTo(ProductSaleStatus.TEMP_SOLD_OUT);
            ArgumentCaptor<ProductRevisionModel> captor = ArgumentCaptor.forClass(ProductRevisionModel.class);
            verify(revisionRepository).save(captor.capture());
            assertThat(captor.getValue().getAction()).isEqualTo(ProductRevisionAction.SALE_STATUS_CHANGE);
        }

        @Test
        @DisplayName("이력 목록 조회가 올바르게 동작한다")
        void findRevisionsByProductId_ShouldReturnList() {
            ProductRevisionModel rev = ProductRevisionModel.create(
                    1L, 0L, ProductRevisionAction.CREATE, null, null, null, "{}");
            when(revisionRepository.findAllByProductId(1L)).thenReturn(List.of(rev));

            List<ProductRevisionModel> result = productService.findRevisionsByProductId(1L);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("특정 이력 상세 조회가 올바르게 동작한다")
        void findRevisionById_Existing_ShouldReturn() {
            ProductRevisionId id = new ProductRevisionId(1L, 0L);
            ProductRevisionModel rev = ProductRevisionModel.create(
                    1L, 0L, ProductRevisionAction.CREATE, null, null, null, "{}");
            when(revisionRepository.findById(id)).thenReturn(Optional.of(rev));

            ProductRevisionModel result = productService.findRevisionById(1L, 0L);

            assertThat(result.getAction()).isEqualTo(ProductRevisionAction.CREATE);
        }
    }

    // === Helper ===

    private ProductModel createTestProduct() {
        return ProductModel.create("테스트상품", 1L, BigDecimal.valueOf(10000),
                "설명", null, null, null, null, null, null);
    }
}

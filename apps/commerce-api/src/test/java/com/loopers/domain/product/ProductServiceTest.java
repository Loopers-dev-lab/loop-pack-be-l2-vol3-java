package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    private static final Long BRAND_ID = 1L;
    private static final String NAME = "테스트 상품";
    private static final BigDecimal PRICE = new BigDecimal("10000");
    private static final int STOCK = 10;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private BrandRepository brandRepository;

    @InjectMocks
    private ProductService productService;

    @DisplayName("register 시")
    @Nested
    class Register {

        @DisplayName("유효한 브랜드와 값이 주어지면 저장 후 상품을 반환한다.")
        @Test
        void register_withValidBrandAndInputs_shouldSaveAndReturn() {
            // given
            BrandModel brand = BrandModel.create("브랜드");
            when(brandRepository.findByIdAndNotDeleted(BRAND_ID)).thenReturn(Optional.of(brand));
            ProductModel product = ProductModel.create(BRAND_ID, NAME, PRICE, STOCK);
            when(productRepository.save(any(ProductModel.class))).thenReturn(product);

            // when
            ProductModel result = productService.register(BRAND_ID, NAME, PRICE, STOCK);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo(NAME);
            verify(productRepository).save(any(ProductModel.class));
        }

        @DisplayName("존재하지 않는 브랜드 ID면 NOT_FOUND 예외가 발생한다.")
        @Test
        void register_withNonExistentBrandId_shouldThrowNotFound() {
            // given
            when(brandRepository.findByIdAndNotDeleted(999L)).thenReturn(Optional.empty());

            // when & then
            CoreException ex = assertThrows(CoreException.class, () ->
                productService.register(999L, NAME, PRICE, STOCK));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("상품명이 null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void register_withNullName_shouldThrowBadRequest() {
            // given
            when(brandRepository.findByIdAndNotDeleted(BRAND_ID)).thenReturn(Optional.of(BrandModel.create("브랜드")));

            // when & then
            CoreException ex = assertThrows(CoreException.class, () ->
                productService.register(BRAND_ID, null, PRICE, STOCK));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("findById 시")
    @Nested
    class FindById {

        @Test
        void findById_withExistingId_shouldReturnPresent() {
            // given
            Long id = 1L;
            ProductModel product = ProductModel.create(BRAND_ID, NAME, PRICE, STOCK);
            when(productRepository.findById(id)).thenReturn(Optional.of(product));

            // when
            Optional<ProductModel> result = productService.findById(id);

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getName()).isEqualTo(NAME);
        }

        @Test
        void findById_withNonExistentId_shouldReturnEmpty() {
            when(productRepository.findById(999L)).thenReturn(Optional.empty());
            assertThat(productService.findById(999L)).isEmpty();
        }
    }

    @DisplayName("findByIdAndNotDeleted 시")
    @Nested
    class FindByIdAndNotDeleted {

        @Test
        void findByIdAndNotDeleted_withExistingNotDeleted_shouldReturnPresent() {
            Long id = 1L;
            ProductModel product = ProductModel.create(BRAND_ID, NAME, PRICE, STOCK);
            when(productRepository.findByIdAndNotDeleted(id)).thenReturn(Optional.of(product));
            assertThat(productService.findByIdAndNotDeleted(id)).isPresent();
        }

        @Test
        void findByIdAndNotDeleted_withNonExistent_shouldReturnEmpty() {
            when(productRepository.findByIdAndNotDeleted(999L)).thenReturn(Optional.empty());
            assertThat(productService.findByIdAndNotDeleted(999L)).isEmpty();
        }
    }

    @DisplayName("update 시")
    @Nested
    class Update {

        @Test
        void update_withNonExistentId_shouldThrowNotFound() {
            when(productRepository.findByIdAndNotDeleted(999L)).thenReturn(Optional.empty());
            CoreException ex = assertThrows(CoreException.class, () ->
                productService.update(999L, NAME, PRICE, STOCK));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @Test
        void update_withValidInputs_shouldUpdateAndSave() {
            Long id = 1L;
            ProductModel product = ProductModel.create(BRAND_ID, "기존명", PRICE, 5);
            when(productRepository.findByIdAndNotDeleted(id)).thenReturn(Optional.of(product));
            when(productRepository.save(product)).thenReturn(product);

            ProductModel result = productService.update(id, "새이름", new BigDecimal("20000"), 10);

            assertThat(result.getName()).isEqualTo("새이름");
            verify(productRepository).save(product);
        }

        @Test
        void update_withNullName_shouldThrowBadRequest() {
            Long id = 1L;
            ProductModel product = ProductModel.create(BRAND_ID, NAME, PRICE, STOCK);
            when(productRepository.findByIdAndNotDeleted(id)).thenReturn(Optional.of(product));
            CoreException ex = assertThrows(CoreException.class, () ->
                productService.update(id, null, PRICE, STOCK));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("validateProductAvailability 시")
    @Nested
    class ValidateProductAvailability {

        @Test
        void validateProductAvailability_whenProductNotFound_shouldThrowNotFound() {
            when(productRepository.findByIdAndNotDeleted(999L)).thenReturn(Optional.empty());
            CoreException ex = assertThrows(CoreException.class, () ->
                productService.validateProductAvailability(999L, 1, null));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @Test
        void validateProductAvailability_whenInsufficientStock_shouldThrowBadRequest() {
            Long id = 1L;
            ProductModel product = ProductModel.create(BRAND_ID, NAME, PRICE, 2);
            when(productRepository.findByIdAndNotDeleted(id)).thenReturn(Optional.of(product));
            CoreException ex = assertThrows(CoreException.class, () ->
                productService.validateProductAvailability(id, 10, null));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @Test
        void validateProductAvailability_whenValid_shouldNotThrow() {
            Long id = 1L;
            ProductModel product = ProductModel.create(BRAND_ID, NAME, PRICE, 10);
            when(productRepository.findByIdAndNotDeleted(id)).thenReturn(Optional.of(product));
            productService.validateProductAvailability(id, 5, 100L);
        }
    }

    @DisplayName("validateProducts 시")
    @Nested
    class ValidateProducts {

        @Test
        void validateProducts_whenEmpty_shouldNotThrow() {
            productService.validateProducts(List.of());
        }

        @Test
        void validateProducts_whenNull_shouldNotThrow() {
            productService.validateProducts(null);
        }

        @Test
        void validateProducts_whenOneItemInvalid_shouldThrow() {
            when(productRepository.findByIdAndNotDeleted(1L)).thenReturn(Optional.empty());
            List<ProductValidationRequest> requests = List.of(new ProductValidationRequest(1L, 1, null));
            assertThrows(CoreException.class, () -> productService.validateProducts(requests));
        }
    }

    @DisplayName("validateAndGetSnapshots 시")
    @Nested
    class ValidateAndGetSnapshots {

        @Test
        void validateAndGetSnapshots_whenNull_shouldThrowBadRequest() {
            CoreException ex = assertThrows(CoreException.class, () ->
                productService.validateAndGetSnapshots(null));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @Test
        void validateAndGetSnapshots_whenEmpty_shouldThrowBadRequest() {
            CoreException ex = assertThrows(CoreException.class, () ->
                productService.validateAndGetSnapshots(List.of()));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @Test
        void validateAndGetSnapshots_whenProductNotFound_shouldThrowNotFound() {
            when(productRepository.findByIdAndNotDeleted(1L)).thenReturn(Optional.empty());
            List<ProductValidationRequest> requests = List.of(new ProductValidationRequest(1L, 1, null));
            CoreException ex = assertThrows(CoreException.class, () ->
                productService.validateAndGetSnapshots(requests));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @Test
        void validateAndGetSnapshots_whenInsufficientStock_shouldThrowBadRequest() {
            Long id = 1L;
            ProductModel product = ProductModel.create(BRAND_ID, NAME, PRICE, 2);
            when(productRepository.findByIdAndNotDeleted(id)).thenReturn(Optional.of(product));
            List<ProductValidationRequest> requests = List.of(new ProductValidationRequest(id, 10, null));
            CoreException ex = assertThrows(CoreException.class, () ->
                productService.validateAndGetSnapshots(requests));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @Test
        void validateAndGetSnapshots_whenValid_shouldReturnSnapshots() {
            Long requestedId = 1L;
            ProductModel product = ProductModel.create(BRAND_ID, NAME, PRICE, 10);
            when(productRepository.findByIdAndNotDeleted(requestedId)).thenReturn(Optional.of(product));
            List<ProductValidationRequest> requests = List.of(new ProductValidationRequest(requestedId, 2, null));

            List<ProductSnapshot> result = productService.validateAndGetSnapshots(requests);

            assertThat(result).hasSize(1);
            // Mock된 product는 persist되지 않아 BaseEntity 기본 id(0L)를 가짐
            assertThat(result.get(0).productId()).isEqualTo(0L);
            assertThat(result.get(0).productName()).isEqualTo(NAME);
            assertThat(result.get(0).price()).isEqualByComparingTo(PRICE);
        }
    }

    @DisplayName("restoreStock 시")
    @Nested
    class RestoreStock {

        @Test
        void restoreStock_whenProductNotFound_shouldThrowNotFound() {
            when(productRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());
            List<RestoreStockItem> items = List.of(new RestoreStockItem(999L, 1));
            CoreException ex = assertThrows(CoreException.class, () -> productService.restoreStock(items));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @Test
        void restoreStock_whenEmpty_shouldNotThrow() {
            productService.restoreStock(List.of());
        }

        @Test
        void restoreStock_whenNull_shouldNotThrow() {
            productService.restoreStock(null);
        }

        @Test
        void restoreStock_whenValid_shouldIncreaseAndSave() {
            Long id = 1L;
            ProductModel product = ProductModel.create(BRAND_ID, NAME, PRICE, 5);
            when(productRepository.findByIdForUpdate(id)).thenReturn(Optional.of(product));
            when(productRepository.save(product)).thenReturn(product);

            productService.restoreStock(List.of(new RestoreStockItem(id, 3)));

            assertThat(product.getStockQuantity()).isEqualTo(8);
            verify(productRepository).save(product);
        }
    }
}

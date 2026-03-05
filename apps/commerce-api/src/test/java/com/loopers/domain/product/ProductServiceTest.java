package com.loopers.domain.product;

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
import java.util.Optional;

import static com.loopers.domain.product.ProductServiceTest.TestCommands.registerCommand;
import static com.loopers.domain.product.ProductServiceTest.TestCommands.updateCommand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService 단위 테스트")
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    @Nested
    @DisplayName("상품 등록")
    class Register {

        @Test
        @DisplayName("성공: 유효한 데이터로 상품을 등록한다")
        void register_Success() {
            // Given
            Long brandId = 1L;
            String name = "샤넬 No.5 향수";
            String description = "클래식한 샤넬의 시그니처 향수";
            BigDecimal price = new BigDecimal("150000.00");
            Integer stock = 100;
            String imageUrl = "https://example.com/chanel-no5.jpg";

            Product expectedProduct = Product.create(brandId, name, description, price, stock, imageUrl);

            given(productRepository.save(any(Product.class))).willReturn(expectedProduct);

            // When
            Product result = productService.register(registerCommand(brandId, name, description, price, stock, imageUrl));

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo(name);
            assertThat(result.getPrice()).isEqualByComparingTo(price);
            assertThat(result.getStock()).isEqualTo(stock);
            verify(productRepository).save(any(Product.class));
        }

        @Test
        @DisplayName("실패: 상품명이 null이면 BAD_REQUEST 예외를 던진다")
        void register_NameNull() {
            // Given
            Long brandId = 1L;
            BigDecimal price = new BigDecimal("10000");
            Integer stock = 10;

            // When & Then
            assertThatThrownBy(() -> productService.register(registerCommand(brandId, null, null, price, stock, null)))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST)
                    .hasMessage("상품명은 필수입니다.");
        }

        @Test
        @DisplayName("실패: 상품명이 빈 문자열이면 BAD_REQUEST 예외를 던진다")
        void register_NameEmpty() {
            // Given
            Long brandId = 1L;
            BigDecimal price = new BigDecimal("10000");
            Integer stock = 10;

            // When & Then
            assertThatThrownBy(() -> productService.register(registerCommand(brandId, "", null, price, stock, null)))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST)
                    .hasMessage("상품명은 필수입니다.");
        }

        @Test
        @DisplayName("실패: 상품명이 200자를 초과하면 BAD_REQUEST 예외를 던진다")
        void register_NameTooLong() {
            // Given
            Long brandId = 1L;
            String name = "a".repeat(201);
            BigDecimal price = new BigDecimal("10000");
            Integer stock = 10;

            // When & Then
            assertThatThrownBy(() -> productService.register(registerCommand(brandId, name, null, price, stock, null)))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST)
                    .hasMessage("상품명은 200자를 초과할 수 없습니다.");
        }

        @Test
        @DisplayName("실패: 가격이 null이면 BAD_REQUEST 예외를 던진다")
        void register_PriceNull() {
            // Given
            Long brandId = 1L;
            String name = "상품명";
            Integer stock = 10;

            // When & Then
            assertThatThrownBy(() -> productService.register(registerCommand(brandId, name, null, null, stock, null)))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST)
                    .hasMessage("가격은 필수입니다.");
        }

        @Test
        @DisplayName("실패: 가격이 0 이하면 BAD_REQUEST 예외를 던진다")
        void register_PriceZeroOrNegative() {
            // Given
            Long brandId = 1L;
            String name = "상품명";
            BigDecimal price = BigDecimal.ZERO;
            Integer stock = 10;

            // When & Then
            assertThatThrownBy(() -> productService.register(registerCommand(brandId, name, null, price, stock, null)))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST)
                    .hasMessage("가격은 0보다 커야 합니다.");
        }

        @Test
        @DisplayName("실패: 재고가 null이면 BAD_REQUEST 예외를 던진다")
        void register_StockNull() {
            // Given
            Long brandId = 1L;
            String name = "상품명";
            BigDecimal price = new BigDecimal("10000");

            // When & Then
            assertThatThrownBy(() -> productService.register(registerCommand(brandId, name, null, price, null, null)))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST)
                    .hasMessage("재고는 필수입니다.");
        }

        @Test
        @DisplayName("실패: 재고가 음수면 BAD_REQUEST 예외를 던진다")
        void register_StockNegative() {
            // Given
            Long brandId = 1L;
            String name = "상품명";
            BigDecimal price = new BigDecimal("10000");
            Integer stock = -1;

            // When & Then
            assertThatThrownBy(() -> productService.register(registerCommand(brandId, name, null, price, stock, null)))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST)
                    .hasMessage("재고는 0 이상이어야 합니다.");
        }

        @Test
        @DisplayName("실패: 설명이 2000자를 초과하면 BAD_REQUEST 예외를 던진다")
        void register_DescriptionTooLong() {
            // Given
            Long brandId = 1L;
            String name = "상품명";
            String description = "a".repeat(2001);
            BigDecimal price = new BigDecimal("10000");
            Integer stock = 10;

            // When & Then
            assertThatThrownBy(() -> productService.register(registerCommand(brandId, name, description, price, stock, null)))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST)
                    .hasMessage("설명은 2000자를 초과할 수 없습니다.");
        }

        @Test
        @DisplayName("실패: 이미지 URL이 500자를 초과하면 BAD_REQUEST 예외를 던진다")
        void register_ImageUrlTooLong() {
            // Given
            Long brandId = 1L;
            String name = "상품명";
            BigDecimal price = new BigDecimal("10000");
            Integer stock = 10;
            String imageUrl = "https://example.com/" + "a".repeat(500);

            // When & Then
            assertThatThrownBy(() -> productService.register(registerCommand(brandId, name, null, price, stock, imageUrl)))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST)
                    .hasMessage("이미지 URL은 500자를 초과할 수 없습니다.");
        }
    }

    @Nested
    @DisplayName("상품 목록 조회")
    class GetAll {

        @Test
        @DisplayName("성공: 페이지네이션으로 상품 목록을 조회한다")
        void getAll_Success() {
            // Given
            org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
            Product product1 = Product.create(1L, "상품1", null, new BigDecimal("10000"), 10, null);
            Product product2 = Product.create(1L, "상품2", null, new BigDecimal("20000"), 20, null);
            
            org.springframework.data.domain.Page<Product> expectedPage = 
                    new org.springframework.data.domain.PageImpl<>(java.util.List.of(product1, product2), pageable, 2);

            given(productRepository.findAllActive(pageable)).willReturn(expectedPage);

            // When
            org.springframework.data.domain.Page<Product> result = productService.getAll(pageable);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getTotalElements()).isEqualTo(2);
            verify(productRepository).findAllActive(pageable);
        }
    }

    @Nested
    @DisplayName("브랜드별 상품 목록 조회")
    class GetAllByBrandId {

        @Test
        @DisplayName("성공: 특정 브랜드의 상품 목록을 조회한다")
        void getAllByBrandId_Success() {
            // Given
            Long brandId = 1L;
            org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
            
            Product product1 = Product.create(brandId, "상품1", null, new BigDecimal("10000"), 10, null);
            Product product2 = Product.create(brandId, "상품2", null, new BigDecimal("20000"), 20, null);
            
            org.springframework.data.domain.Page<Product> expectedPage = 
                    new org.springframework.data.domain.PageImpl<>(java.util.List.of(product1, product2), pageable, 2);

            given(productRepository.findAllActiveByBrandId(brandId, pageable)).willReturn(expectedPage);

            // When
            org.springframework.data.domain.Page<Product> result = productService.getAllByBrandId(brandId, pageable);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getTotalElements()).isEqualTo(2);
            verify(productRepository).findAllActiveByBrandId(brandId, pageable);
        }
    }

    @Nested
    @DisplayName("상품 상세 조회")
    class GetById {

        @Test
        @DisplayName("성공: 유효한 상품 ID로 조회한다")
        void getById_Success() {
            // Given
            Long productId = 1L;
            Product product = Product.create(1L, "상품명", null, new BigDecimal("10000"), 10, null);

            given(productRepository.findActiveById(productId)).willReturn(Optional.of(product));

            // When
            Product result = productService.getById(productId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("상품명");
            verify(productRepository).findActiveById(productId);
        }

        @Test
        @DisplayName("실패: 상품이 존재하지 않으면 NOT_FOUND 예외를 던진다")
        void getById_NotFound() {
            // Given
            Long productId = 999L;

            given(productRepository.findActiveById(productId)).willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> productService.getById(productId))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.NOT_FOUND)
                    .hasMessage("상품을 찾을 수 없습니다.");
        }
    }

    @Nested
    @DisplayName("상품 수정")
    class Update {

        @Test
        @DisplayName("성공: 유효한 데이터로 상품을 수정한다")
        void update_Success() {
            // Given
            Long productId = 1L;
            Product product = Product.create(1L, "기존 상품명", "기존 설명", new BigDecimal("10000"), 10, null);

            String newName = "새 상품명";
            String newDescription = "새 설명";
            BigDecimal newPrice = new BigDecimal("20000");
            Integer newStock = 20;
            String newImageUrl = "https://example.com/new.jpg";

            given(productRepository.findActiveById(productId)).willReturn(Optional.of(product));

            // When
            Product result = productService.update(productId, updateCommand(newName, newDescription, newPrice, newStock, newImageUrl));

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo(newName);
            assertThat(result.getDescription()).isEqualTo(newDescription);
            assertThat(result.getPrice()).isEqualByComparingTo(newPrice);
            assertThat(result.getStock()).isEqualTo(newStock);
            assertThat(result.getImageUrl()).isEqualTo(newImageUrl);
            verify(productRepository).findActiveById(productId);
        }

        @Test
        @DisplayName("성공: 일부 필드만 수정한다")
        void update_PartialUpdate() {
            // Given
            Long productId = 1L;
            Product product = Product.create(1L, "기존 상품명", "기존 설명", new BigDecimal("10000"), 10, null);

            String newName = "새 상품명";

            given(productRepository.findActiveById(productId)).willReturn(Optional.of(product));

            // When
            Product result = productService.update(productId, updateCommand(newName, null, null, null, null));

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo(newName);
            verify(productRepository).findActiveById(productId);
        }

        @Test
        @DisplayName("실패: 상품이 존재하지 않으면 NOT_FOUND 예외를 던진다")
        void update_NotFound() {
            // Given
            Long productId = 999L;

            given(productRepository.findActiveById(productId)).willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> productService.update(productId, updateCommand("새 이름", null, null, null, null)))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.NOT_FOUND)
                    .hasMessage("상품을 찾을 수 없습니다.");
        }

        @Test
        @DisplayName("실패: 상품명이 빈 문자열이면 BAD_REQUEST 예외를 던진다")
        void update_NameEmpty() {
            // Given
            Long productId = 1L;
            Product product = Product.create(1L, "기존 상품명", null, new BigDecimal("10000"), 10, null);

            given(productRepository.findActiveById(productId)).willReturn(Optional.of(product));

            // When & Then
            assertThatThrownBy(() -> productService.update(productId, updateCommand("", null, null, null, null)))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST)
                    .hasMessage("상품명은 필수입니다.");
        }

        @Test
        @DisplayName("실패: 가격이 0 이하면 BAD_REQUEST 예외를 던진다")
        void update_PriceZero() {
            // Given
            Long productId = 1L;
            Product product = Product.create(1L, "상품명", null, new BigDecimal("10000"), 10, null);

            given(productRepository.findActiveById(productId)).willReturn(Optional.of(product));

            // When & Then
            assertThatThrownBy(() -> productService.update(productId, updateCommand(null, null, BigDecimal.ZERO, null, null)))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST)
                    .hasMessage("가격은 0보다 커야 합니다.");
        }

        @Test
        @DisplayName("실패: 재고가 음수면 BAD_REQUEST 예외를 던진다")
        void update_StockNegative() {
            // Given
            Long productId = 1L;
            Product product = Product.create(1L, "상품명", null, new BigDecimal("10000"), 10, null);

            given(productRepository.findActiveById(productId)).willReturn(Optional.of(product));

            // When & Then
            assertThatThrownBy(() -> productService.update(productId, updateCommand(null, null, null, -1, null)))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST)
                    .hasMessage("재고는 0 이상이어야 합니다.");
        }
    }

    static class TestCommands {
        static RegisterProductCommand registerCommand(Long brandId, String name, String description, BigDecimal price, Integer stock, String imageUrl) {
            return new RegisterProductCommand(brandId, name, description, price, stock, imageUrl);
        }

        static UpdateProductCommand updateCommand(String name, String description, BigDecimal price, Integer stock, String imageUrl) {
            return new UpdateProductCommand(name, description, price, stock, imageUrl);
        }
    }

    @Nested
    @DisplayName("상품 삭제")
    class Delete {

        @Test
        @DisplayName("성공: 유효한 상품 ID로 삭제한다")
        void delete_Success() {
            // Given
            Long productId = 1L;
            Product product = Product.create(1L, "상품명", null, new BigDecimal("10000"), 10, null);

            given(productRepository.findActiveById(productId)).willReturn(Optional.of(product));

            // When
            productService.delete(productId);

            // Then
            assertThat(product.isDeleted()).isTrue();
            verify(productRepository).findActiveById(productId);
        }

        @Test
        @DisplayName("실패: 상품이 존재하지 않으면 NOT_FOUND 예외를 던진다")
        void delete_NotFound() {
            // Given
            Long productId = 999L;

            given(productRepository.findActiveById(productId)).willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> productService.delete(productId))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.NOT_FOUND)
                    .hasMessage("상품을 찾을 수 없습니다.");
        }
    }
}

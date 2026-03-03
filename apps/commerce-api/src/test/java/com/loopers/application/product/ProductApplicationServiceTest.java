package com.loopers.application.product;

import com.loopers.application.product.command.CreateProductCommand;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.brand.vo.BrandName;
import com.loopers.domain.category.Category;
import com.loopers.domain.category.CategoryRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class ProductApplicationServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private BrandRepository brandRepository;
    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private ProductApplicationService productApplicationService;

    @Nested
    @DisplayName("상품 등록")
    class CreateTest {

        @Test
        @DisplayName("성공")
        void createSuccess() {
            UUID categoryId = UUID.randomUUID();
            UUID brandId = UUID.randomUUID();
            UUID savedId = UUID.randomUUID();
            CreateProductCommand command = new CreateProductCommand(
                    "강아지 사료",
                    10000,
                    20,
                    "소형견용",
                    categoryId,
                    brandId
            );
            when(brandRepository.findById(brandId)).thenReturn(Optional.of(new Brand(brandId, new BrandName("퍼피박스"), "", "")));
            when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(new Category(categoryId, "푸드")));

            Product saved = new Product(savedId, "강아지 사료", 10000, 20, "소형견용", categoryId, brandId, 0, null);
            when(productRepository.save(any(Product.class))).thenReturn(saved);

            Product result = productApplicationService.create(command);

            assertThat(result.id()).isEqualTo(savedId);
            assertThat(result.name()).isEqualTo("강아지 사료");
            verify(productRepository).save(any(Product.class));
        }

        @Test
        @DisplayName("실패 - 브랜드가 존재하지 않음")
        void createFailWhenBrandNotFound() {
            UUID categoryId = UUID.randomUUID();
            UUID brandId = UUID.randomUUID();
            CreateProductCommand command = new CreateProductCommand(
                    "강아지 사료",
                    10000,
                    20,
                    "소형견용",
                    categoryId,
                    brandId
            );

            when(brandRepository.findById(brandId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productApplicationService.create(command))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
            verify(productRepository, never()).save(any(Product.class));
        }

        @Test
        @DisplayName("실패 - 카테고리가 존재하지 않음")
        void createFailWhenCategoryNotFound() {
            UUID categoryId = UUID.randomUUID();
            UUID brandId = UUID.randomUUID();
            CreateProductCommand command = new CreateProductCommand(
                    "강아지 사료",
                    10000,
                    20,
                    "소형견용",
                    categoryId,
                    brandId
            );

            when(brandRepository.findById(brandId)).thenReturn(Optional.of(new Brand(brandId, new BrandName("퍼피박스"), "", "")));
            when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productApplicationService.create(command))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
            verify(productRepository, never()).save(any(Product.class));
        }

        @Test
        @DisplayName("실패 - 카테고리 ID 누락")
        void createFailWhenCategoryIdMissing() {
            CreateProductCommand command = new CreateProductCommand(
                    "강아지 사료",
                    10000,
                    20,
                    "소형견용",
                    null,
                    UUID.randomUUID()
            );

            assertThatThrownBy(() -> productApplicationService.create(command))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
            verify(productRepository, never()).save(any(Product.class));
        }

        @Test
        @DisplayName("실패 - 브랜드 ID 누락")
        void createFailWhenBrandIdMissing() {
            CreateProductCommand command = new CreateProductCommand(
                    "강아지 사료",
                    10000,
                    20,
                    "소형견용",
                    UUID.randomUUID(),
                    null
            );

            assertThatThrownBy(() -> productApplicationService.create(command))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
            verify(productRepository, never()).save(any(Product.class));
        }
    }

    @Nested
    @DisplayName("상품 조회")
    class GetTest {

        @Test
        @DisplayName("실패 - 존재하지 않는 상품")
        void getFailNotFound() {
            UUID productId = UUID.randomUUID();
            when(productRepository.findById(productId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productApplicationService.get(productId))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("상품 목록 조회")
    class ListTest {

        @Test
        @DisplayName("브랜드 필터로 조회한다")
        void listByBrand() {
            UUID categoryId = UUID.randomUUID();
            UUID brandId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            PageRequest pageable = PageRequest.of(0, 20);
            Page<Product> page = new PageImpl<>(List.of(
                    new Product(productId, "A", 1000, 5, "d1", categoryId, brandId, 0, null)
            ));
            when(productRepository.findAll(brandId, pageable)).thenReturn(page);

            Page<Product> result = productApplicationService.list(brandId, pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).brandId()).isEqualTo(brandId);
        }
    }
}

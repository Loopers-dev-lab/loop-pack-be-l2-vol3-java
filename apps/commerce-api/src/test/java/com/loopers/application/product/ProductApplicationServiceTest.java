package com.loopers.application.product;

import com.loopers.application.product.command.CreateProductCommand;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductApplicationServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductApplicationService productApplicationService;

    @Nested
    @DisplayName("상품 등록")
    class CreateTest {

        @Test
        @DisplayName("성공")
        void createSuccess() {
            CreateProductCommand command = new CreateProductCommand(
                    "강아지 사료",
                    10000,
                    20,
                    "소형견용",
                    1L,
                    1L
            );
            Product saved = new Product(1L, "강아지 사료", 10000, 20, "소형견용", 1L, 1L, 0, null);
            when(productRepository.save(any(Product.class))).thenReturn(saved);

            Product result = productApplicationService.create(command);

            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.name()).isEqualTo("강아지 사료");
            verify(productRepository).save(any(Product.class));
        }
    }

    @Nested
    @DisplayName("상품 조회")
    class GetTest {

        @Test
        @DisplayName("실패 - 존재하지 않는 상품")
        void getFailNotFound() {
            when(productRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productApplicationService.get(999L))
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
            PageRequest pageable = PageRequest.of(0, 20);
            Page<Product> page = new PageImpl<>(List.of(
                    new Product(1L, "A", 1000, 5, "d1", 1L, 10L, 0, null)
            ));
            when(productRepository.findAll(10L, pageable)).thenReturn(page);

            Page<Product> result = productApplicationService.list(10L, pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).brandId()).isEqualTo(10L);
        }
    }
}

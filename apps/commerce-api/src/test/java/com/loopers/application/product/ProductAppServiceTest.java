package com.loopers.application.product;

import com.loopers.domain.common.Money;
import com.loopers.domain.product.Option;
import com.loopers.domain.product.OptionRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSortCondition;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("ProductAppService 단위 테스트")
class ProductAppServiceTest {

    private ProductAppService productAppService;
    private ProductRepository productRepository;
    private OptionRepository optionRepository;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        optionRepository = mock(OptionRepository.class);
        productAppService = new ProductAppService(productRepository, optionRepository);
    }

    @Nested
    @DisplayName("상품 생성")
    class CreateProductTest {

        @Test
        @DisplayName("유효한 정보로 상품을 생성할 수 있다")
        void create_success() {
            // given
            Product savedProduct = Product.of(1L, 1L, "테스트 상품", Money.of(10000L), false);
            given(productRepository.save(any(Product.class))).willReturn(savedProduct);

            // when
            Product result = productAppService.create(1L, "테스트 상품", Money.of(10000L));

            // then
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getName()).isEqualTo("테스트 상품");
            verify(productRepository).save(any(Product.class));
        }
    }

    @Nested
    @DisplayName("상품 조회")
    class GetByIdTest {

        @Test
        @DisplayName("ID로 상품을 조회할 수 있다")
        void getById_found() {
            // given
            Product product = Product.of(1L, 1L, "테스트 상품", Money.of(10000L), false);
            given(productRepository.findById(1L)).willReturn(Optional.of(product));

            // when
            Product result = productAppService.getById(1L);

            // then
            assertThat(result.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("존재하지 않는 ID로 조회하면 예외가 발생한다")
        void getById_notFound() {
            // given
            given(productRepository.findById(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> productAppService.getById(999L))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("상품을 찾을 수 없습니다.");
        }
    }

    @Nested
    @DisplayName("상품 목록 조회")
    class GetProductsTest {

        @Test
        @DisplayName("정렬 조건으로 상품 목록을 조회할 수 있다")
        void getProducts() {
            // given
            List<Product> products = List.of(
                    Product.of(1L, 1L, "상품A", Money.of(10000L), false),
                    Product.of(2L, 1L, "상품B", Money.of(20000L), false)
            );
            given(productRepository.findAll(ProductSortCondition.LATEST)).willReturn(products);

            // when
            List<Product> result = productAppService.getProducts(ProductSortCondition.LATEST);

            // then
            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("옵션 조회")
    class GetOptionTest {

        @Test
        @DisplayName("ID로 옵션을 조회할 수 있다")
        void getOptionById_found() {
            // given
            Option option = Option.of(1L, 1L, "기본 옵션", Money.of(1000L), 100, false);
            given(optionRepository.findById(1L)).willReturn(Optional.of(option));

            // when
            Option result = productAppService.getOptionById(1L);

            // then
            assertThat(result.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("존재하지 않는 옵션 ID로 조회하면 예외가 발생한다")
        void getOptionById_notFound() {
            // given
            given(optionRepository.findById(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> productAppService.getOptionById(999L))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("옵션을 찾을 수 없습니다.");
        }
    }

    @Nested
    @DisplayName("배치 조회")
    class BatchQueryTest {

        @Test
        @DisplayName("상품 ID 목록으로 옵션 Map을 조회할 수 있다")
        void getOptionsByProductIds() {
            // given
            List<Long> productIds = List.of(1L, 2L);
            List<Option> options = List.of(
                    Option.of(1L, 1L, "옵션A", Money.of(0L), 10, false),
                    Option.of(2L, 1L, "옵션B", Money.of(500L), 20, false),
                    Option.of(3L, 2L, "옵션C", Money.of(1000L), 5, false)
            );
            given(optionRepository.findByProductIdIn(productIds)).willReturn(options);

            // when
            Map<Long, List<Option>> result = productAppService.getOptionsByProductIds(productIds);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(1L)).hasSize(2);
            assertThat(result.get(2L)).hasSize(1);
        }

        @Test
        @DisplayName("옵션 ID 목록으로 옵션 Map을 조회할 수 있다")
        void getOptionsByIds() {
            // given
            List<Long> optionIds = List.of(1L, 2L);
            List<Option> options = List.of(
                    Option.of(1L, 1L, "옵션A", Money.of(0L), 10, false),
                    Option.of(2L, 1L, "옵션B", Money.of(500L), 20, false)
            );
            given(optionRepository.findByIdIn(optionIds)).willReturn(options);

            // when
            Map<Long, Option> result = productAppService.getOptionsByIds(optionIds);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(1L).getName()).isEqualTo("옵션A");
            assertThat(result.get(2L).getName()).isEqualTo("옵션B");
        }

        @Test
        @DisplayName("상품 ID 목록으로 상품 Map을 조회할 수 있다")
        void getByIds() {
            // given
            List<Long> productIds = List.of(1L, 2L);
            List<Product> products = List.of(
                    Product.of(1L, 1L, "상품A", Money.of(10000L), false),
                    Product.of(2L, 1L, "상품B", Money.of(20000L), false)
            );
            given(productRepository.findByIdIn(productIds)).willReturn(products);

            // when
            Map<Long, Product> result = productAppService.getByIds(productIds);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(1L).getName()).isEqualTo("상품A");
        }
    }

    @Nested
    @DisplayName("재고 차감")
    class DecreaseStockTest {

        @Test
        @DisplayName("재고를 차감할 수 있다")
        void decreaseStock_success() {
            // given
            Option option = Option.of(1L, 1L, "기본 옵션", Money.of(0L), 100, false);
            given(optionRepository.findById(1L)).willReturn(Optional.of(option));
            given(optionRepository.save(any(Option.class))).willReturn(option);

            // when
            Option result = productAppService.decreaseStock(1L, 10);

            // then
            assertThat(result.getStock()).isEqualTo(90);
            verify(optionRepository).save(option);
        }

        @Test
        @DisplayName("재고보다 많은 수량을 차감하면 예외가 발생한다")
        void decreaseStock_insufficientStock() {
            // given
            Option option = Option.of(1L, 1L, "기본 옵션", Money.of(0L), 5, false);
            given(optionRepository.findById(1L)).willReturn(Optional.of(option));

            // when & then
            assertThatThrownBy(() -> productAppService.decreaseStock(1L, 10))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("재고가 부족합니다.");
        }
    }

    @Nested
    @DisplayName("재고 복원")
    class IncreaseStockTest {

        @Test
        @DisplayName("재고를 복원할 수 있다")
        void increaseStock_success() {
            // given
            Option option = Option.of(1L, 1L, "기본 옵션", Money.of(0L), 90, false);
            given(optionRepository.findById(1L)).willReturn(Optional.of(option));
            given(optionRepository.save(any(Option.class))).willReturn(option);

            // when
            Option result = productAppService.increaseStock(1L, 10);

            // then
            assertThat(result.getStock()).isEqualTo(100);
            verify(optionRepository).save(option);
        }
    }

    @Nested
    @DisplayName("옵션 생성")
    class CreateOptionTest {

        @Test
        @DisplayName("옵션을 생성할 수 있다")
        void createOption_success() {
            // given
            Product product = Product.of(1L, 1L, "테스트 상품", Money.of(10000L), false);
            Option savedOption = Option.of(1L, 1L, "새 옵션", Money.of(500L), 50, false);

            given(productRepository.findById(1L)).willReturn(Optional.of(product));
            given(optionRepository.save(any(Option.class))).willReturn(savedOption);

            // when
            Option result = productAppService.createOption(1L, "새 옵션", Money.of(500L), 50);

            // then
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getName()).isEqualTo("새 옵션");
            verify(optionRepository).save(any(Option.class));
        }

        @Test
        @DisplayName("존재하지 않는 상품에 옵션을 생성하면 예외가 발생한다")
        void createOption_productNotFound() {
            // given
            given(productRepository.findById(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> productAppService.createOption(999L, "옵션", Money.of(0L), 10))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("상품을 찾을 수 없습니다.");
        }
    }
}

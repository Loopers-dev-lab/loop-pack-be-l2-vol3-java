package com.loopers.domain.product;

import com.loopers.domain.brand.Brand;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
public class ProductServiceIntegrationTest {

    private static final String VALID_PRODUCT_NAME = "나이키 에어맥스";
    private static final String NEW_PRODUCT_NAME = "나이키 조던";
    private static final Money VALID_PRICE = new Money(10000);
    private static final Stock VALID_STOCK = new Stock(100);
    private static final Long NOT_EXISTED_PRODUCT_ID = 999L;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("상품 등록 시")
    @Nested
    class Register {

        @DisplayName("중복되지 않는 상품명으로 등록에 성공한다.")
        @Test
        void registersProductSucceed_whenProductNameIsUnique() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand("나이키"));

            // act
            Product result = productService.register(brand.getId(), VALID_PRODUCT_NAME, VALID_PRICE, VALID_STOCK);

            // assert
            assertThat(result.getId()).isPositive();
            assertThat(result.getName()).isEqualTo(VALID_PRODUCT_NAME);

            // DB에 실제로 저장됐는지 확인
            Product saved = productJpaRepository.findById(result.getId()).orElseThrow();
            assertThat(saved.getName()).isEqualTo(VALID_PRODUCT_NAME);
            assertThat(saved.getPrice().getAmount()).isEqualTo(VALID_PRICE.getAmount());
        }

        @DisplayName("같은 브랜드에 중복되는 상품명으로 등록하면, CONFLICT 에러가 발생한다.")
        @Test
        void registersProductFail_whenProductNameIsDuplicated() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            productJpaRepository.save(new Product(brand.getId(), VALID_PRODUCT_NAME, VALID_PRICE, VALID_STOCK));

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                    productService.register(brand.getId(), VALID_PRODUCT_NAME, VALID_PRICE, VALID_STOCK));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("상품 상세 조회 시")
    @Nested
    class FindById {

        @DisplayName("존재하는 productId로 조회하면, 상품 정보를 반환한다.")
        @Test
        void findByIdSucceed_whenProductIdExists() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product saved = productJpaRepository.save(
                    new Product(brand.getId(), VALID_PRODUCT_NAME, VALID_PRICE, VALID_STOCK));

            // act
            Product result = productService.findById(saved.getId());

            // assert
            assertThat(result.getId()).isEqualTo(saved.getId());
            assertThat(result.getName()).isEqualTo(VALID_PRODUCT_NAME);
        }

        @DisplayName("존재하지 않는 productId로 조회하면, NOT_FOUND 에러가 발생한다.")
        @Test
        void findByIdFail_whenProductIdDoesNotExist() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                    productService.findById(NOT_EXISTED_PRODUCT_ID));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("상품 정보 수정 시")
    @Nested
    class Update {

        @DisplayName("중복되지 않는 상품명으로 수정하면, 성공한다.")
        @Test
        void updateSucceed_whenProductNameIsUnique() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product product = productJpaRepository.save(
                    new Product(brand.getId(), VALID_PRODUCT_NAME, VALID_PRICE, VALID_STOCK));
            Money newPrice = new Money(20000);
            Stock newStock = new Stock(50);

            // act
            productService.update(product.getId(), NEW_PRODUCT_NAME, newPrice, newStock);

            // assert - dirty checking으로 DB에 반영됐는지 확인
            Product updated = productJpaRepository.findById(product.getId()).orElseThrow();
            assertThat(updated.getName()).isEqualTo(NEW_PRODUCT_NAME);
            assertThat(updated.getPrice().getAmount()).isEqualTo(20000);
            assertThat(updated.getStock().getQuantity()).isEqualTo(50);
        }

        @DisplayName("같은 브랜드에 중복되는 상품명으로 수정하면, CONFLICT 에러가 발생한다.")
        @Test
        void updateFail_whenProductNameIsDuplicated() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product product1 = productJpaRepository.save(
                    new Product(brand.getId(), VALID_PRODUCT_NAME, VALID_PRICE, VALID_STOCK));
            productJpaRepository.save(
                    new Product(brand.getId(), NEW_PRODUCT_NAME, VALID_PRICE, VALID_STOCK));

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                    productService.update(product1.getId(), NEW_PRODUCT_NAME, VALID_PRICE, VALID_STOCK));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("존재하지 않는 productId로 수정하면, NOT_FOUND 에러가 발생한다.")
        @Test
        void updateFail_whenProductIdDoesNotExist() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                    productService.update(NOT_EXISTED_PRODUCT_ID, VALID_PRODUCT_NAME, VALID_PRICE, VALID_STOCK));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("상품 삭제 시")
    @Nested
    class Delete {

        @DisplayName("존재하는 productId로 삭제하면, soft delete가 적용된다.")
        @Test
        void deleteSucceed_whenProductIdExists() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product product = productJpaRepository.save(
                    new Product(brand.getId(), VALID_PRODUCT_NAME, VALID_PRICE, VALID_STOCK));

            // act
            productService.delete(product.getId());

            // assert - DB에서 재조회하여 deletedAt이 설정됐는지 확인
            Product deleted = productJpaRepository.findById(product.getId()).orElseThrow();
            assertThat(deleted.getDeletedAt()).isNotNull();
        }

        @DisplayName("존재하지 않는 productId로 삭제하면, NOT_FOUND 에러가 발생한다.")
        @Test
        void deleteFail_whenProductIdDoesNotExist() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                    productService.delete(NOT_EXISTED_PRODUCT_ID));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("브랜드 삭제 시, 해당 브랜드의 모든 상품이 soft delete된다.")
        @Test
        void deleteAllByBrandIdSucceed_whenBrandIdExists() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product product1 = productJpaRepository.save(
                    new Product(brand.getId(), VALID_PRODUCT_NAME, VALID_PRICE, VALID_STOCK));
            Product product2 = productJpaRepository.save(
                    new Product(brand.getId(), NEW_PRODUCT_NAME, VALID_PRICE, VALID_STOCK));

            // act
            productService.deleteAllByBrandId(brand.getId());

            // assert
            Product deleted1 = productJpaRepository.findById(product1.getId()).orElseThrow();
            Product deleted2 = productJpaRepository.findById(product2.getId()).orElseThrow();
            assertThat(deleted1.getDeletedAt()).isNotNull();
            assertThat(deleted2.getDeletedAt()).isNotNull();
        }
    }

    @DisplayName("상품 목록 조회 시")
    @Nested
    class FindAll {

        @DisplayName("brandId 없이 조회하면, 전체 상품 목록을 반환한다.")
        @Test
        void returnsAllProducts_whenBrandIdIsNull() {
            // arrange
            Brand brand1 = brandJpaRepository.save(new Brand("나이키"));
            Brand brand2 = brandJpaRepository.save(new Brand("아디다스"));
            productJpaRepository.save(new Product(brand1.getId(), VALID_PRODUCT_NAME, VALID_PRICE, VALID_STOCK));
            productJpaRepository.save(new Product(brand2.getId(), "아디다스 운동화", VALID_PRICE, VALID_STOCK));

            // act
            Page<Product> result = productService.findAll(null,
                    PageRequest.of(0, 20, Sort.by("createdAt").descending()));

            // assert
            assertThat(result.getTotalElements()).isEqualTo(2);
        }

        @DisplayName("brandId로 필터링하면, 해당 브랜드의 상품만 반환한다.")
        @Test
        void returnsFilteredProducts_whenBrandIdIsProvided() {
            // arrange
            Brand brand1 = brandJpaRepository.save(new Brand("나이키"));
            Brand brand2 = brandJpaRepository.save(new Brand("아디다스"));
            productJpaRepository.save(new Product(brand1.getId(), VALID_PRODUCT_NAME, VALID_PRICE, VALID_STOCK));
            productJpaRepository.save(new Product(brand2.getId(), "아디다스 운동화", VALID_PRICE, VALID_STOCK));

            // act
            Page<Product> result = productService.findAll(brand1.getId(),
                    PageRequest.of(0, 20, Sort.by("createdAt").descending()));

            // assert
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getBrandId()).isEqualTo(brand1.getId());
        }
    }

    @DisplayName("재고 확인 시 (비관적 락, 차감 제외)")
    @Nested
    class FindAllAndVerifyStock {

        @DisplayName("모든 상품이 존재하고 재고가 충분하면, 재고가 차감되지 않은 상품 목록을 반환한다.")
        @Test
        void returnsProductsWithoutDecreasingStock_whenAllStocksAreSufficient() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product product1 = productJpaRepository.save(
                    new Product(brand.getId(), VALID_PRODUCT_NAME, VALID_PRICE, new Stock(10)));
            Product product2 = productJpaRepository.save(
                    new Product(brand.getId(), NEW_PRODUCT_NAME, VALID_PRICE, new Stock(5)));
            Map<Long, Quantity> quantityMap = Map.of(
                    product1.getId(), new Quantity(2),
                    product2.getId(), new Quantity(3)
            );

            // act
            List<Product> result = productService.findAllAndVerifyStock(quantityMap);

            // assert - 반환값 확인
            assertThat(result).hasSize(2);
            assertThat(result).extracting(Product::getId)
                    .containsExactlyInAnyOrder(product1.getId(), product2.getId());

            // assert - 재고 차감 없음 확인 (차감은 호출자 책임)
            assertThat(productJpaRepository.findById(product1.getId()).orElseThrow().getStock().getQuantity()).isEqualTo(10);
            assertThat(productJpaRepository.findById(product2.getId()).orElseThrow().getStock().getQuantity()).isEqualTo(5);
        }

        @DisplayName("주문 항목에 존재하지 않는 상품이 포함되면, NOT_FOUND 에러가 발생한다.")
        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            // arrange
            Long notExistedProductId = 999L;
            Map<Long, Quantity> quantityMap = Map.of(notExistedProductId, new Quantity(1));

            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> productService.findAllAndVerifyStock(quantityMap));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("재고가 부족한 상품이 포함되면, BAD_REQUEST 에러가 발생한다.")
        @Test
        void throwsBadRequest_whenStockIsInsufficient() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product product = productJpaRepository.save(
                    new Product(brand.getId(), VALID_PRODUCT_NAME, VALID_PRICE, new Stock(1)));
            Map<Long, Quantity> quantityMap = Map.of(product.getId(), new Quantity(5)); // 재고(1) < 주문(5)

            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> productService.findAllAndVerifyStock(quantityMap));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}

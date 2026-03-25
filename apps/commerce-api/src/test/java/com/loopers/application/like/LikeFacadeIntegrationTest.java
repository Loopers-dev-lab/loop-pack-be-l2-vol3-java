package com.loopers.application.like;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.Stock;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.like.LikeJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class LikeFacadeIntegrationTest {

    private static final Long USER_ID = 1L;
    private static final String BRAND_NAME = "나이키";
    private static final String PRODUCT_NAME = "나이키 에어맥스";
    private static final Money VALID_PRICE = new Money(10000);
    private static final Stock VALID_STOCK = new Stock(100);

    @Autowired
    private LikeFacade likeFacade;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private LikeJpaRepository likeJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Brand savedBrand() {
        return brandJpaRepository.save(new Brand(BRAND_NAME));
    }

    private Product savedProduct(Long brandId) {
        return productJpaRepository.save(new Product(brandId, PRODUCT_NAME, VALID_PRICE, VALID_STOCK));
    }

    @DisplayName("좋아요 등록 시 이벤트 기반 좋아요 수 증가")
    @Nested
    class CreateLikeWithEvent {

        @DisplayName("좋아요가 등록되고, 이벤트를 통해 상품의 좋아요 수가 증가한다")
        @Test
        void likeCountIncreasedViaEvent() {
            // arrange
            Brand brand = savedBrand();
            Product product = savedProduct(brand.getId());

            // act
            LikeInfo result = likeFacade.create(USER_ID, product.getId());

            // assert - 좋아요 자체는 저장됨
            assertThat(result).isNotNull();
            assertThat(result.userId()).isEqualTo(USER_ID);
            assertThat(result.productId()).isEqualTo(product.getId());

            // assert - 이벤트 처리 후 likeCount가 증가함
            Product updated = productJpaRepository.findById(product.getId()).orElseThrow();
            assertThat(updated.getLikeCount()).isEqualTo(1);
        }

        @DisplayName("좋아요 등록이 실패하면 이벤트가 발행되지 않아 좋아요 수가 변하지 않는다")
        @Test
        void likeCountNotChanged_whenLikeCreationFails() {
            // arrange
            Brand brand = savedBrand();
            Product product = savedProduct(brand.getId());
            likeFacade.create(USER_ID, product.getId()); // 첫 번째 좋아요

            // act - 중복 좋아요 시도 (CONFLICT 예외)
            try {
                likeFacade.create(USER_ID, product.getId());
            } catch (Exception ignored) {
            }

            // assert - likeCount는 1 그대로 (중복 좋아요의 이벤트는 발행 안 됨)
            Product updated = productJpaRepository.findById(product.getId()).orElseThrow();
            assertThat(updated.getLikeCount()).isEqualTo(1);
        }
    }

    @DisplayName("좋아요 취소 시 이벤트 기반 좋아요 수 감소")
    @Nested
    class DeleteLikeWithEvent {

        @DisplayName("좋아요가 취소되고, 이벤트를 통해 상품의 좋아요 수가 감소한다")
        @Test
        void likeCountDecreasedViaEvent() {
            // arrange
            Brand brand = savedBrand();
            Product product = savedProduct(brand.getId());
            likeFacade.create(USER_ID, product.getId()); // 좋아요 등록 (likeCount=1)

            // act
            likeFacade.delete(USER_ID, product.getId());

            // assert - 이벤트 처리 후 likeCount가 감소함
            Product updated = productJpaRepository.findById(product.getId()).orElseThrow();
            assertThat(updated.getLikeCount()).isEqualTo(0);
        }
    }
}

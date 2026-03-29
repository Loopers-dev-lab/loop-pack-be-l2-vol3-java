package com.loopers.application.like;

import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserDto;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandDescription;
import com.loopers.domain.brand.BrandName;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.user.Gender;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@SpringBootTest
class LikeFacadeIntegrationTest {

    @Autowired
    private LikeFacade likeFacade;

    @Autowired
    private UserFacade userFacade;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Long userId;
    private Long productId;

    @BeforeEach
    void setUp() {
        UserDto.UserInfo user = userFacade.register(
            "testuser1", "TestPass1!", "테스터",
            LocalDate.of(2000, 1, 1), "test@loopers.com", Gender.MALE
        );
        userId = user.id();

        Brand brand = brandJpaRepository.save(
            Brand.create(new BrandName("LOOPERS"), new BrandDescription("루퍼스 브랜드"))
        );

        Product product = productJpaRepository.save(
            Product.create(brand.getId(), "테스트 신발", "설명", BigDecimal.valueOf(50000), 100)
        );
        productId = product.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("좋아요를 등록할 때,")
    @Nested
    class Like {

        @DisplayName("유저와 상품이 존재하면 좋아요 등록에 성공하고 상품의 likeCount가 1 증가한다")
        @Test
        void like_success() {
            LikeDto.LikeInfo result = likeFacade.like(userId, productId);

            assertThat(result.userId()).isEqualTo(userId);
            assertThat(result.productId()).isEqualTo(productId);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                Product product = productRepository.findById(productId).orElseThrow();
                assertThat(product.getLikeCount()).isEqualTo(1);
            });
        }

        @DisplayName("같은 상품에 두 번 좋아요를 눌러도 멱등하게 성공한다")
        @Test
        void like_idempotent_whenDuplicateRequest() {
            likeFacade.like(userId, productId);
            LikeDto.LikeInfo second = likeFacade.like(userId, productId);

            assertThat(second.userId()).isEqualTo(userId);
            assertThat(second.productId()).isEqualTo(productId);
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                Product product = productRepository.findById(productId).orElseThrow();
                assertThat(product.getLikeCount()).isEqualTo(1);
            });
        }

        @DisplayName("존재하지 않는 유저 ID로 좋아요를 누르면 예외가 발생한다")
        @Test
        void like_fail_userNotFound() {
            assertThatThrownBy(() -> likeFacade.like(999L, productId))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                    .isEqualTo(ErrorType.USER_NOT_FOUND));
        }

        @DisplayName("존재하지 않는 상품 ID로 좋아요를 누르면 예외가 발생한다")
        @Test
        void like_fail_productNotFound() {
            assertThatThrownBy(() -> likeFacade.like(userId, 999L))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                    .isEqualTo(ErrorType.PRODUCT_NOT_FOUND));
        }
    }

    @DisplayName("좋아요를 취소할 때,")
    @Nested
    class Unlike {

        @DisplayName("좋아요가 존재하면 취소에 성공하고 상품의 likeCount가 0으로 돌아온다")
        @Test
        void unlike_success() {
            likeFacade.like(userId, productId);
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                Product product = productRepository.findById(productId).orElseThrow();
                assertThat(product.getLikeCount()).isEqualTo(1);
            });

            likeFacade.unlike(userId, productId);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                Product product = productRepository.findById(productId).orElseThrow();
                assertThat(product.getLikeCount()).isZero();
            });
        }

        @DisplayName("좋아요 기록이 없어도 멱등하게 성공한다")
        @Test
        void unlike_idempotent_whenLikeNotFound() {
            likeFacade.unlike(userId, productId);

            Product product = productRepository.findById(productId).orElseThrow();
            assertThat(product.getLikeCount()).isZero();
        }

        @DisplayName("존재하지 않는 상품 ID로 취소를 시도하면 예외가 발생한다")
        @Test
        void unlike_fail_productNotFound() {
            assertThatThrownBy(() -> likeFacade.unlike(userId, 999L))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                    .isEqualTo(ErrorType.PRODUCT_NOT_FOUND));
        }
    }

    @DisplayName("좋아요 취소 후 다시 좋아요를 등록할 수 있다")
    @Test
    void like_after_unlike_success() {
        likeFacade.like(userId, productId);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Product product = productRepository.findById(productId).orElseThrow();
            assertThat(product.getLikeCount()).isEqualTo(1);
        });

        likeFacade.unlike(userId, productId);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Product product = productRepository.findById(productId).orElseThrow();
            assertThat(product.getLikeCount()).isZero();
        });

        likeFacade.like(userId, productId);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Product product = productRepository.findById(productId).orElseThrow();
            assertThat(product.getLikeCount()).isEqualTo(1);
        });
    }

}

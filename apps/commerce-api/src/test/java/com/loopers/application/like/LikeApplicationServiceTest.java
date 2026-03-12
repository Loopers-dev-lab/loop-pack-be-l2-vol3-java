package com.loopers.application.like;

import com.loopers.domain.common.Money;
import com.loopers.domain.like.LikeDomainService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.Stock;
import com.loopers.fake.FakeLikeRepository;
import com.loopers.fake.FakeProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("LikeApplicationService 테스트")
class LikeApplicationServiceTest {

    private FakeLikeRepository fakeLikeRepository;
    private FakeProductRepository fakeProductRepository;
    private LikeDomainService likeDomainService;
    private LikeApplicationService likeApplicationService;

    @BeforeEach
    void setUp() {
        fakeLikeRepository = new FakeLikeRepository();
        fakeProductRepository = new FakeProductRepository();
        likeDomainService = new LikeDomainService(fakeLikeRepository);
        likeApplicationService = new LikeApplicationService(likeDomainService, fakeProductRepository);
    }

    private Product createAndSaveProduct() {
        Product product = Product.create(1L, "테스트 상품", "설명",
            new Money(10000), new Stock(100), "http://image.url");
        return fakeProductRepository.save(product);
    }

    @Nested
    @DisplayName("좋아요 등록")
    class Like {

        @Test
        @DisplayName("성공 - 상품이 존재하고 처음 좋아요하는 경우")
        void 좋아요_등록_성공() {
            // Arrange
            Product product = createAndSaveProduct();
            Long userId = 1L;

            // Act
            LikeResult result = likeApplicationService.like(userId, product.getId());

            // Assert
            assertThat(result.userId()).isEqualTo(userId);
            assertThat(result.productId()).isEqualTo(product.getId());
            assertThat(result.createdAt()).isNotNull();
        }

        @Test
        @DisplayName("실패 - 상품이 존재하지 않는 경우")
        void 상품_미존재_예외() {
            // Arrange
            Long userId = 1L;
            Long nonExistentProductId = 999L;

            // Act & Assert
            CoreException ex = assertThrows(CoreException.class,
                () -> likeApplicationService.like(userId, nonExistentProductId));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @Test
        @DisplayName("실패 - 이미 좋아요한 상품인 경우")
        void 중복_좋아요_예외() {
            // Arrange
            Product product = createAndSaveProduct();
            Long userId = 1L;
            likeApplicationService.like(userId, product.getId());

            // Act & Assert
            CoreException ex = assertThrows(CoreException.class,
                () -> likeApplicationService.like(userId, product.getId()));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @Nested
    @DisplayName("좋아요 취소")
    class Unlike {

        @Test
        @DisplayName("성공 - 좋아요가 존재하는 경우")
        void 좋아요_취소_성공() {
            // Arrange
            Product product = createAndSaveProduct();
            Long userId = 1L;
            likeApplicationService.like(userId, product.getId());

            // Act
            likeApplicationService.unlike(userId, product.getId());

            // Assert
            assertThat(fakeLikeRepository.exists(userId, product.getId())).isFalse();
        }

        @Test
        @DisplayName("성공 - 좋아요가 존재하지 않아도 멱등하게 동작")
        void 좋아요_미존재_멱등성() {
            // Arrange
            Long userId = 1L;
            Long productId = 999L;

            // Act & Assert - 예외 없이 성공
            assertDoesNotThrow(() -> likeApplicationService.unlike(userId, productId));
        }
    }
}

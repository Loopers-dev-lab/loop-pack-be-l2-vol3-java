package com.loopers.application.like;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.LikeAddedEvent;
import com.loopers.domain.like.LikeRemovedEvent;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.outbox.OutboxEventPublisher;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LikeFacadeTest {

    @Mock
    private LikeService likeService;

    @Mock
    private ProductService productService;

    @Mock
    private UserService userService;

    @Mock
    private BrandService brandService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    private LikeFacade likeFacade;

    @BeforeEach
    void setUp() {
        likeFacade = new LikeFacade(likeService, productService, userService, brandService, eventPublisher, outboxEventPublisher);
    }

    @DisplayName("좋아요 추가")
    @Nested
    class AddLike {

        @DisplayName("좋아요 추가 시 LikeAddedEvent가 발행된다.")
        @Test
        void publishesLikeAddedEvent_whenLikeAdded() {
            // Arrange
            String loginId = "testuser";
            String rawPassword = "Test1234!";
            User user = new User(loginId, "encrypted", "홍길동", "19900101", "test@example.com");
            Product product = new Product(1L, "테스트상품", 10000, 100, "설명", "img.jpg");

            given(userService.authenticate(loginId, rawPassword)).willReturn(user);
            given(productService.getProduct(1L)).willReturn(product);

            // Act
            likeFacade.addLike(loginId, rawPassword, 1L);

            // Assert
            verify(eventPublisher).publishEvent(any(LikeAddedEvent.class));
        }
    }

    @DisplayName("좋아요 취소")
    @Nested
    class RemoveLike {

        @DisplayName("좋아요 취소 시 LikeRemovedEvent가 발행된다.")
        @Test
        void publishesLikeRemovedEvent_whenLikeRemoved() {
            // Arrange
            String loginId = "testuser";
            String rawPassword = "Test1234!";
            User user = new User(loginId, "encrypted", "홍길동", "19900101", "test@example.com");
            Product product = new Product(1L, "테스트상품", 10000, 100, "설명", "img.jpg");

            given(userService.authenticate(loginId, rawPassword)).willReturn(user);
            given(productService.getProduct(1L)).willReturn(product);

            // Act
            likeFacade.removeLike(loginId, rawPassword, 1L);

            // Assert
            verify(eventPublisher).publishEvent(any(LikeRemovedEvent.class));
        }
    }
}

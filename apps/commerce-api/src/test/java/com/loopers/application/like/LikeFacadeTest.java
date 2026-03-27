package com.loopers.application.like;

import com.loopers.domain.product.Money;
import com.loopers.domain.product.ProductModel;
import com.loopers.application.product.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LikeFacadeTest {

    @InjectMocks
    private LikeFacade likeFacade;

    @Mock
    private LikeService likeService;

    @Mock
    private ProductService productService;

    @Mock
    private LikeTransactionService likeTransactionService;

    @DisplayName("좋아요 등록")
    @Nested
    class Like {

        @DisplayName("like 호출 시 likeTransactionService.doLike를 위임한다")
        @Test
        void delegatesToLikeTransactionService() {
            // arrange
            Long userId = 1L;
            Long productId = 100L;
            // act
            likeFacade.like(userId, productId);
            // assert
            verify(likeTransactionService).doLike(userId, productId);
        }
    }

    @DisplayName("좋아요 취소")
    @Nested
    class Unlike {

        @DisplayName("unlike 호출 시 likeTransactionService.doUnlike를 위임한다")
        @Test
        void delegatesToLikeTransactionService() {
            // arrange
            Long userId = 1L;
            Long productId = 100L;
            // act
            likeFacade.unlike(userId, productId);
            // assert
            verify(likeTransactionService).doUnlike(userId, productId);
        }
    }
}

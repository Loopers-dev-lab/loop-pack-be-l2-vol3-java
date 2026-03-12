package com.loopers.application.like;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class LikeFacadeTest {

    @InjectMocks
    private LikeFacade likeFacade;

    @Mock
    private LikeService likeService;

    @Mock
    private ProductService productService;

    @Mock
    private BrandService brandService;

    @DisplayName("좋아요 등록")
    @Nested
    class Register {

        @DisplayName("새로 생성되면 상품 좋아요 수를 증가시킨다")
        @Test
        void increasesLikeCountOnNewLike() {
            // arrange
            Long memberId = 1L;
            Long productId = 100L;
            ProductModel product = new ProductModel("에어맥스", "러닝화", new Money(129000), 1L);
            given(productService.getById(productId)).willReturn(product);
            given(likeService.register(memberId, productId)).willReturn(true);
            // act
            likeFacade.register(memberId, productId);
            // assert
            then(productService).should().increaseLikeCount(productId);
        }

        @DisplayName("이미 존재하면 좋아요 수를 증가시키지 않는다")
        @Test
        void doesNotIncreaseLikeCountOnExistingLike() {
            // arrange
            Long memberId = 1L;
            Long productId = 100L;
            ProductModel product = new ProductModel("에어맥스", "러닝화", new Money(129000), 1L);
            given(productService.getById(productId)).willReturn(product);
            given(likeService.register(memberId, productId)).willReturn(false);
            // act
            likeFacade.register(memberId, productId);
            // assert
            then(productService).should(never()).increaseLikeCount(productId);
        }
    }

    @DisplayName("좋아요 취소")
    @Nested
    class Cancel {

        @DisplayName("취소되면 상품 좋아요 수를 감소시킨다")
        @Test
        void decreasesLikeCountOnCancel() {
            // arrange
            Long memberId = 1L;
            Long productId = 100L;
            ProductModel product = new ProductModel("에어맥스", "러닝화", new Money(129000), 1L);
            given(productService.getById(productId)).willReturn(product);
            given(likeService.cancel(memberId, productId)).willReturn(true);
            // act
            likeFacade.cancel(memberId, productId);
            // assert
            then(productService).should().decreaseLikeCount(productId);
        }

        @DisplayName("좋아요가 없었으면 좋아요 수를 감소시키지 않는다")
        @Test
        void doesNotDecreaseLikeCountWhenNotExists() {
            // arrange
            Long memberId = 1L;
            Long productId = 100L;
            ProductModel product = new ProductModel("에어맥스", "러닝화", new Money(129000), 1L);
            given(productService.getById(productId)).willReturn(product);
            given(likeService.cancel(memberId, productId)).willReturn(false);
            // act
            likeFacade.cancel(memberId, productId);
            // assert
            then(productService).should(never()).decreaseLikeCount(productId);
        }
    }
}

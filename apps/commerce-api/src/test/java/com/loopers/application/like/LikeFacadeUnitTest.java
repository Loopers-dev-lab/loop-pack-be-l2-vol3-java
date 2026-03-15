package com.loopers.application.like;

import com.loopers.domain.like.ProductLikeService;
import com.loopers.domain.member.MemberModel;
import com.loopers.domain.member.MemberService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.infrastructure.product.ProductCacheService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LikeFacadeUnitTest {

    @Mock
    private ProductLikeService productLikeService;

    @Mock
    private MemberService memberService;

    @Mock
    private ProductService productService;

    @InjectMocks
    private LikeFacade likeFacade;

    @Mock
    private ProductCacheService productCacheService;

    private MemberModel createMember() {
        MemberModel member = new MemberModel("testuser", "password1!@", "홍길동",
                LocalDate.of(2000, 6, 5), "test@example.com");
        ReflectionTestUtils.setField(member, "id", 1L);
        return member;
    }

    private ProductModel createProduct() {
        ProductModel product = new ProductModel(1L, "에어맥스", "러닝화", 129000, 100, null);
        ReflectionTestUtils.setField(product, "id", 10L);
        return product;
    }

    @DisplayName("좋아요를 추가할 때,")
    @Nested
    class AddLike {

        @DisplayName("정상 흐름이면, 회원 인증 + 상품 검증 후 좋아요가 등록되고 likeCount가 증가한다.")
        @Test
        void addLikeSuccess() {
            // given
            MemberModel member = createMember();
            ProductModel product = createProduct();
            when(memberService.getMyInfo("testuser", "password1!@")).thenReturn(member);
            when(productService.getById(10L)).thenReturn(product);

            // when
            likeFacade.addLike("testuser", "password1!@", 10L);

            // then
            verify(productLikeService).addLike(1L, 10L);
            verify(productService).increaseLikeCount(10L);

            // 캐시 무효화 검증
            verify(productCacheService).evictProductDetail(10L);
            verify(productCacheService).evictProductList();
        }

        @DisplayName("이미 좋아요한 상품이면, CONFLICT 예외가 발생하고 likeCount는 증가하지 않는다.")
        @Test
        void failWithAlreadyLiked() {
            // given
            MemberModel member = createMember();
            ProductModel product = createProduct();
            when(memberService.getMyInfo("testuser", "password1!@")).thenReturn(member);
            when(productService.getById(10L)).thenReturn(product);
            doThrow(new CoreException(ErrorType.CONFLICT, "이미 좋아요한 상품입니다."))
                    .when(productLikeService).addLike(1L, 10L);

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                    likeFacade.addLike("testuser", "password1!@", 10L)
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
            verify(productService, never()).increaseLikeCount(10L);

            // 실패 시 캐시 무효화도 호출되면 안 됨
            verify(productCacheService, never()).evictProductDetail(10L);
            verify(productCacheService, never()).evictProductList();
        }

        @DisplayName("존재하지 않는 상품이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void failWithProductNotFound() {
            // given
            MemberModel member = createMember();
            when(memberService.getMyInfo("testuser", "password1!@")).thenReturn(member);
            when(productService.getById(999L))
                    .thenThrow(new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다."));

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                    likeFacade.addLike("testuser", "password1!@", 999L)
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("좋아요를 취소할 때,")
    @Nested
    class RemoveLike {

        @DisplayName("정상 흐름이면, 좋아요가 제거되고 likeCount가 감소한다.")
        @Test
        void removeLikeSuccess() {
            // given
            MemberModel member = createMember();
            ProductModel product = createProduct();
            when(memberService.getMyInfo("testuser", "password1!@")).thenReturn(member);
            when(productService.getById(10L)).thenReturn(product);

            // when
            likeFacade.removeLike("testuser", "password1!@", 10L);

            // then
            verify(productLikeService).removeLike(1L, 10L);
            verify(productService).decreaseLikeCount(10L);

            // 캐시 무효화 검증
            verify(productCacheService).evictProductDetail(10L);
            verify(productCacheService).evictProductList();
        }

        @DisplayName("좋아요하지 않은 상품이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void failWithNotLiked() {
            // given
            MemberModel member = createMember();
            ProductModel product = createProduct();
            when(memberService.getMyInfo("testuser", "password1!@")).thenReturn(member);
            when(productService.getById(10L)).thenReturn(product);
            doThrow(new CoreException(ErrorType.NOT_FOUND, "좋아요하지 않은 상품입니다."))
                    .when(productLikeService).removeLike(1L, 10L);

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                    likeFacade.removeLike("testuser", "password1!@", 10L)
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("좋아요 수를 조회할 때,")
    @Nested
    class CountByProductId {

        @DisplayName("상품의 좋아요 수가 반환된다.")
        @Test
        void countSuccess() {
            // given
            when(productLikeService.countByProductId(10L)).thenReturn(5L);

            // when
            long result = likeFacade.countByProductId(10L);

            // then
            assertThat(result).isEqualTo(5L);
        }
    }

}

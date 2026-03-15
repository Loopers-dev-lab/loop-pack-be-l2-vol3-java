package com.loopers.application.favorite;

import com.loopers.domain.favorite.model.FavoriteCommand;
import com.loopers.domain.favorite.service.FavoriteService;
import com.loopers.domain.member.model.Member;
import com.loopers.domain.member.service.MemberService;
import com.loopers.domain.product.model.Product;
import com.loopers.domain.product.vo.DisplayStatus;
import com.loopers.domain.product.service.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FavoriteFacadeTest {

    @InjectMocks
    private FavoriteFacade favoriteFacade;

    @Mock
    private FavoriteService favoriteService;

    @Mock
    private MemberService memberService;

    @Mock
    private ProductService productService;

    private static Member createTestMember() {
        return Member.reconstruct(1L, "testuser", "encodedPw", "홍길동", LocalDate.of(1990, 1, 1), "test@test.com");
    }

    private static Product createTestProduct() {
        return Product.reconstruct(1L, 1L, "상품A", 10000, 100, DisplayStatus.DISPLAYING, 0L);
    }

    @DisplayName("좋아요 등록")
    @Nested
    class AddFavorite {

        @DisplayName("인증에 실패하면 CoreException(UNAUTHORIZED)이 발생한다")
        @Test
        void throwsException_whenAuthFails() {
            // arrange
            when(memberService.findMember("testuser", "wrongpw"))
                    .thenThrow(new CoreException(ErrorType.UNAUTHORIZED, "인증에 실패했습니다."));

            // act & assert
            assertThatThrownBy(() -> favoriteFacade.addFavorite("testuser", "wrongpw", 1L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED));
        }

        @DisplayName("존재하지 않는 상품에 좋아요 등록 시 CoreException(NOT_FOUND)이 발생한다")
        @Test
        void throwsException_whenProductNotFound() {
            // arrange
            Member member = createTestMember();
            when(memberService.findMember("testuser", "password")).thenReturn(member);
            when(productService.findProduct(999L))
                    .thenThrow(new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다."));

            // act & assert
            assertThatThrownBy(() -> favoriteFacade.addFavorite("testuser", "password", 999L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }

        @DisplayName("이미 좋아요한 상품에 좋아요 등록 시 CoreException(CONFLICT)이 발생한다")
        @Test
        void throwsException_whenAlreadyFavorited() {
            // arrange
            Member member = createTestMember();
            Product product = createTestProduct();
            when(memberService.findMember("testuser", "password")).thenReturn(member);
            when(productService.findProduct(1L)).thenReturn(product);
            doThrow(new CoreException(ErrorType.CONFLICT, "이미 좋아요한 상품입니다."))
                    .when(favoriteService).addFavorite(any(FavoriteCommand.Add.class));

            // act & assert
            assertThatThrownBy(() -> favoriteFacade.addFavorite("testuser", "password", 1L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.CONFLICT));
        }

        @DisplayName("정상 등록 시 favoriteService.addFavorite과 productService.increaseLikeCount가 호출된다")
        @Test
        void callsAddFavoriteAndIncreaseLikeCount_onSuccess() {
            // arrange
            Member member = createTestMember();
            Product product = createTestProduct();
            when(memberService.findMember("testuser", "password")).thenReturn(member);
            when(productService.findProduct(1L)).thenReturn(product);
            when(favoriteService.addFavorite(any(FavoriteCommand.Add.class))).thenReturn(true);

            // act
            favoriteFacade.addFavorite("testuser", "password", 1L);

            // assert
            verify(favoriteService).addFavorite(any(FavoriteCommand.Add.class));
            verify(productService).increaseLikeCount(1L);
        }
    }

    @DisplayName("좋아요 취소")
    @Nested
    class DeleteFavorite {

        @DisplayName("인증에 실패하면 CoreException(UNAUTHORIZED)이 발생한다")
        @Test
        void throwsException_whenAuthFails() {
            // arrange
            when(memberService.findMember("testuser", "wrongpw"))
                    .thenThrow(new CoreException(ErrorType.UNAUTHORIZED, "인증에 실패했습니다."));

            // act & assert
            assertThatThrownBy(() -> favoriteFacade.deleteFavorite("testuser", "wrongpw", 1L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED));
        }

        @DisplayName("등록하지 않은 좋아요 취소 시 CoreException(NOT_FOUND)이 발생한다")
        @Test
        void throwsException_whenFavoriteNotFound() {
            // arrange
            Member member = createTestMember();
            Product product = createTestProduct();
            when(memberService.findMember("testuser", "password")).thenReturn(member);
            when(productService.findProduct(1L)).thenReturn(product);
            doThrow(new CoreException(ErrorType.NOT_FOUND, "좋아요 정보가 없습니다."))
                    .when(favoriteService).delete(any(FavoriteCommand.Delete.class));

            // act & assert
            assertThatThrownBy(() -> favoriteFacade.deleteFavorite("testuser", "password", 1L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }

        @DisplayName("정상 취소 시 favoriteService.delete와 productService.decreaseLikeCount가 호출된다")
        @Test
        void callsDeleteAndDecreaseLikeCount_onSuccess() {
            // arrange
            Member member = createTestMember();
            Product product = createTestProduct();
            when(memberService.findMember("testuser", "password")).thenReturn(member);
            when(productService.findProduct(1L)).thenReturn(product);

            // act
            favoriteFacade.deleteFavorite("testuser", "password", 1L);

            // assert
            verify(favoriteService).delete(any(FavoriteCommand.Delete.class));
            verify(productService).decreaseLikeCount(1L);
        }
    }
}

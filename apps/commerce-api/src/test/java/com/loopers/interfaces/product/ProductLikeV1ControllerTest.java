package com.loopers.interfaces.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loopers.application.product.ProductLikeFacade;
import com.loopers.application.product.dto.ProductLikeResult;
import com.loopers.domain.product.ProductErrorCode;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.LoginUser;
import com.loopers.interfaces.product.dto.ProductLikeV1Dto;
import com.loopers.support.error.CoreException;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("ProductLikeV1Controller 단위 테스트")
@ExtendWith(MockitoExtension.class)
class ProductLikeV1ControllerTest {

    @Mock
    private ProductLikeFacade productLikeFacade;

    @InjectMocks
    private ProductLikeV1Controller productLikeV1Controller;

    private final LoginUser loginUser = new LoginUser(1L, "testuser", "테스터");

    @DisplayName("POST /api/v1/products/{productId}/likes")
    @Nested
    class Like {

        @DisplayName("좋아요 등록 요청이면, productLikeFacade.like를 호출하고 성공 응답을 반환한다.")
        @Test
        void like_callsFacadeLike() {
            // arrange
            Long productId = 10L;
            doNothing().when(productLikeFacade).like(1L, 10L);

            // act
            ApiResponse<Object> response = productLikeV1Controller.like(loginUser, productId);

            // assert
            verify(productLikeFacade).like(1L, 10L);
            assertThat(response.meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS);
        }

        @DisplayName("존재하지 않는 상품이면, 예외가 전파된다.")
        @Test
        void like_whenProductNotFound_throwsException() {
            // arrange
            Long productId = 999L;
            doThrow(new CoreException(ProductErrorCode.NOT_FOUND))
                .when(productLikeFacade).like(1L, 999L);

            // act & assert
            assertThatThrownBy(
                () -> productLikeV1Controller.like(loginUser, productId)
            ).isInstanceOf(CoreException.class);
        }
    }

    @DisplayName("DELETE /api/v1/products/{productId}/likes")
    @Nested
    class Unlike {

        @DisplayName("좋아요 취소 요청이면, productLikeFacade.unlike를 호출하고 성공 응답을 반환한다.")
        @Test
        void unlike_callsFacadeUnlike() {
            // arrange
            Long productId = 10L;
            doNothing().when(productLikeFacade).unlike(1L, 10L);

            // act
            ApiResponse<Object> response = productLikeV1Controller.unlike(loginUser, productId);

            // assert
            verify(productLikeFacade).unlike(1L, 10L);
            assertThat(response.meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS);
        }
    }

    @DisplayName("GET /api/v1/users/me/likes")
    @Nested
    class GetMyLikes {

        @DisplayName("좋아요 목록을 조회하면, ProductLikeV1Dto.ListResponse를 반환한다.")
        @Test
        void getMyLikes_returnsListResponse() {
            // arrange
            List<ProductLikeResult> results = List.of(
                new ProductLikeResult(1L, 1L, 10L, ZonedDateTime.now()),
                new ProductLikeResult(2L, 1L, 20L, ZonedDateTime.now())
            );
            when(productLikeFacade.getMyLikedProducts(1L)).thenReturn(results);

            // act
            ApiResponse<ProductLikeV1Dto.ListResponse> response = productLikeV1Controller.getMyLikes(loginUser);

            // assert
            assertAll(
                () -> assertThat(response.meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.data().items()).hasSize(2),
                () -> assertThat(response.data().items().get(0).productId()).isEqualTo(10L),
                () -> assertThat(response.data().items().get(1).productId()).isEqualTo(20L)
            );
        }

        @DisplayName("좋아요가 없으면, 빈 목록을 반환한다.")
        @Test
        void getMyLikes_returnsEmptyList_whenNoLikes() {
            // arrange
            when(productLikeFacade.getMyLikedProducts(1L)).thenReturn(List.of());

            // act
            ApiResponse<ProductLikeV1Dto.ListResponse> response = productLikeV1Controller.getMyLikes(loginUser);

            // assert
            assertAll(
                () -> assertThat(response.meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.data().items()).isEmpty()
            );
        }
    }
}

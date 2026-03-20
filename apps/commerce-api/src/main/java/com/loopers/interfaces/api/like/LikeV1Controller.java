package com.loopers.interfaces.api.like;

import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.user.UserModel;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 좋아요 고객 API V1 REST 엔드포인트를 제공하는 컨트롤러.
 *
 * <p>상품 좋아요 등록, 취소 및 내 좋아요 목록 조회 기능을 제공한다.
 * {@link LikeService}를 직접 호출한다.
 * 좋아요 등록/취소는 멱등성을 보장한다.</p>
 */
@RestController
@RequiredArgsConstructor
public class LikeV1Controller {

    private final LikeService likeService;

    /**
     * 상품에 좋아요를 등록한다.
     *
     * <p>이미 좋아요가 등록된 경우 멱등하게 처리한다.</p>
     *
     * @param user 인증된 사용자 (인터셉터에서 주입)
     * @param productId 좋아요를 등록할 상품 ID
     * @return 성공 응답
     */
    @PostMapping("/api/v1/products/{productId}/likes")
    public ResponseEntity<ApiResponse<Object>> addLike(
            @AuthUser UserModel user,
            @PathVariable Long productId) {
        likeService.addLike(user.getUserId(), productId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    /**
     * 상품 좋아요를 취소한다.
     *
     * <p>이미 취소된 경우 멱등하게 처리한다.</p>
     *
     * @param user 인증된 사용자 (인터셉터에서 주입)
     * @param productId 좋아요를 취소할 상품 ID
     * @return 성공 응답
     */
    @DeleteMapping("/api/v1/products/{productId}/likes")
    public ResponseEntity<ApiResponse<Object>> removeLike(
            @AuthUser UserModel user,
            @PathVariable Long productId) {
        likeService.removeLike(user.getUserId(), productId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    /**
     * 내 좋아요 목록을 조회한다.
     *
     * @param user 인증된 사용자 (인터셉터에서 주입)
     * @return 좋아요 목록 응답
     */
    @GetMapping("/api/v1/users/me/likes")
    public ResponseEntity<ApiResponse<List<LikeV1Dto.LikeResponse>>> getMyLikes(@AuthUser UserModel user) {
        List<LikeModel> likes = likeService.getMyLikes(user.getUserId());
        List<LikeV1Dto.LikeResponse> response = likes.stream()
                .map(LikeV1Dto.LikeResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}

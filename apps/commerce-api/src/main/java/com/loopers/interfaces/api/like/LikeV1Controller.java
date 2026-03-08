package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeFacade;
import com.loopers.application.like.LikeDto;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
public class LikeV1Controller {

    private final LikeFacade likeFacade;

    @PostMapping("/api/v1/products/{productId}/likes")
    public ApiResponse<LikeV1Dto.LikeResponse> like(
        @RequestHeader("X-Loopers-User-Id") Long userId,
        @PathVariable Long productId
    ) {
        try {
            LikeDto.LikeResult like = likeFacade.likeWithStatus(userId, productId);
            String message = like.alreadyLiked() ? "already_liked" : "success";
            return ApiResponse.success(LikeV1Dto.LikeResponse.from(like.like(), message));
        } catch (ObjectOptimisticLockingFailureException | OptimisticLockException e) {
            throw new CoreException(ErrorType.CONFLICT, "동시 요청으로 실패했습니다. 다시 시도해주세요");
        }
    }

    @DeleteMapping("/api/v1/products/{productId}/likes")
    public ResponseEntity<Void> unlike(
        @RequestHeader("X-Loopers-User-Id") Long userId,
        @PathVariable Long productId
    ) {
        try {
            likeFacade.unlike(userId, productId);
            return ResponseEntity.noContent().build();
        } catch (ObjectOptimisticLockingFailureException | OptimisticLockException e) {
            throw new CoreException(ErrorType.CONFLICT, "동시 요청으로 실패했습니다. 다시 시도해주세요");
        }
    }

    @GetMapping("/api/v1/users/{userId}/likes")
    public ApiResponse<LikeV1Dto.LikedProductPageResponse> getMyLikes(
        @RequestHeader("X-Loopers-User-Id") Long requestUserId,
        @PathVariable Long userId,
        Pageable pageable
    ) {
        try {
            if (!requestUserId.equals(userId)) {
                throw new CoreException(ErrorType.BAD_REQUEST, "본인 좋아요 목록만 조회할 수 있습니다");
            }

            return ApiResponse.success(
                LikeV1Dto.LikedProductPageResponse.from(likeFacade.getMyLikes(userId, pageable))
            );
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, e.getMessage());
        }
    }
}

package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeFacade;
import com.loopers.application.product.ProductCacheService;
import com.loopers.domain.like.Like;
import com.loopers.domain.member.Member;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.auth.AuthMember;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class LikeController {

    private final LikeFacade likeFacade;
    private final ProductCacheService productCacheService;

    @PostMapping("/api/v1/products/{productId}/likes")
    public ApiResponse<Object> addLike(@AuthMember Member member, @PathVariable Long productId) {
        likeFacade.addLike(member.getId(), productId);
        productCacheService.evictProductDetail(productId);
        productCacheService.evictProductList();
        return ApiResponse.success(null);
    }

    @DeleteMapping("/api/v1/products/{productId}/likes")
    public ApiResponse<Object> removeLike(@AuthMember Member member, @PathVariable Long productId) {
        likeFacade.removeLike(member.getId(), productId);
        productCacheService.evictProductDetail(productId);
        productCacheService.evictProductList();
        return ApiResponse.success(null);
    }

    @GetMapping("/api/v1/users/{userId}/likes")
    public ApiResponse<List<LikeDto.LikeResponse>> getLikes(
        @AuthMember Member member,
        @PathVariable Long userId
    ) {
        if (!member.getId().equals(userId)) {
            throw new CoreException(ErrorType.FORBIDDEN, "본인의 좋아요 목록만 조회할 수 있습니다.");
        }
        List<LikeDto.LikeResponse> responses = likeFacade.getLikesByMemberId(userId).stream()
            .map(LikeDto.LikeResponse::from)
            .toList();
        return ApiResponse.success(responses);
    }
}

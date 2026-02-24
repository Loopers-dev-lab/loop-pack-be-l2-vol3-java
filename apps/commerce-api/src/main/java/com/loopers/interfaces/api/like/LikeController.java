package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeFacade;
import com.loopers.domain.like.Like;
import com.loopers.domain.member.Member;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.auth.AuthMember;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class LikeController {

    private final LikeFacade likeFacade;

    @PostMapping("/api/v1/products/{productId}/likes")
    public ApiResponse<Object> addLike(@AuthMember Member member, @PathVariable Long productId) {
        likeFacade.addLike(member.getId(), productId);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/api/v1/products/{productId}/likes")
    public ApiResponse<Object> removeLike(@AuthMember Member member, @PathVariable Long productId) {
        likeFacade.removeLike(member.getId(), productId);
        return ApiResponse.success(null);
    }

    @GetMapping("/api/v1/users/{userId}/likes")
    public ApiResponse<List<LikeDto.LikeResponse>> getLikes(@PathVariable Long userId) {
        List<LikeDto.LikeResponse> responses = likeFacade.getLikesByMemberId(userId).stream()
            .map(LikeDto.LikeResponse::from)
            .toList();
        return ApiResponse.success(responses);
    }
}

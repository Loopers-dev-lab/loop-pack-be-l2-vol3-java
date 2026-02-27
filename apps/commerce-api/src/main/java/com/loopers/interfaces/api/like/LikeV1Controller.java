package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeFacade;
import com.loopers.application.like.LikeInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
public class LikeV1Controller {

    private final LikeFacade likeFacade;

    @PostMapping("/api/v1/products/{productId}/likes")
    public ApiResponse<LikeV1Dto.LikeResponse> like(
        @PathVariable Long productId,
        @RequestHeader("X-Loopers-UserId") Long userId
    ) {
        LikeInfo info = likeFacade.like(userId, productId);
        return ApiResponse.success(LikeV1Dto.LikeResponse.from(info));
    }

    @DeleteMapping("/api/v1/products/{productId}/likes")
    public ApiResponse<Object> unlike(
        @PathVariable Long productId,
        @RequestHeader("X-Loopers-UserId") Long userId
    ) {
        likeFacade.unlike(userId, productId);
        return ApiResponse.success();
    }

    @GetMapping("/api/v1/users/{userId}/likes")
    public ApiResponse<List<LikeV1Dto.LikeResponse>> getLikesByUser(
        @PathVariable Long userId
    ) {
        List<LikeV1Dto.LikeResponse> responses = likeFacade.getLikesByUserId(userId).stream()
            .map(LikeV1Dto.LikeResponse::from)
            .toList();
        return ApiResponse.success(responses);
    }
}

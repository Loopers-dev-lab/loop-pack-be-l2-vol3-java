package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeService;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/likes")
public class LikeV1Controller implements LikeV1ApiSpec {

    private final LikeService likeService;

    @PostMapping
    @Override
    public ApiResponse<Void> like(@Valid @RequestBody LikeV1Dto.LikeRequest request) {
        likeService.like(request.memberId(), request.productId());
        return ApiResponse.<Void>success();
    }

    @DeleteMapping
    @Override
    public ApiResponse<Void> unlike(
        @RequestParam Long memberId,
        @RequestParam Long productId
    ) {
        likeService.unlike(memberId, productId);
        return ApiResponse.<Void>success();
    }
}

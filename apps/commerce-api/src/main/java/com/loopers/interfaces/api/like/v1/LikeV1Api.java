package com.loopers.interfaces.api.like.v1;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.loopers.application.like.LikeService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.LoginUser;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products/{productId}/likes")
public class LikeV1Api implements LikeV1ApiSpec {

    private final LikeService likeService;

    @PostMapping
    @Override
    public ApiResponse<Object> likeProduct(@LoginUser Long userId, @PathVariable Long productId) {
        likeService.likeProduct(userId, productId);
        return ApiResponse.success();
    }
}
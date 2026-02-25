package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthUser;
import com.loopers.interfaces.api.auth.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products/{productId}/likes")
@RequiredArgsConstructor
public class LikeV1Controller implements LikeApiV1Spec {

    private final LikeFacade likeFacade;

    // Command

    @PostMapping
    @Override
    public ApiResponse<Void> like(
            @PathVariable Long productId,
            @AuthUser AuthenticatedUser authUser) {
        likeFacade.like(authUser.id(), productId);
        return ApiResponse.success();
    }
}

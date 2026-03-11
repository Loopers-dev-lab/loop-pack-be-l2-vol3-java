package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeFacade;
import com.loopers.application.user.AuthUserPrincipal;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products")
public class LikeV1Controller {

    private final LikeFacade likeFacade;

    @PostMapping("/{productId}/like")
    public ApiResponse<Object> toggleLike(
        @PathVariable Long productId,
        @AuthUser AuthUserPrincipal user
    ) {
        likeFacade.toggleLike(productId, user.getId());
        return ApiResponse.success();
    }
}

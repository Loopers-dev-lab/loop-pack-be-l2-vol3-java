package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeFacade;
import com.loopers.application.user.AuthUserPrincipal;
import com.loopers.domain.like.LikeAction;
import com.loopers.domain.like.ProductLikedEvent;
import com.loopers.domain.like.ProductUnlikedEvent;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products")
public class LikeV1Controller {

    private final LikeFacade likeFacade;
    private final ApplicationEventPublisher eventPublisher;

    @PostMapping("/{productId}/like")
    public ApiResponse<Object> toggleLike(
        @PathVariable Long productId,
        @AuthUser AuthUserPrincipal user
    ) {
        LikeAction action = likeFacade.toggleLike(productId, user.getId());
        if (action == LikeAction.LIKED) {
            eventPublisher.publishEvent(new ProductLikedEvent(productId, user.getId()));
        } else {
            eventPublisher.publishEvent(new ProductUnlikedEvent(productId, user.getId()));
        }
        return ApiResponse.success();
    }
}

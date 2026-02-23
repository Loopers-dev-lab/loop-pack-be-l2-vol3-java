package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeFacade;
import com.loopers.domain.user.User;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.auth.AuthUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products/{productId}/likes")
public class ProductLikeController implements ProductLikeApiSpec {

    private final LikeFacade likeFacade;

    public ProductLikeController(LikeFacade likeFacade) {
        this.likeFacade = likeFacade;
    }

    @PostMapping
    @Override
    public ApiResponse<LikeResponse.LikeResult> likeProduct(@AuthUser User user, @PathVariable Long productId) {
        LikeFacade.LikeResult result = likeFacade.likeProduct(user.getId(), productId);
        return ApiResponse.success(new LikeResponse.LikeResult(true, result.likeCount()));
    }

    @DeleteMapping
    @Override
    public ApiResponse<LikeResponse.LikeResult> unlikeProduct(@AuthUser User user, @PathVariable Long productId) {
        LikeFacade.LikeResult result = likeFacade.unlikeProduct(user.getId(), productId);
        return ApiResponse.success(new LikeResponse.LikeResult(false, result.likeCount()));
    }
}

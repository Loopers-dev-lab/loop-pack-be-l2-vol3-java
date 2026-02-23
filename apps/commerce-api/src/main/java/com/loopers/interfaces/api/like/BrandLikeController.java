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
@RequestMapping("/api/v1/brands/{brandId}/likes")
public class BrandLikeController implements BrandLikeApiSpec {

    private final LikeFacade likeFacade;

    public BrandLikeController(LikeFacade likeFacade) {
        this.likeFacade = likeFacade;
    }

    @PostMapping
    @Override
    public ApiResponse<Object> likeBrand(@AuthUser User user, @PathVariable Long brandId) {
        likeFacade.likeBrand(user.getId(), brandId);
        return ApiResponse.success();
    }

    @DeleteMapping
    @Override
    public ApiResponse<Object> unlikeBrand(@AuthUser User user, @PathVariable Long brandId) {
        likeFacade.unlikeBrand(user.getId(), brandId);
        return ApiResponse.success();
    }
}

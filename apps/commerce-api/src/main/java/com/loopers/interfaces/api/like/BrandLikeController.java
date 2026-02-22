package com.loopers.interfaces.api.like;

import com.loopers.domain.like.BrandLikeService;
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

    private final BrandLikeService brandLikeService;

    public BrandLikeController(BrandLikeService brandLikeService) {
        this.brandLikeService = brandLikeService;
    }

    @PostMapping
    @Override
    public ApiResponse<Object> likeBrand(@AuthUser User user, @PathVariable Long brandId) {
        brandLikeService.like(user.getId(), brandId);
        return ApiResponse.success();
    }

    @DeleteMapping
    @Override
    public ApiResponse<Object> unlikeBrand(@AuthUser User user, @PathVariable Long brandId) {
        brandLikeService.unlike(user.getId(), brandId);
        return ApiResponse.success();
    }
}

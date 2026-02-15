package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeFacade;
import com.loopers.application.like.LikeInfo;
import com.loopers.domain.user.User;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
public class LikeV1Controller implements LikeV1ApiSpec {

    private final LikeFacade likeFacade;

    @PostMapping("/api/v1/products/{productId}/likes")
    @Override
    public ApiResponse<Object> like(@AuthUser User user, @PathVariable Long productId) {
        likeFacade.like(user.getId(), productId);
        return ApiResponse.success();
    }

    @DeleteMapping("/api/v1/products/{productId}/likes")
    @Override
    public ApiResponse<Object> unlike(@AuthUser User user, @PathVariable Long productId) {
        likeFacade.unlike(user.getId(), productId);
        return ApiResponse.success();
    }

    @GetMapping("/api/v1/likes")
    @Override
    public ApiResponse<LikeV1Dto.LikeListResponse> getMyLikes(@AuthUser User user) {
        List<LikeInfo> infos = likeFacade.getMyLikes(user.getId());
        return ApiResponse.success(LikeV1Dto.LikeListResponse.from(infos));
    }
}

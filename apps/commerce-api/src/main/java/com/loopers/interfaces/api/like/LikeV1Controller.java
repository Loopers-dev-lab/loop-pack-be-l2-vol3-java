package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeFacade;
import com.loopers.application.like.LikeInfo;
import com.loopers.application.user.UserInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.support.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/likes")
public class LikeV1Controller implements LikeV1ApiSpec {

    private final LikeFacade likeFacade;

    @PostMapping("/{productId}")
    @Override
    public ApiResponse<LikeV1Dto.LikeResponse> createLike(
            @LoginUser UserInfo loginUser,
            @PathVariable long productId)
    {
        LikeInfo like = likeFacade.create(loginUser.id(), productId);
        return ApiResponse.success(LikeV1Dto.LikeResponse.from(like));
    }

    @DeleteMapping("/{productId}")
    @Override
    public ApiResponse<Void> deleteLike(
            @LoginUser UserInfo loginUser,
            @PathVariable long productId)
    {
        likeFacade.delete(loginUser.id(), productId);
        return ApiResponse.success(null);
    }

    @GetMapping
    @Override
    public ApiResponse<LikeV1Dto.LikedProductListResponse> getLikes(
            @LoginUser UserInfo loginUser)
    {
        List<LikeInfo> likes = likeFacade.findAllByUserId(loginUser.id());
        return ApiResponse.success(LikeV1Dto.LikedProductListResponse.from(likes));
    }
}

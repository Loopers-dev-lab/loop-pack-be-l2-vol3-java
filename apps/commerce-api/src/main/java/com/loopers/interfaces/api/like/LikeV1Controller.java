package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeFacade;
import com.loopers.application.like.LikeInfo;
import com.loopers.application.user.UserService;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
public class LikeV1Controller {

    private final LikeFacade likeFacade;
    private final UserService userService;

    @PostMapping("/api/v1/products/{productId}/likes")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<LikeV1Dto.LikeResponse> register(
            @RequestHeader("X-Loopers-LoginId") String loginId,
            @RequestHeader("X-Loopers-LoginPw") String loginPw,
            @PathVariable Long productId
    ) {
        Long userId = userService.getUserId(loginId, loginPw);
        LikeInfo like = likeFacade.register(userId, productId);
        return ApiResponse.success(LikeV1Dto.LikeResponse.from(like));
    }

    @DeleteMapping("/api/v1/products/{productId}/likes")
    public ApiResponse<Void> cancel(
            @RequestHeader("X-Loopers-LoginId") String loginId,
            @RequestHeader("X-Loopers-LoginPw") String loginPw,
            @PathVariable Long productId
    ) {
        Long userId = userService.getUserId(loginId, loginPw);
        likeFacade.cancel(userId, productId);
        return ApiResponse.success(null);
    }

    @GetMapping("/api/v1/users/me/likes")
    public ApiResponse<List<LikeV1Dto.LikedProductResponse>> getLikes(
            @RequestHeader("X-Loopers-LoginId") String loginId,
            @RequestHeader("X-Loopers-LoginPw") String loginPw
    ) {
        Long userId = userService.getUserId(loginId, loginPw);
        List<LikeV1Dto.LikedProductResponse> likes = likeFacade.getLikedProductsByUserId(userId).stream()
                                                               .map(LikeV1Dto.LikedProductResponse::from)
                                                               .toList();
        return ApiResponse.success(likes);
    }
}

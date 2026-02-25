package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeFacade;
import com.loopers.application.user.UserFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/likes")
public class LikeV1Controller implements LikeV1ApiSpec {

    private final LikeFacade likeFacade;
    private final UserFacade userFacade;

    public LikeV1Controller(LikeFacade likeFacade, UserFacade userFacade) {
        this.likeFacade = likeFacade;
        this.userFacade = userFacade;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Override
    public ApiResponse<LikeV1Dto.LikeResponse> addLike(
        @RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
        @Valid @RequestBody LikeV1Dto.AddLikeRequest request
    ) {
        Long userId = userFacade.findUserIdByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, "로그인이 필요합니다."));
        var info = likeFacade.addLike(userId, request.productId());
        return ApiResponse.success(LikeV1Dto.LikeResponse.from(info));
    }

    @DeleteMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Override
    public ApiResponse<Void> removeLike(
        @RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
        @PathVariable Long productId
    ) {
        Long userId = userFacade.findUserIdByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, "로그인이 필요합니다."));
        likeFacade.removeLike(userId, productId);
        return ApiResponse.success(null);
    }

    @GetMapping
    @Override
    public ApiResponse<LikeV1Dto.PagedLikesResponse> getMyLikes(
        @RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
        Pageable pageable
    ) {
        if (loginId == null || loginId.isBlank()) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        Long userId = userFacade.findUserIdByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "사용자를 찾을 수 없습니다: " + loginId));
        var page = likeFacade.findLikesByUserId(userId, pageable);
        return ApiResponse.success(LikeV1Dto.PagedLikesResponse.from(page));
    }
}

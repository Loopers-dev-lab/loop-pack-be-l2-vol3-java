package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeFacade;
import com.loopers.application.like.LikeWithProduct;
import com.loopers.domain.member.MemberModel;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.LoginMember;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class LikeV1Controller {

    private final LikeFacade likeFacade;

    @PostMapping("/api/v1/products/{productId}/likes")
    public ApiResponse<Void> like(@LoginMember MemberModel member, @PathVariable Long productId) {
        likeFacade.like(member.getId(), productId);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/api/v1/products/{productId}/likes")
    public ApiResponse<Void> unlike(@LoginMember MemberModel member, @PathVariable Long productId) {
        likeFacade.unlike(member.getId(), productId);
        return ApiResponse.success(null);
    }

    @GetMapping("/api/v1/users/{userId}/likes")
    public ApiResponse<Page<LikeV1Dto.LikeResponse>> getMyLikes(
        @LoginMember MemberModel member,
        @PathVariable Long userId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        if (!member.getId().equals(userId)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "본인의 좋아요 목록만 조회할 수 있습니다.");
        }
        Page<LikeWithProduct> likes = likeFacade.getMyLikesWithProducts(member.getId(),
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return ApiResponse.success(likes.map(lwp -> LikeV1Dto.LikeResponse.from(lwp.like(), lwp.product())));
    }
}

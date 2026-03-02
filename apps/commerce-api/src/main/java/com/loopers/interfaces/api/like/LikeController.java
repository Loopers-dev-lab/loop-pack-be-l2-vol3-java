package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeFacade;
import com.loopers.application.like.LikeInfo;
import com.loopers.domain.member.Member;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.resolver.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/likes")
@RequiredArgsConstructor
public class LikeController {
    private final LikeFacade likeFacade;

    @PostMapping("/{productId}")
    public ApiResponse<LikeDto.ToggleResponse> toggleLike(
            @LoginUser Member member,
            @PathVariable Long productId
    ) {
        boolean liked = likeFacade.toggleLike(member.getId(), productId);
        return ApiResponse.success(LikeDto.ToggleResponse.from(liked));
    }

    @GetMapping
    public ApiResponse<LikeDto.LikeListResponse> getLikedProducts(@LoginUser Member member) {
        List<LikeInfo> likedProducts = likeFacade.getLikedProducts(member.getId());
        return ApiResponse.success(LikeDto.LikeListResponse.from(likedProducts));
    }
}

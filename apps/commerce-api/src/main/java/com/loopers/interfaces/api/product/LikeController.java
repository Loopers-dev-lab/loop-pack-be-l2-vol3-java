package com.loopers.interfaces.api.product;

import com.loopers.application.like.LikeApplicationService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.AuthMember;
import com.loopers.domain.member.Member;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class LikeController {

    private final LikeApplicationService likeApplicationService;

    public LikeController(LikeApplicationService likeApplicationService) {
        this.likeApplicationService = likeApplicationService;
    }

    @PostMapping("/products/{productId}/likes")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> registerLike(@PathVariable Long productId, @AuthMember Member member) {
        likeApplicationService.register(productId, member);
        return ApiResponse.success();
    }

    @DeleteMapping("/products/{productId}/likes")
    public ApiResponse<Void> cancelLike(@PathVariable Long productId, @AuthMember Member member) {
        likeApplicationService.cancel(productId, member);
        return ApiResponse.success();
    }

    @GetMapping("/me/likes")
    public ApiResponse<ProductDto.ProductListResponse> getMyLikes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthMember Member member
    ) {
        return ApiResponse.success(ProductDto.ProductListResponse.from(
                likeApplicationService.getMyLikes(member.id().value(), PageRequest.of(page, size))
        ));
    }
}

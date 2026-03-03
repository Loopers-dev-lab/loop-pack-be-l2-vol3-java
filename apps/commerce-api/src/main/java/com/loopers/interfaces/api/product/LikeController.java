package com.loopers.interfaces.api.product;

import com.loopers.application.like.LikeFacade;
import com.loopers.application.product.ProductQueryFacade;
import com.loopers.domain.product.Product;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.AuthMember;
import com.loopers.domain.member.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class LikeController {

    private final LikeFacade likeFacade;
    private final ProductQueryFacade productQueryFacade;

    @PostMapping("/products/{productId}/likes")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> registerLike(@PathVariable UUID productId, @AuthMember Member member) {
        likeFacade.register(productId, member);
        return ApiResponse.success();
    }

    @DeleteMapping("/products/{productId}/likes")
    public ApiResponse<Void> cancelLike(@PathVariable UUID productId, @AuthMember Member member) {
        likeFacade.cancel(productId, member);
        return ApiResponse.success();
    }

    @GetMapping("/me/likes")
    public ApiResponse<ProductDto.ProductListResponse> getMyLikes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthMember Member member
    ) {
        Page<Product> likedProducts = likeFacade.getMyLikes(member.id().value(), PageRequest.of(page, size));
        return ApiResponse.success(ProductDto.ProductListResponse.from(productQueryFacade.toListView(likedProducts)));
    }
}

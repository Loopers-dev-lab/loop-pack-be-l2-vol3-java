package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeFacade;
import com.loopers.domain.user.User;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.auth.AuthUser;
import com.loopers.support.error.CommonErrorType;
import com.loopers.support.error.CoreException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
public class UserLikeController implements UserLikeApiSpec {

    private final LikeFacade likeFacade;

    public UserLikeController(LikeFacade likeFacade) {
        this.likeFacade = likeFacade;
    }

    @GetMapping("/{userId}/likes")
    @Override
    public ApiResponse<LikeResponse.LikedProductListResponse> getMyProductLikes(
            @AuthUser User user,
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (!user.getId().equals(userId)) {
            throw new CoreException(CommonErrorType.FORBIDDEN);
        }

        LikeFacade.LikedProductListResult result = likeFacade.getMyLikedProducts(userId, page, size);

        List<LikeResponse.LikedProductSummary> products = result.products().stream()
                .map(p -> new LikeResponse.LikedProductSummary(
                        p.productId(), p.productName(), p.basePrice(),
                        p.brandName(), p.likeCount(), p.likedAt()))
                .toList();

        return ApiResponse.success(
                new LikeResponse.LikedProductListResponse(
                        products, result.page(), result.size(), result.totalElements(), result.totalPages()));
    }

    @GetMapping("/me/brand-likes")
    @Override
    public ApiResponse<LikeResponse.LikedBrandListResponse> getMyBrandLikes(
            @AuthUser User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        LikeFacade.LikedBrandListResult result = likeFacade.getMyLikedBrands(user.getId(), page, size);

        List<LikeResponse.LikedBrandSummary> brands = result.brands().stream()
                .map(b -> new LikeResponse.LikedBrandSummary(
                        b.brandId(), b.brandName(), b.description(), b.likedAt()))
                .toList();

        return ApiResponse.success(
                new LikeResponse.LikedBrandListResponse(
                        brands, result.page(), result.size(), result.totalElements(), result.totalPages()));
    }
}

package com.loopers.interfaces.api.like;

import com.loopers.application.brand.BrandApplicationService;
import com.loopers.application.like.LikeApplicationService;
import com.loopers.application.product.ProductApplicationService;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.like.Like;
import com.loopers.domain.product.Product;
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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@RestController
public class LikeV1Controller implements LikeV1ApiSpec {

    private final LikeApplicationService likeApplicationService;
    private final ProductApplicationService productApplicationService;
    private final BrandApplicationService brandApplicationService;

    @PostMapping("/api/v1/products/{productId}/likes")
    @Override
    public ApiResponse<Void> like(@AuthUser User user, @PathVariable Long productId) {
        likeApplicationService.like(user.getId(), productId);
        return ApiResponse.success();
    }

    @DeleteMapping("/api/v1/products/{productId}/likes")
    @Override
    public ApiResponse<Void> unlike(@AuthUser User user, @PathVariable Long productId) {
        likeApplicationService.unlike(user.getId(), productId);
        return ApiResponse.success();
    }

    @GetMapping("/api/v1/likes")
    @Override
    public ApiResponse<LikeV1Dto.LikeListResponse> getMyLikes(@AuthUser User user) {
        List<Like> likes = likeApplicationService.getMyLikes(user.getId());

        Set<Long> productIds = likes.stream()
            .map(Like::getProductId)
            .collect(Collectors.toSet());
        Map<Long, Product> productMap = productApplicationService.getByIds(productIds);

        Set<Long> brandIds = productMap.values().stream()
            .map(Product::getBrandId)
            .collect(Collectors.toSet());
        Map<Long, Brand> brandMap = brandApplicationService.getByIds(brandIds);

        List<LikeV1Dto.LikeResponse> likeResponses = likes.stream()
            .filter(like -> {
                Product product = productMap.get(like.getProductId());
                return product != null && brandMap.containsKey(product.getBrandId());
            })
            .map(like -> {
                Product product = productMap.get(like.getProductId());
                Brand brand = brandMap.get(product.getBrandId());
                return LikeV1Dto.LikeResponse.from(like, product, brand);
            })
            .toList();

        return ApiResponse.success(LikeV1Dto.LikeListResponse.from(likeResponses));
    }
}

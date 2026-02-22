package com.loopers.interfaces.api.like;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.BrandLike;
import com.loopers.domain.like.BrandLikeService;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.like.ProductLike;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
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
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/users")
public class UserLikeController implements UserLikeApiSpec {

    private final LikeService likeService;
    private final BrandLikeService brandLikeService;
    private final ProductService productService;
    private final BrandService brandService;

    public UserLikeController(LikeService likeService, BrandLikeService brandLikeService,
                              ProductService productService, BrandService brandService) {
        this.likeService = likeService;
        this.brandLikeService = brandLikeService;
        this.productService = productService;
        this.brandService = brandService;
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

        List<ProductLike> likes = likeService.getMyProductLikes(userId, page, size);
        long totalElements = likeService.countMyProductLikes(userId);
        int totalPages = (int) Math.ceil((double) totalElements / size);

        List<Long> productIds = likes.stream().map(ProductLike::getProductId).toList();
        Map<Long, Product> productMap = productService.getProductsByIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<Long> brandIds = productMap.values().stream()
                .map(Product::getBrandId)
                .distinct()
                .toList();
        Map<Long, Brand> brandMap = brandService.getBrandsByIds(brandIds).stream()
                .collect(Collectors.toMap(Brand::getId, Function.identity()));

        List<LikeResponse.LikedProductSummary> products = likes.stream()
                .filter(like -> productMap.containsKey(like.getProductId()))
                .map(like -> {
                    Product product = productMap.get(like.getProductId());
                    Brand brand = brandMap.get(product.getBrandId());
                    String brandName = brand != null ? brand.getName() : "";
                    return new LikeResponse.LikedProductSummary(
                            product.getId(),
                            product.getName(),
                            product.getBasePrice(),
                            brandName,
                            product.getLikeCount(),
                            like.getCreatedAt()
                    );
                })
                .toList();

        return ApiResponse.success(
                new LikeResponse.LikedProductListResponse(products, page, size, totalElements, totalPages));
    }

    @GetMapping("/me/brand-likes")
    @Override
    public ApiResponse<LikeResponse.LikedBrandListResponse> getMyBrandLikes(
            @AuthUser User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Long userId = user.getId();
        List<BrandLike> likes = brandLikeService.getMyBrandLikes(userId, page, size);
        long totalElements = brandLikeService.countMyBrandLikes(userId);
        int totalPages = (int) Math.ceil((double) totalElements / size);

        List<Long> brandIds = likes.stream().map(BrandLike::getBrandId).toList();
        Map<Long, Brand> brandMap = brandService.getBrandsByIds(brandIds).stream()
                .collect(Collectors.toMap(Brand::getId, Function.identity()));

        List<LikeResponse.LikedBrandSummary> brands = likes.stream()
                .filter(like -> brandMap.containsKey(like.getBrandId()))
                .map(like -> {
                    Brand brand = brandMap.get(like.getBrandId());
                    return new LikeResponse.LikedBrandSummary(
                            brand.getId(),
                            brand.getName(),
                            brand.getDescription(),
                            like.getCreatedAt()
                    );
                })
                .toList();

        return ApiResponse.success(
                new LikeResponse.LikedBrandListResponse(brands, page, size, totalElements, totalPages));
    }
}

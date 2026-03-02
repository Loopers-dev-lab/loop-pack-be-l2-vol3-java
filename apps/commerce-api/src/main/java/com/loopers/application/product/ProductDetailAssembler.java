package com.loopers.application.product;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.Product;

import lombok.RequiredArgsConstructor;

/**
 * 상품 목록에 브랜드 정보와 좋아요 여부를 조합하여 상품 상세를 생성하는 어셈블러.
 */
@Component
@RequiredArgsConstructor
public class ProductDetailAssembler {

    private final BrandService brandService;
    private final LikeService likeService;

    /**
     * 상품 목록에 브랜드 정보와 사용자의 좋아요 여부를 조합한다.
     *
     * @param products 상품 목록
     * @param userId   사용자 ID (비로그인 시 null)
     * @return 브랜드 및 좋아요 정보가 포함된 상품 상세 목록
     */
    public List<ProductDetail> assemble(List<Product> products, Long userId) {
        List<Long> productIds = products.stream()
                .map(Product::getId)
                .toList();
        List<Long> brandIds = products.stream()
                .map(Product::getBrandId)
                .distinct()
                .toList();

        Map<Long, Brand> brands = brandService.getActiveBrandMap(brandIds);
        Set<Long> likedProductIds = likeService.getLikedProductIds(userId, productIds);

        return products.stream()
                .filter(product -> brands.containsKey(product.getBrandId()))
                .map(product -> ProductDetail.from(
                        product,
                        brands.get(product.getBrandId()),
                        likedProductIds.contains(product.getId())
                ))
                .toList();
    }
}

package com.loopers.application.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Product;

/**
 * 상품 상세 조회 결과.
 *
 * @param rank 인기 랭킹 순위 (1-based, 순위권 밖이면 null)
 */
public record ProductDetail(
        Long productId,
        String name,
        String thumbnailUrl,
        Long price,
        Long stock,
        String description,
        Long brandId,
        String brandName,
        String brandLogoUrl,
        Long likeCount,
        boolean liked,
        Integer rank
) {

    /**
     * 순위 정보 없이 상품 상세를 생성한다.
     *
     * @param product 상품
     * @param brand   브랜드
     * @param liked   좋아요 여부
     * @return 상품 상세 (rank=null)
     */
    public static ProductDetail from(Product product, Brand brand, boolean liked) {
        return from(product, brand, liked, null);
    }

    /**
     * 순위 정보를 포함하여 상품 상세를 생성한다.
     *
     * @param product 상품
     * @param brand   브랜드
     * @param liked   좋아요 여부
     * @param rank    순위 (nullable)
     * @return 상품 상세
     */
    public static ProductDetail from(Product product, Brand brand, boolean liked, Integer rank) {
        return new ProductDetail(
                product.getId(),
                product.getName().getValue(),
                product.getThumbnailUrl().getValue(),
                product.getPrice().getAmount(),
                product.getStock().getValue(),
                product.getDescription(),
                brand.getId(),
                brand.getName(),
                brand.getLogoUrl(),
                product.getLikeCount(),
                liked,
                rank
        );
    }
}

package com.loopers.application.product;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.SellingStatus;

public record ProductInfo(
    Long id,
    Long brandId,
    String name,
    String description,
    int price,
    int stock,
    SellingStatus sellingStatus,
    int likeCount,
    Boolean isLiked,
    Integer rank
) {
    public static ProductInfo from(Product product) {
        return new ProductInfo(
            product.getId(),
            product.getBrandId(),
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            product.getStock(),
            product.getSellingStatus(),
            product.getLikeCount(),
            null,
            null
        );
    }

    public static ProductInfo from(Product product, Boolean isLiked) {
        return new ProductInfo(
            product.getId(),
            product.getBrandId(),
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            product.getStock(),
            product.getSellingStatus(),
            product.getLikeCount(),
            isLiked,
            null
        );
    }

    /**
     * product_metrics에서 읽은 likeCount와 isLiked를 함께 받는 팩토리 메서드.
     * Eventual Consistency: likeCount는 Kafka Consumer가 처리한 시점의 값이다.
     */
    public static ProductInfo from(Product product, int likeCount, Boolean isLiked) {
        return new ProductInfo(
            product.getId(),
            product.getBrandId(),
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            product.getStock(),
            product.getSellingStatus(),
            likeCount,
            isLiked,
            null
        );
    }

    /**
     * isLiked만 교체한 새 인스턴스를 반환한다.
     * Cache-Aside 조회 시 캐시에 저장된 상품 정보(isLiked=null)에
     * 사용자별 좋아요 여부를 오버레이할 때 사용한다.
     */
    public ProductInfo withIsLiked(Boolean isLiked) {
        return new ProductInfo(id, brandId, name, description, price, stock, sellingStatus, likeCount, isLiked, rank);
    }

    /**
     * rank만 교체한 새 인스턴스를 반환한다.
     * 상품 상세 조회 시 ZREVRANK로 조회한 순위를 오버레이할 때 사용한다.
     */
    public ProductInfo withRank(Integer rank) {
        return new ProductInfo(id, brandId, name, description, price, stock, sellingStatus, likeCount, isLiked, rank);
    }
}

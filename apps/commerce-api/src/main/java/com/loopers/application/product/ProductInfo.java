package com.loopers.application.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductStockModel;
import com.loopers.support.enums.DisplayStatus;
import com.loopers.support.enums.ProductSaleStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 상품 정보 DTO.
 * <p>
 * 상품 엔티티와 재고 엔티티를 조합하여
 * 도메인 모델을 직접 노출하지 않고 interfaces 계층에 전달하기 위한 응답 객체이다.
 * 가용 재고(availableStock) 정보를 포함한다.
 * </p>
 */
@Getter
@Builder
public class ProductInfo {
    private final Long productId;
    private final Long brandId;
    private final String productName;
    private final String description;
    private final BigDecimal price;
    private final String category;
    private final String color;
    private final String size;
    private final String option;
    private final String imageUrl;
    private final String attachFile;
    private final DisplayStatus displayStatus;
    private final ProductSaleStatus saleStatus;
    private final Long revisionSeq;
    private final int availableStock;
    private final String brandName;
    private final long likeCount;
    private final Long rank;

    /**
     * ProductModel과 ProductStockModel을 조합하여 ProductInfo DTO로 변환한다 (admin용).
     * brandName=null, likeCount=0 기본값.
     *
     * @param product 상품 엔티티
     * @param stock   재고 엔티티 (null이면 availableStock=0)
     * @return 상품 정보 DTO (가용 재고 포함)
     */
    public static ProductInfo from(ProductModel product, ProductStockModel stock) {
        return ProductInfo.builder()
                .productId(product.getProductId())
                .brandId(product.getBrandId())
                .productName(product.getProductName())
                .description(product.getDescription())
                .price(product.getPrice())
                .category(product.getCategory())
                .color(product.getColor())
                .size(product.getSize())
                .option(product.getOption())
                .imageUrl(product.getImageUrl())
                .attachFile(product.getAttachFile())
                .displayStatus(product.getDisplayStatus())
                .saleStatus(product.getSaleStatus())
                .revisionSeq(product.getRevisionSeq())
                .availableStock(stock != null ? stock.getAvailableQty() : 0)
                .brandName(null)
                .likeCount(0)
                .rank(null)
                .build();
    }

    /**
     * ProductModel + Stock + 브랜드명 + 좋아요 수를 조합하여 ProductInfo DTO로 변환한다 (고객용).
     *
     * @param product   상품 엔티티
     * @param stock     재고 엔티티 (null이면 availableStock=0)
     * @param brandName 브랜드명
     * @param likeCount 좋아요 수
     * @return 상품 정보 DTO (브랜드명, 좋아요 수 포함)
     */
    public static ProductInfo from(ProductModel product, ProductStockModel stock,
                                   String brandName, long likeCount) {
        return ProductInfo.builder()
                .productId(product.getProductId())
                .brandId(product.getBrandId())
                .productName(product.getProductName())
                .description(product.getDescription())
                .price(product.getPrice())
                .category(product.getCategory())
                .color(product.getColor())
                .size(product.getSize())
                .option(product.getOption())
                .imageUrl(product.getImageUrl())
                .attachFile(product.getAttachFile())
                .displayStatus(product.getDisplayStatus())
                .saleStatus(product.getSaleStatus())
                .revisionSeq(product.getRevisionSeq())
                .availableStock(stock != null ? stock.getAvailableQty() : 0)
                .brandName(brandName)
                .likeCount(likeCount)
                .rank(null)
                .build();
    }

    /**
     * ProductModel + Stock + 브랜드명 + 좋아요 수 + 순위를 조합하여 ProductInfo DTO로 변환한다 (고객용, 순위 포함).
     *
     * @param product   상품 엔티티
     * @param stock     재고 엔티티 (null이면 availableStock=0)
     * @param brandName 브랜드명
     * @param likeCount 좋아요 수
     * @param rank      오늘 기준 순위 (nullable)
     * @return 상품 정보 DTO (순위 포함)
     */
    public static ProductInfo from(ProductModel product, ProductStockModel stock,
                                   String brandName, long likeCount, Long rank) {
        return ProductInfo.builder()
                .productId(product.getProductId())
                .brandId(product.getBrandId())
                .productName(product.getProductName())
                .description(product.getDescription())
                .price(product.getPrice())
                .category(product.getCategory())
                .color(product.getColor())
                .size(product.getSize())
                .option(product.getOption())
                .imageUrl(product.getImageUrl())
                .attachFile(product.getAttachFile())
                .displayStatus(product.getDisplayStatus())
                .saleStatus(product.getSaleStatus())
                .revisionSeq(product.getRevisionSeq())
                .availableStock(stock != null ? stock.getAvailableQty() : 0)
                .brandName(brandName)
                .likeCount(likeCount)
                .rank(rank)
                .build();
    }
}

package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.stock.StockModel;

public class ProductInfo {

    public enum StockStatus {
        IN_STOCK, LOW_STOCK, OUT_OF_STOCK;

        public static StockStatus from(int quantity) {
            if (quantity <= 0) return OUT_OF_STOCK;
            if (quantity <= 5) return LOW_STOCK;
            return IN_STOCK;
        }
    }

    public record Summary(
        Long id, String name, int price, String brandName, StockStatus stockStatus
    ) {
        public static Summary from(ProductModel product, BrandModel brand, StockModel stock) {
            return new Summary(
                product.getId(),
                product.getName(),
                product.getPrice().value(),
                brand.getName(),
                StockStatus.from(stock.getQuantity())
            );
        }
    }

    public record Detail(
        Long id, String name, String description, int price,
        String brandName, int likeCount, StockStatus stockStatus
    ) {
        public static Detail from(ProductModel product, BrandModel brand, StockModel stock) {
            return new Detail(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice().value(),
                brand.getName(),
                product.getLikeCount(),
                StockStatus.from(stock.getQuantity())
            );
        }
    }

    public record AdminSummary(
        Long id, String name, int price, String brandName, int stockQuantity
    ) {
        public static AdminSummary from(ProductModel product, BrandModel brand, StockModel stock) {
            return new AdminSummary(
                product.getId(),
                product.getName(),
                product.getPrice().value(),
                brand.getName(),
                stock.getQuantity()
            );
        }
    }

    public record AdminDetail(
        Long id, String name, String description, int price,
        Long brandId, String brandName, int likeCount, int stockQuantity
    ) {
        public static AdminDetail from(ProductModel product, BrandModel brand, StockModel stock) {
            return new AdminDetail(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice().value(),
                brand.getId(),
                brand.getName(),
                product.getLikeCount(),
                stock.getQuantity()
            );
        }
    }
}

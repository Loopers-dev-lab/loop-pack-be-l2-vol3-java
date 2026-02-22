package com.loopers.interfaces.api.admin;

import com.loopers.domain.common.Money;
import com.loopers.domain.product.Option;
import com.loopers.domain.product.Product;

import java.math.BigDecimal;
import java.util.List;

public class AdminProductDto {

    public record CreateRequest(Long brandId, String name, BigDecimal basePrice) {
        public Money toBasePrice() {
            return Money.of(basePrice);
        }
    }

    public record UpdateRequest(String name, BigDecimal basePrice) {
        public Money toBasePrice() {
            return Money.of(basePrice);
        }
    }

    public record CreateOptionRequest(String name, BigDecimal additionalPrice, int stock) {
        public Money toAdditionalPrice() {
            return additionalPrice != null ? Money.of(additionalPrice) : Money.zero();
        }
    }

    public record UpdateStockRequest(int stock) {}

    public record ProductResponse(Long id, Long brandId, String name, BigDecimal basePrice, boolean deleted) {
        public static ProductResponse from(Product product) {
            return new ProductResponse(
                    product.getId(),
                    product.getBrandId(),
                    product.getName(),
                    product.getBasePrice().getAmount(),
                    product.isDeleted()
            );
        }
    }

    public record OptionResponse(Long id, Long productId, String name, BigDecimal additionalPrice, int stock, boolean soldOut) {
        public static OptionResponse from(Option option) {
            return new OptionResponse(
                    option.getId(),
                    option.getProductId(),
                    option.getName(),
                    option.getAdditionalPrice().getAmount(),
                    option.getStock(),
                    option.isSoldOut()
            );
        }
    }

    public record ProductDetailResponse(ProductResponse product, List<OptionResponse> options) {
        public static ProductDetailResponse of(Product product, List<Option> options) {
            return new ProductDetailResponse(
                    ProductResponse.from(product),
                    options.stream().map(OptionResponse::from).toList()
            );
        }
    }

    public record ProductListResponse(List<ProductResponse> products) {
        public static ProductListResponse from(List<Product> products) {
            return new ProductListResponse(
                    products.stream().map(ProductResponse::from).toList()
            );
        }
    }
}

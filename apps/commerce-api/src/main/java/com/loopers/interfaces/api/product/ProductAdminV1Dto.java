package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductInfo;
import com.loopers.application.product.ProductRegisterCommand;
import com.loopers.application.product.ProductUpdateCommand;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Stock;
import org.springframework.data.domain.Page;

import java.time.ZonedDateTime;
import java.util.List;

public class ProductAdminV1Dto {

    public record ProductResponse(
            long id,
            long brandId,
            String brandName,
            String name,
            int price,
            int stock,  // BR-P05: 관리자에게는 실제 재고 수량 노출
            int likeCount,
            ZonedDateTime createdAt,
            ZonedDateTime updatedAt
    ) {
        public static ProductResponse from(ProductInfo info) {
            return new ProductResponse(
                    info.id(),
                    info.brandId(),
                    info.brandName(),
                    info.name(),
                    info.price(),
                    info.stock(),
                    info.likeCount(),
                    info.createdAt(),
                    info.updatedAt()
            );
        }
    }

    public record ProductListResponse(
            List<ProductResponse> products,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
        public static ProductListResponse from(Page<ProductInfo> info) {
            return new ProductListResponse(
                    info.getContent().stream().map(ProductResponse::from).toList(),
                    info.getNumber(),
                    info.getSize(),
                    info.getTotalElements(),
                    info.getTotalPages()
            );
        }
    }

    public record RegisterRequest(
            Long brandId,
            String name,
            int price,
            int stock
    ) {
        public ProductRegisterCommand toCommand() {
            return new ProductRegisterCommand(brandId, name, new Money(price), new Stock(stock));
        }
    }

    public record UpdateRequest(
            String name,
            int price,
            int stock
    ) {
        public ProductUpdateCommand toCommand(long id) {
            return new ProductUpdateCommand(id, name, new Money(price), new Stock(stock));
        }
    }
}

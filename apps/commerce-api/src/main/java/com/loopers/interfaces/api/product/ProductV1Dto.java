package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductInfo;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

public class ProductV1Dto {
    public record CreateRequest(
            @NotBlank String name,
            String description,
            @NotNull @DecimalMin("0") BigDecimal price,
            @NotNull @Min(0) Integer stock
    ) {}

    public record UpdateRequest(
            @NotBlank String name,
            String description,
            @NotNull @DecimalMin("0") BigDecimal price,
            @NotNull @Min(0) Integer stock
    ) {}

    public record ProductResponse(
            Long id,
            String name,
            String description,
            BigDecimal price,
            Integer stock,
            ZonedDateTime createdAt,
            ZonedDateTime updatedAt
    ) {
        public static ProductResponse from(ProductInfo info) {
            return new ProductResponse(
                    info.id(),
                    info.name(),
                    info.description(),
                    info.price(),
                    info.stock(),
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
        public static ProductListResponse from(Page<ProductInfo> page) {
            return new ProductListResponse(
                    page.getContent().stream()
                            .map(ProductResponse::from)
                            .toList(),
                    page.getNumber(),
                    page.getSize(),
                    page.getTotalElements(),
                    page.getTotalPages()
            );
        }
    }
}

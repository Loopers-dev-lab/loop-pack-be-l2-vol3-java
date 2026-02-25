package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeProductInfo;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class LikeV1Dto {

    // Query

    public record ListRequest(
            @PositiveOrZero Integer page,
            @Min(1) @Max(100) Integer size
    ) {
        public ListRequest {
            if (page == null) page = 0;
            if (size == null) size = 20;
        }

        public Pageable toPageable() {
            return PageRequest.of(page, size);
        }
    }

    // Response

    public record LikeProductResponse(
            Long id,
            Long brandId,
            String brandName,
            String name,
            BigDecimal price,
            Integer stockQuantity,
            String description,
            Integer likeCount,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static LikeProductResponse from(LikeProductInfo info) {
            return new LikeProductResponse(
                    info.id(),
                    info.brandId(),
                    info.brandName(),
                    info.name(),
                    info.price(),
                    info.stockQuantity(),
                    info.description(),
                    info.likeCount(),
                    info.createdAt(),
                    info.updatedAt()
            );
        }
    }
}

package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeDto;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

public class LikeV1Dto {

    public record LikeResponse(
        Long productId,
        boolean liked,
        String message
    ) {
        public static LikeResponse from(LikeDto.LikeInfo likeInfo, String message) {
            return new LikeResponse(likeInfo.productId(), true, message);
        }
    }

    public record LikedProductResponse(
        Long productId,
        Long brandId,
        String brandName,
        String productName,
        String productDescription,
        BigDecimal productPrice,
        Integer likeCount,
        ZonedDateTime likedAt
    ) {
        public static LikedProductResponse from(LikeDto.LikedProductInfo info) {
            return new LikedProductResponse(
                info.productId(),
                info.brandId(),
                info.brandName(),
                info.productName(),
                info.productDescription(),
                info.productPrice(),
                info.likeCount(),
                info.likedAt()
            );
        }
    }

    public record LikedProductPageResponse(
        List<LikedProductResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
        public static LikedProductPageResponse from(Page<LikeDto.LikedProductInfo> page) {
            return new LikedProductPageResponse(
                page.getContent().stream().map(LikedProductResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
            );
        }
    }
}

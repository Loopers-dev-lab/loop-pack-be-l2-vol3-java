package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeInfo;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;

import java.time.ZonedDateTime;
import java.util.List;

public class LikeV1Dto {

    public record AddLikeRequest(
        @NotNull(message = "상품 ID는 필수입니다.")
        Long productId
    ) {
    }

    public record LikeResponse(
        Long id,
        Long userId,
        Long productId,
        ZonedDateTime createdAt
    ) {
        public static LikeResponse from(LikeInfo info) {
            if (info == null) {
                return null;
            }
            return new LikeResponse(
                info.id(),
                info.userId(),
                info.productId(),
                info.createdAt()
            );
        }
    }

    public record PagedLikesResponse(
        List<LikeResponse> content,
        long totalElements,
        int totalPages,
        int number,
        int size
    ) {
        public static PagedLikesResponse from(Page<LikeInfo> page) {
            if (page == null) {
                return new PagedLikesResponse(List.of(), 0L, 0, 0, 0);
            }
            List<LikeResponse> content = page.getContent().stream()
                .map(LikeResponse::from)
                .toList();
            return new PagedLikesResponse(
                content,
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize()
            );
        }
    }
}

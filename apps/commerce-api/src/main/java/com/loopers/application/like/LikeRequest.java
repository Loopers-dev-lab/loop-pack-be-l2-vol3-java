package com.loopers.application.like;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

public record LikeRequest() {

    // Query

    public record ListLiked(
            @PositiveOrZero Integer page,
            @Min(1) @Max(100) Integer size
    ) {
        public ListLiked {
            if (page == null) page = 0;
            if (size == null) size = 20;
        }

        public Pageable toPageable() {
            return PageRequest.of(page, size);
        }
    }
}

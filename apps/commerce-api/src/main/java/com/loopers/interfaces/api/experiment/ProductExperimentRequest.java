package com.loopers.interfaces.api.experiment;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public record ProductExperimentRequest() {

    // Query

    public record ListOffset(
            Long brandId,

            @PositiveOrZero(message = "페이지 번호는 0 이상이어야 합니다")
            Integer page,

            @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다")
            @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다")
            Integer size
    ) {
        public ListOffset {
            if (page == null) page = 0;
            if (size == null) size = 20;
        }

        public Pageable toPageable() {
            return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        }
    }

    public record ListCursor(
            Long brandId,
            Long cursor,

            @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다")
            @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다")
            Integer size
    ) {
        public ListCursor {
            if (size == null) size = 20;
        }
    }
}

package com.loopers.application.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

public record OrderRequest() {

    // Command

    public record Place(
            @NotNull(message = "주문 상품 목록은 필수입니다")
            @Size(min = 1, max = 100, message = "주문 상품은 1~100건이어야 합니다")
            List<@Valid PlaceItem> orderItems
    ) {
    }

    public record PlaceItem(
            @NotNull(message = "상품 ID는 필수입니다")
            Long productId,

            @NotNull(message = "수량은 필수입니다")
            @Min(value = 1, message = "수량은 1 이상이어야 합니다")
            @Max(value = 9999999, message = "수량은 9,999,999 이하여야 합니다")
            Integer quantity
    ) {
    }

    // Query

    public record ListByUser(
            LocalDate startDate,
            LocalDate endDate,
            @PositiveOrZero(message = "페이지 번호는 0 이상이어야 합니다") Integer page,
            @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다")
            @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다") Integer size
    ) {
        public ListByUser {
            page = Objects.requireNonNullElse(page, 0);
            size = Objects.requireNonNullElse(size, 20);
        }

        public Pageable toPageable() {
            return PageRequest.of(page, size);
        }

        public ZonedDateTime startDateTime() {
            return startDate != null
                    ? startDate.atStartOfDay(ZoneId.systemDefault()) : null;
        }

        public ZonedDateTime endDateTime() {
            return endDate != null
                    ? endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()) : null;
        }
    }
}

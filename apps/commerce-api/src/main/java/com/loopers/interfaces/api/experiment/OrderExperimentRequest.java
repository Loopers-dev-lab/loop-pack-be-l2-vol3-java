package com.loopers.interfaces.api.experiment;

import com.loopers.domain.order.OrderStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public record OrderExperimentRequest() {

    // Query

    public record UserOrderList(
            @NotNull(message = "userId는 필수입니다")
            Long userId,

            OrderStatus status,

            LocalDate startDate,

            LocalDate endDate,

            @PositiveOrZero(message = "페이지 번호는 0 이상이어야 합니다")
            Integer page,

            @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다")
            @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다")
            Integer size
    ) {
        public UserOrderList {
            if (page == null) page = 0;
            if (size == null) size = 20;
        }

        public Pageable toPageable() {
            return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        }

        public ZonedDateTime startDateTime() {
            return startDate != null ? startDate.atStartOfDay(ZoneId.systemDefault()) : null;
        }

        public ZonedDateTime endDateTime() {
            return endDate != null ? endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()) : null;
        }
    }

    public record AdminStatusList(
            @NotNull(message = "status는 필수입니다")
            OrderStatus status,

            @PositiveOrZero(message = "페이지 번호는 0 이상이어야 합니다")
            Integer page,

            @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다")
            @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다")
            Integer size
    ) {
        public AdminStatusList {
            if (page == null) page = 0;
            if (size == null) size = 20;
        }

        public Pageable toPageable() {
            return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        }
    }

    public record ProductOrderList(
            @NotNull(message = "productId는 필수입니다")
            Long productId,

            @PositiveOrZero(message = "페이지 번호는 0 이상이어야 합니다")
            Integer page,

            @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다")
            @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다")
            Integer size
    ) {
        public ProductOrderList {
            if (page == null) page = 0;
            if (size == null) size = 20;
        }

        public Pageable toPageable() {
            return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        }
    }
}

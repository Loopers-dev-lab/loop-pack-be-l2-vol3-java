package com.loopers.application.order.query;

import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public record OrderListByUserRequest(
        Long userId,
        LocalDate startAt,
        LocalDate endAt,
        Pageable pageable
) {
}

package com.loopers.application.order.query;

import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.UUID;

public record OrderListByUserRequest(
        String memberId,
        LocalDate startAt,
        LocalDate endAt,
        Pageable pageable
) {
}

package com.loopers.application.order;

import java.util.Map;

public record OrderCommand(
    Long userId,
    Map<Long, Integer> productQuantities
) {
}

package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class OrderPolicy {

    public static void validateNoDuplicateProducts(Collection<Long> productIds) {
        Set<Long> unique = new HashSet<>();
        for (Long id : productIds) {
            if (!unique.add(id)) {
                throw new CoreException(ErrorType.BAD_REQUEST, "중복된 상품이 포함되어 있습니다.");
            }
        }
    }

    private OrderPolicy() {}
}

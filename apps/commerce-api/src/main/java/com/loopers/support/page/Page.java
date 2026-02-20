package com.loopers.support.page;

import java.util.List;

public record Page<T>(
        List<T> content,
        boolean hasNext
) {

}

package com.loopers.interfaces.api;

import java.util.List;

public record PageResponse<T>(
        List<T> content,
        boolean hasNext
) {

}

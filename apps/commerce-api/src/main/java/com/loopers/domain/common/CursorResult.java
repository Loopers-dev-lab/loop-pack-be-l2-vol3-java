package com.loopers.domain.common;

import java.util.List;

/**
 * 커서 기반 페이지네이션 결과
 *
 * LIMIT + 1 조회 결과를 기반으로 hasNext를 판단한다.
 * totalElements/totalPages 없이 "다음 페이지 존재 여부"만 제공한다.
 */
public record CursorResult<T>(
        List<T> items,
        boolean hasNext
) {
    /**
     * LIMIT + 1로 조회한 결과에서 CursorResult를 생성한다.
     *
     * @param fetchedItems LIMIT + 1로 조회한 결과
     * @param size 요청한 페이지 크기
     */
    public static <T> CursorResult<T> of(List<T> fetchedItems, int size) {
        boolean hasNext = fetchedItems.size() > size;
        List<T> items = hasNext ? fetchedItems.subList(0, size) : fetchedItems;
        return new CursorResult<>(items, hasNext);
    }
}

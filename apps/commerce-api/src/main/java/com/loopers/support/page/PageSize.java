package com.loopers.support.page;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * 페이지 번호와 크기를 담는 페이징 요청 값 객체.
 *
 * @param page 페이지 번호 (0-based)
 * @param size 페이지 크기
 */
public record PageSize(int page, int size) {

    private static final int MAX_SIZE = 100;

    /**
     * 페이지 크기를 최대값(100)으로 제한하여 생성한다.
     *
     * @param page 페이지 번호 (0-based)
     * @param size 요청 페이지 크기
     * @return 최대값이 적용된 PageSize
     */
    public static PageSize withMaxSize(int page, int size) {
        return new PageSize(page, Math.min(size, MAX_SIZE));
    }

    /**
     * 정렬 조건 없이 {@link Pageable}로 변환한다.
     *
     * @return Pageable
     */
    public Pageable toPageable() {
        return PageRequest.of(page, size);
    }

    /**
     * 정렬 조건을 포함하여 {@link Pageable}로 변환한다.
     *
     * @param sort 정렬 조건
     * @return Pageable
     */
    public Pageable toPageable(Sort sort) {
        return PageRequest.of(page, size, sort);
    }
}

package com.loopers.interfaces.api;

import java.util.List;

/**
 * 페이징 응답 래퍼.
 *
 * <p>페이지 콘텐츠, 현재 페이지 번호, 페이지 크기, 전체 요소 수, 전체 페이지 수를 포함한다.
 * Spring Data 타입에 의존하지 않는다.</p>
 *
 * @param content       현재 페이지의 콘텐츠 목록
 * @param page          현재 페이지 번호 (0부터 시작)
 * @param size          페이지 크기
 * @param totalElements 전체 요소 수
 * @param totalPages    전체 페이지 수
 * @param <T>           콘텐츠 요소 타입
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}

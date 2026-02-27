package com.loopers.interfaces.api;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 페이징 응답 래퍼.
 *
 * <p>Spring Data의 {@link Page} 객체를 API 응답에 적합한 형태로 변환하는 제네릭 레코드이다.
 * 페이지 콘텐츠, 현재 페이지 번호, 페이지 크기, 전체 요소 수, 전체 페이지 수를 포함한다.</p>
 *
 * @param content 현재 페이지의 콘텐츠 목록
 * @param page 현재 페이지 번호 (0부터 시작)
 * @param size 페이지 크기
 * @param totalElements 전체 요소 수
 * @param totalPages 전체 페이지 수
 * @param <T> 콘텐츠 요소 타입
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    /**
     * Spring Data {@link Page} 객체를 {@link PageResponse}로 변환하는 팩토리 메서드.
     *
     * @param page 변환할 Spring Data Page 객체
     * @param <T> 콘텐츠 요소 타입
     * @return 변환된 PageResponse
     */
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}

package com.loopers.support.page;

import java.util.List;

/**
 * 도메인 레이어에서 사용하는 페이징 결과 객체.
 *
 * <p>Spring Data의 {@code Page<T>}에 의존하지 않으며,
 * infrastructure 계층에서 {@code Page<T>}를 변환하여 반환한다.</p>
 *
 * @param content       현재 페이지의 콘텐츠 목록
 * @param page          현재 페이지 번호 (0부터 시작)
 * @param size          페이지 크기
 * @param totalElements 전체 요소 수
 * @param totalPages    전체 페이지 수
 * @param <T>           콘텐츠 요소 타입
 */
public record PagedResult<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
}

package com.loopers.support.page;

/**
 * 도메인 레이어에서 사용하는 페이징/정렬 요청 객체.
 *
 * <p>Spring Data의 {@code Pageable}에 의존하지 않으며,
 * infrastructure 계층에서 {@code Pageable}로 변환하여 사용한다.</p>
 *
 * @param page      페이지 번호 (0부터 시작)
 * @param size      페이지 크기
 * @param sortField 정렬 기준 필드명 (엔티티 필드명)
 * @param ascending true이면 오름차순, false이면 내림차순
 */
public record PageQuery(int page, int size, String sortField, boolean ascending) {
}

package com.loopers.support.page;

import java.util.List;
import java.util.function.Function;

/**
 * 슬라이스 기반 페이징 응답을 담는 제네릭 레코드.
 *
 * @param content 현재 페이지의 데이터 목록
 * @param hasNext 다음 페이지 존재 여부
 * @param <T> 데이터 타입
 */
public record Page<T>(
        List<T> content,
        boolean hasNext
) {

    /**
     * 페이지 데이터를 다른 타입으로 변환한다.
     *
     * @param mapper 변환 함수
     * @param <R> 변환 대상 타입
     * @return 변환된 페이지
     */
    public <R> Page<R> map(Function<T, R> mapper) {
        return new Page<>(
                content.stream().map(mapper).toList(),
                hasNext
        );
    }
}

package com.loopers.domain.ranking;

import java.util.List;

import com.loopers.domain.shared.annotation.DomainService;

import lombok.RequiredArgsConstructor;

/**
 * 랭킹 조회 도메인 서비스.
 *
 * <p>일간·시간 단위 Redis Sorted Set에서 랭킹 데이터를 읽기 전용으로 제공한다.</p>
 */
@DomainService
@RequiredArgsConstructor
public class RankingService {

    private final RankingRepository rankingRepository;

    /**
     * 일간 상위 랭킹을 조회한다.
     *
     * @param date   yyyyMMdd 형식 (nullable, null이면 오늘)
     * @param offset 시작 오프셋 (0-based)
     * @param count  조회할 항목 수
     * @return 순위가 포함된 랭킹 항목 목록
     */
    public List<RankingItem> readDailyTopRanked(String date, int offset, int count) {
        String key = RankingKeyResolver.resolveDaily(date);
        return rankingRepository.readTopRanked(key, offset, count);
    }

    /**
     * 시간 단위 상위 랭킹을 조회한다.
     *
     * @param datetime yyyyMMddHH 형식 (nullable, null이면 현재 시간)
     * @param offset   시작 오프셋 (0-based)
     * @param count    조회할 항목 수
     * @return 순위가 포함된 랭킹 항목 목록
     */
    public List<RankingItem> readHourlyTopRanked(String datetime, int offset, int count) {
        String key = RankingKeyResolver.resolveHourly(datetime);
        return rankingRepository.readTopRanked(key, offset, count);
    }
}

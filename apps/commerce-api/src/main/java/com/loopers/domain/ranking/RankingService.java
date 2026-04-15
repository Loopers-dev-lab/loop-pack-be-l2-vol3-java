package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.util.List;

import com.loopers.domain.shared.annotation.DomainService;

import lombok.RequiredArgsConstructor;

/**
 * 랭킹 조회 도메인 서비스.
 *
 * <p>일간·시간 단위는 Redis Sorted Set, 주간·월간은 배치 집계 DB에서 랭킹 데이터를 읽기 전용으로 제공한다.</p>
 */
@DomainService
@RequiredArgsConstructor
public class RankingService {

    private final RankingRepository rankingRepository;
    private final WeeklyRankingRepository weeklyRankingRepository;
    private final MonthlyRankingRepository monthlyRankingRepository;

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

    /**
     * 주간 상위 랭킹을 조회한다.
     *
     * @param scoreDate 조회 기준일
     * @param page      페이지 번호 (0-based)
     * @param size      페이지 크기
     * @return 주간 랭킹 엔티티 목록 (점수 내림차순)
     */
    public List<ProductRankingWeekly> readWeeklyTopRanked(LocalDate scoreDate, int page, int size) {
        return weeklyRankingRepository.readTopRanked(scoreDate, page, size);
    }

    /**
     * 월간 상위 랭킹을 조회한다.
     *
     * @param scoreDate 조회 기준일
     * @param page      페이지 번호 (0-based)
     * @param size      페이지 크기
     * @return 월간 랭킹 엔티티 목록 (점수 내림차순)
     */
    public List<ProductRankingMonthly> readMonthlyTopRanked(LocalDate scoreDate, int page, int size) {
        return monthlyRankingRepository.readTopRanked(scoreDate, page, size);
    }
}

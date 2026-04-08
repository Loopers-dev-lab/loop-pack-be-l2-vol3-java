package com.loopers.domain.ranking;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RankingService {

    private final RankingRepository rankingRepository;

    /**
     * 지정 날짜의 Top-N 랭킹을 페이지 단위로 조회한다.
     *
     * @param date 조회 날짜
     * @param page 페이지 번호 (0-based)
     * @param size 페이지 크기
     */
    public List<RankingRepository.RankingEntry> getTopRankings(LocalDate date, int page, int size) {
        long start = (long) page * size;
        long end = start + size - 1;
        return rankingRepository.getTopRankings(date, start, end);
    }

    /**
     * 특정 상품의 순위를 조회한다. (1-based 반환, 없으면 null)
     */
    public Long getRank(LocalDate date, Long productId) {
        Long zeroBasedRank = rankingRepository.getRank(date, productId);
        return zeroBasedRank != null ? zeroBasedRank + 1 : null;
    }

    /**
     * 전체 랭킹 멤버 수를 반환한다.
     */
    public long getTotalCount(LocalDate date) {
        return rankingRepository.getTotalCount(date);
    }

    /**
     * 시간 단위 Top-N 랭킹을 조회한다.
     *
     * @param hourKey 시간 키 (yyyyMMddHH)
     * @param size    조회 개수
     */
    public List<RankingRepository.RankingEntry> getHourlyTopRankings(String hourKey, int size) {
        return rankingRepository.getHourlyTopRankings(hourKey, 0, size - 1);
    }
}

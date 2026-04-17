package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingWeight;
import com.loopers.domain.ranking.RankingWeightRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 랭킹 가중치 초기값 적재.
 *
 * 앱 시작 시 ranking_weight 테이블에 데이터가 없으면 기본값을 삽입한다.
 * 이미 존재하는 event_type은 건너뛰므로 재배포 시 멱등성이 보장된다.
 * 이후 가중치 변경은 관리자 API를 통해 DB 원장을 직접 수정한다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class RankingWeightInitializer implements ApplicationRunner {

    // 기본 가중치: 주문(구매 의도 최고) > 좋아요 > 조회(가장 빈번, 의도 가장 낮음)
    private static final Map<String, BigDecimal> DEFAULT_WEIGHTS = Map.of(
            "VIEW", new BigDecimal("0.1"),
            "LIKE", new BigDecimal("0.2"),
            "ORDER", new BigDecimal("0.7")
    );

    private final RankingWeightRepository rankingWeightRepository;

    @Transactional
    @Override
    public void run(ApplicationArguments args) {
        DEFAULT_WEIGHTS.forEach((eventType, weight) -> {
            if (rankingWeightRepository.findByEventType(eventType).isEmpty()) {
                rankingWeightRepository.save(new RankingWeight(eventType, weight));
                log.info("[RankingWeightInitializer] 가중치 초기화: eventType={}, weight={}", eventType, weight);
            }
        });
    }
}

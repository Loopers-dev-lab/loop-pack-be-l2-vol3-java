package com.loopers.infrastructure.ranking;

import com.loopers.application.ranking.RankingAppService;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("RankingScoreRecalculationScheduler 통합 테스트")
class RankingScoreRecalculationSchedulerTest {

    @Autowired
    private RankingScoreRecalculationScheduler scheduler;

    @Autowired
    private RankingAppService rankingAppService;

    @Autowired
    @Qualifier("redisTemplateMaster")
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    private final String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
    private final String allKey = "ranking:all:" + date;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @Test
    @DisplayName("재계산 후 ranking:all 키에 타이브레이커가 포함된 점수가 기록된다")
    void recalculate_writesToAllKey() {
        // given: 상품 2개, 같은 주문 1건씩
        rankingAppService.updateOrderRanking(List.of(101L), 10000);
        rankingAppService.updateOrderRanking(List.of(202L), 10000);

        // 상품101: 조회 3번 추가 → 조회수 높음
        rankingAppService.updateViewRanking(101L);
        rankingAppService.updateViewRanking(101L);
        rankingAppService.updateViewRanking(101L);

        // when
        scheduler.recalculate();

        // then: ranking:all 키에 데이터가 생성됨
        Double score101 = redisTemplate.opsForZSet().score(allKey, "101");
        Double score202 = redisTemplate.opsForZSet().score(allKey, "202");

        assertThat(score101).isNotNull();
        assertThat(score202).isNotNull();

        // 상품101은 조회수가 더 높으므로 소수부가 더 큼
        assertThat(score101).isGreaterThan(score202);
    }

    @Test
    @DisplayName("메인 점수가 다르면 정수부 차이로 순위가 결정된다")
    void recalculate_mainScoreDominates() {
        // given: 상품101=주문3건, 상품202=조회1건
        rankingAppService.updateOrderRanking(List.of(101L), 10000);
        rankingAppService.updateOrderRanking(List.of(101L), 10000);
        rankingAppService.updateOrderRanking(List.of(101L), 10000);
        rankingAppService.updateViewRanking(202L);

        // when
        scheduler.recalculate();

        // then
        Double score101 = redisTemplate.opsForZSet().score(allKey, "101");
        Double score202 = redisTemplate.opsForZSet().score(allKey, "202");

        assertThat(score101).isNotNull();
        assertThat(score202).isNotNull();
        // 메인 점수: 101=floor(3*0.6)=1, 202=floor(1*0.1)=0
        assertThat(Math.floor(score101)).isGreaterThan(Math.floor(score202));
    }

    @Test
    @DisplayName("raw 데이터가 없으면 재계산을 건너뛴다")
    void recalculate_emptyData() {
        scheduler.recalculate();

        Boolean exists = redisTemplate.hasKey(allKey);
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("재계산 후 ranking:all 키에 TTL이 설정된다")
    void recalculate_ttlSet() {
        rankingAppService.updateViewRanking(101L);

        scheduler.recalculate();

        Long ttl = redisTemplate.getExpire(allKey);
        assertThat(ttl).isGreaterThan(0);
        assertThat(ttl).isLessThanOrEqualTo(172800L);
    }

    @Test
    @DisplayName("주문 1건(10000원)이 좋아요 3건보다 높은 순위를 가진다")
    void recalculate_orderBeatsLikes() {
        // given: 상품101=주문 1건(10000원), 상품202=좋아요 3건
        rankingAppService.updateOrderRanking(List.of(101L), 10000);
        rankingAppService.updateLikeRanking(202L);
        rankingAppService.updateLikeRanking(202L);
        rankingAppService.updateLikeRanking(202L);

        // when
        scheduler.recalculate();

        // then: 주문 점수 = log1p(10000)*0.6 ≈ 5.53, 좋아요 점수 = 3*0.2 = 0.6
        Double score101 = redisTemplate.opsForZSet().score(allKey, "101");
        Double score202 = redisTemplate.opsForZSet().score(allKey, "202");

        assertThat(score101).isNotNull();
        assertThat(score202).isNotNull();
        assertThat(score101).isGreaterThan(score202);
    }

    @Test
    @DisplayName("전일 ranking:all 점수가 carry-over로 반영된다")
    void recalculate_withCarryOver() {
        // given: 전일 점수 세팅
        String yesterdayAllKey = "ranking:all:" + LocalDate.now().minusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE);
        redisTemplate.opsForZSet().add(yesterdayAllKey, "101", 10.0);

        // 오늘 조회 1건
        rankingAppService.updateViewRanking(101L);

        // when
        scheduler.recalculate();

        // then: 메인점수 = views(1)*0.1 + carry-over(floor(10)*0.1) = 0.1 + 1.0 = 1.1
        // 정수부 = floor(1.1) = 1 + 타이브레이커
        Double score = redisTemplate.opsForZSet().score(allKey, "101");
        assertThat(score).isNotNull();
        assertThat(Math.floor(score)).isEqualTo(1.0);
    }
}

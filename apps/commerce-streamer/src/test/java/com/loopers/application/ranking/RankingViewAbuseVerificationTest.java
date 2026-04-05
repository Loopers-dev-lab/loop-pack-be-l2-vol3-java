package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingKeyGenerator;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(RedisTestContainersConfig.class)
@DisplayName("View 어뷰징 영향 실측 (선택 5-2: A → D-3 전환 근거)")
class RankingViewAbuseVerificationTest {

    @Autowired
    private RankingApp rankingApp;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @Test
    @DisplayName("단일 봇 3000회 조회 → score=300 (좋아요 1500건에 상당)")
    void singleBotCanInflateScoreTo300() {
        LocalDate date = LocalDate.of(2026, 4, 5);
        String key = RankingKeyGenerator.dailyKey(date);

        // 단일 봇이 동일 상품을 3000회 조회
        for (int i = 0; i < 3000; i++) {
            rankingApp.applyViewScore(1L, date);
        }

        Double abusedScore = redisTemplate.opsForZSet().score(key, "1");

        // 실제 정상 상품: 좋아요 50건 = 0.2 * 50 = 10점
        for (int i = 0; i < 50; i++) {
            rankingApp.applyLikeDelta(2L, 1, date);
        }
        Double normalScore = redisTemplate.opsForZSet().score(key, "2");

        System.out.println("[VIEW-ABUSE] 단일 봇 3000회 조회 score=" + abusedScore);
        System.out.println("[VIEW-ABUSE] 정상 상품(좋아요 50건) score=" + normalScore);
        System.out.println("[VIEW-ABUSE] 봇이 정상 대비 " + (abusedScore / normalScore) + "배 점수 확보");

        assertThat(abusedScore).isCloseTo(300.0, org.assertj.core.data.Offset.offset(0.01));
        assertThat(abusedScore).isGreaterThan(normalScore); // 봇이 정상 상품을 능가
    }

    @Test
    @DisplayName("10개 봇이 각 300회씩 조회 → 총 3000회, score=300 (동일 효과)")
    void botFarmInflation() {
        LocalDate date = LocalDate.of(2026, 4, 5);
        String key = RankingKeyGenerator.dailyKey(date);

        // 10봇 × 각 300회 조회 (사용자 추적이 없으니 모두 카운트됨)
        int botCount = 10;
        int viewsPerBot = 300;
        for (int bot = 0; bot < botCount; bot++) {
            for (int view = 0; view < viewsPerBot; view++) {
                rankingApp.applyViewScore(1L, date);
            }
        }

        Double score = redisTemplate.opsForZSet().score(key, "1");

        System.out.println("[BOT-FARM] 10봇 × 300회 = 3000조회 score=" + score);
        // 예상: 0.1 * 3000 = 300
        assertThat(score).isCloseTo(300.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    @DisplayName("Bitmap(D-3) 도입 시뮬레이션 — 유저당 1회만 카운트")
    void bitmapSimulation() {
        LocalDate date = LocalDate.of(2026, 4, 5);
        String key = RankingKeyGenerator.dailyKey(date);

        // Bitmap 시뮬레이션: memberId별로 최초 1회만 카운트
        // 봇 10개가 각 300회 조회해도 유효 카운트는 10회뿐
        int botCount = 10;
        int viewsPerBot = 300;
        java.util.Set<Integer> seenMembers = new java.util.HashSet<>();

        for (int bot = 0; bot < botCount; bot++) {
            for (int view = 0; view < viewsPerBot; view++) {
                // Bitmap SETBIT 시뮬레이션: 이미 카운트된 봇이면 스킵
                if (seenMembers.add(bot)) {
                    rankingApp.applyViewScore(1L, date);
                }
            }
        }

        Double score = redisTemplate.opsForZSet().score(key, "1");

        System.out.println("[BITMAP-SIM] 10봇 × 300회 but Bitmap 적용 score=" + score);
        System.out.println("[BITMAP-SIM] 봇 증가에도 선형(유저 수만큼)만 증가 → 어뷰징 효과 극히 제한");
        // 예상: 0.1 * 10 = 1.0 (봇 10마리 → 10점)
        assertThat(score).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    @DisplayName("A vs Bitmap 비교 — 300배 차이")
    void compareAvsBitmap() {
        LocalDate dateA = LocalDate.of(2026, 4, 5);
        LocalDate dateB = LocalDate.of(2026, 4, 6);

        // 시나리오: 10봇 × 300회 조회 (같은 어뷰징)

        // A: 중복 방지 없음 (현재)
        for (int bot = 0; bot < 10; bot++) {
            for (int view = 0; view < 300; view++) {
                rankingApp.applyViewScore(1L, dateA);
            }
        }

        // D-3: Bitmap (유저당 1회)
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (int bot = 0; bot < 10; bot++) {
            for (int view = 0; view < 300; view++) {
                if (seen.add(bot)) {
                    rankingApp.applyViewScore(1L, dateB);
                }
            }
        }

        Double scoreA = redisTemplate.opsForZSet().score(RankingKeyGenerator.dailyKey(dateA), "1");
        Double scoreB = redisTemplate.opsForZSet().score(RankingKeyGenerator.dailyKey(dateB), "1");

        System.out.println("[A-vs-BITMAP] A(no-dedup) score=" + scoreA);
        System.out.println("[A-vs-BITMAP] D-3(Bitmap) score=" + scoreB);
        System.out.println("[A-vs-BITMAP] ratio A/Bitmap=" + (scoreA / scoreB) + "배");

        assertThat(scoreA / scoreB).isCloseTo(300.0, org.assertj.core.data.Offset.offset(0.1));
    }
}

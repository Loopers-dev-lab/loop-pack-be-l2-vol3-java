package com.loopers.domain.ranking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class RankingServiceIntegrationTest {

	private static final String KEY_PREFIX = "ranking:all:";
	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

	@Autowired
	private RankingService rankingService;

	@Autowired
	@Qualifier("redisTemplateMaster")
	private RedisTemplate<String, String> masterRedisTemplate;

	@BeforeEach
	void setUp() {
		Set<String> keys = masterRedisTemplate.keys(KEY_PREFIX + "*");
		if (keys != null && !keys.isEmpty()) {
			masterRedisTemplate.delete(keys);
		}
	}

	private String todayKey() {
		return KEY_PREFIX + LocalDate.now().format(DATE_FORMAT);
	}

	@DisplayName("이벤트 → ZSET 점수 반영")
	@Nested
	class EventToZset {

		@DisplayName("조회 이벤트를 처리하면 ZSET에 0.1 점수가 누적된다")
		@Test
		void viewEventAddsScore() {
			// when
			rankingService.addViewScore(1L);

			// then
			Double score = masterRedisTemplate.opsForZSet().score(todayKey(), "1");
			assertThat(score).isEqualTo(0.1);
		}

		@DisplayName("좋아요 이벤트를 처리하면 ZSET에 0.2 점수가 누적된다")
		@Test
		void likeEventAddsScore() {
			// when
			rankingService.addLikeScore(1L);

			// then
			Double score = masterRedisTemplate.opsForZSet().score(todayKey(), "1");
			assertThat(score).isEqualTo(0.2);
		}

		@DisplayName("주문 이벤트를 처리하면 ZSET에 0.7 × 수량 점수가 누적된다")
		@Test
		void orderEventAddsWeightedScore() {
			// when
			rankingService.addOrderScore(1L, 3);

			// then
			Double score = masterRedisTemplate.opsForZSet().score(todayKey(), "1");
			assertThat(score).isCloseTo(2.1, within(0.001));
		}

		@DisplayName("여러 이벤트를 순차적으로 처리하면 가중치 합산 점수가 누적된다")
		@Test
		void multipleEventsAccumulateScore() {
			// when: 조회 2번(+0.2) + 좋아요 1번(+0.2) + 주문 1건 수량 2(+1.4)
			rankingService.addViewScore(1L);
			rankingService.addViewScore(1L);
			rankingService.addLikeScore(1L);
			rankingService.addOrderScore(1L, 2);

			// then: 0.1 + 0.1 + 0.2 + 1.4 = 1.8
			Double score = masterRedisTemplate.opsForZSet().score(todayKey(), "1");
			assertThat(score).isCloseTo(1.8, within(0.001));
		}

		@DisplayName("서로 다른 상품의 점수는 독립적으로 누적된다")
		@Test
		void differentProductsHaveIndependentScores() {
			// when
			rankingService.addOrderScore(1L, 1);  // 상품1: +0.7
			rankingService.addLikeScore(2L);       // 상품2: +0.2
			rankingService.addViewScore(3L);       // 상품3: +0.1

			// then
			assertAll(
					() -> assertThat(masterRedisTemplate.opsForZSet().score(todayKey(), "1"))
							.isEqualTo(0.7),
					() -> assertThat(masterRedisTemplate.opsForZSet().score(todayKey(), "2"))
							.isEqualTo(0.2),
					() -> assertThat(masterRedisTemplate.opsForZSet().score(todayKey(), "3"))
							.isEqualTo(0.1)
			);
		}

		@DisplayName("주문 1건(0.7)이 좋아요 3건(0.6)보다 높은 점수를 받아 순위가 높다")
		@Test
		void orderOutranksMultipleLikes() {
			// given: 상품A에 주문 1건, 상품B에 좋아요 3건
			rankingService.addOrderScore(1L, 1);
			rankingService.addLikeScore(2L);
			rankingService.addLikeScore(2L);
			rankingService.addLikeScore(2L);

			// then: ZREVRANGE에서 상품A(0.7)가 상품B(0.6)보다 앞에 위치
			Set<String> topRanking = masterRedisTemplate.opsForZSet()
					.reverseRange(todayKey(), 0, -1);
			assertThat(topRanking).containsExactly("1", "2");
		}
	}

	@DisplayName("키 전략")
	@Nested
	class KeyStrategy {

		@DisplayName("점수가 반영되면 오늘 날짜 키에 저장된다")
		@Test
		void scoreStoredInTodayKey() {
			// when
			rankingService.addViewScore(1L);

			// then
			Boolean exists = masterRedisTemplate.hasKey(todayKey());
			assertThat(exists).isTrue();
		}

		@DisplayName("TTL이 2일(172800초)로 설정된다")
		@Test
		void ttlSetToTwoDays() {
			// when
			rankingService.addViewScore(1L);

			// then
			Long ttl = masterRedisTemplate.getExpire(todayKey());
			assertThat(ttl).isGreaterThan(172800 - 10);
			assertThat(ttl).isLessThanOrEqualTo(172800);
		}
	}
}
